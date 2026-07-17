package io.wax100.arenaCore.manager;

import io.wax100.arenaCore.ArenaCore;
import io.wax100.arenaCore.model.ArenaFieldConfig;
import io.wax100.arenaCore.model.ArenaSession;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;
import org.bukkit.scoreboard.Team;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 観客向け競技者HP表示マネージャ。
 *
 * <p>試合中、フィールド近傍（{@code spectator-health.radius} ブロック以内）にいる
 * 非競技者を観客とみなし、競技者のHPを表示する。表示方式は観客ごとに
 * {@code /arena hp} で切り替えられる:
 * <ul>
 *   <li>{@link DisplayMode#BOSSBAR} — 競技者ごとのボスバー（チーム色・HP割合）</li>
 *   <li>{@link DisplayMode#SIDEBAR} — サイドバーに競技者名とHP値の一覧</li>
 *   <li>{@link DisplayMode#OFF} — 非表示</li>
 * </ul>
 * デフォルトは {@code spectator-health.default-mode}（初期値: bossbar）。
 * 観客の選択はプレイヤーの PersistentDataContainer に保存され、再ログイン後も保持される。
 *
 * <p>表示は観客にのみ適用されるため、競技者本人には相手のHPは見えない。
 * サイドバー方式では専用スコアボードにメインスコアボードのアリーナチーム
 * （カラー・メンバー）をミラーリングし、観客から見たネームタグ色を維持する。
 *
 * <p>ライフサイクル:
 * <ul>
 *   <li>{@link #start(ArenaSession)} — 試合開始時（{@code beginActiveMatch}）に呼ばれる</li>
 *   <li>{@link #stop()} — セッション終了時（{@code cleanupSession}）に呼ばれる。冪等。</li>
 * </ul>
 */
public class SpectatorHealthManager {

    /** 観客ごとのHP表示方式。 */
    public enum DisplayMode {
        /** 競技者ごとのボスバー表示 */
        BOSSBAR,
        /** サイドバーの一覧表示 */
        SIDEBAR,
        /** 非表示 */
        OFF;

        /**
         * 文字列から表示方式を解決する（大文字小文字を無視）。
         *
         * @param value 入力文字列
         * @return 対応する表示方式。不正な場合は {@code null}
         */
        public static DisplayMode fromString(String value) {
            if (value == null) return null;
            try {
                return valueOf(value.toUpperCase());
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
    }

    /** HP最大値のフォールバック（GENERIC_MAX_HEALTH 属性が取得できない場合） */
    private static final double DEFAULT_MAX_HEALTH = 20.0;

    private static final String OBJECTIVE_NAME = "arena_hp";

    private final ArenaCore plugin;
    /** 表示方式の選択を保存する PDC キー */
    private final NamespacedKey modeKey;

    // ── config値 ──
    private boolean enabled;
    private double radius;
    private int updateInterval;
    private DisplayMode defaultMode;

    // ── 状態 ──
    private ArenaSession session;
    private BukkitTask task;
    /** 競技者UUID → ボスバー */
    private final Map<UUID, BossBar> bars = new HashMap<>();
    /** サイドバー用の専用スコアボード（ScoreboardManager 不可時は null） */
    private Scoreboard board;
    private Objective objective;
    /** サイドバーに表示中の競技者名 */
    private final Set<String> displayedNames = new HashSet<>();
    /** 現在表示を適用中の観客 → 適用した表示方式 */
    private final Map<UUID, DisplayMode> applied = new HashMap<>();

    /**
     * SpectatorHealthManager を初期化する。
     *
     * @param plugin ArenaCore プラグインインスタンス
     */
    public SpectatorHealthManager(ArenaCore plugin) {
        this.plugin = plugin;
        this.modeKey = new NamespacedKey(plugin, "spectator_hp_mode");
        loadConfig();
    }

    /** config.yml から観客HP表示設定を読み込む。 */
    private void loadConfig() {
        var config = plugin.getConfig();
        enabled = config.getBoolean("spectator-health.enabled", true);
        radius = Math.max(0, config.getDouble("spectator-health.radius", 100));
        updateInterval = Math.max(1,
                config.getInt("spectator-health.update-interval", 10));

        String modeName = config.getString("spectator-health.default-mode", "bossbar");
        defaultMode = DisplayMode.fromString(modeName);
        if (defaultMode == null) {
            plugin.getLogger().warning("spectator-health.default-mode が不正です ("
                    + modeName + ")。bossbar を使用します。");
            defaultMode = DisplayMode.BOSSBAR;
        }
    }

    // ══════════════════════════════════════
    //  表示方式の選択
    // ══════════════════════════════════════

    /**
     * プレイヤーの表示方式を返す。
     *
     * <p>本人が {@code /arena hp} で選択済みならその値、未選択なら
     * config のデフォルト方式を返す。
     *
     * @param player 対象プレイヤー
     * @return 表示方式
     */
    public DisplayMode getMode(Player player) {
        String saved = player.getPersistentDataContainer()
                .get(modeKey, PersistentDataType.STRING);
        DisplayMode mode = DisplayMode.fromString(saved);
        return mode != null ? mode : defaultMode;
    }

    /**
     * プレイヤーの表示方式を設定して保存する。
     *
     * <p>観客として表示中の場合は即座に切り替える。
     *
     * @param player 対象プレイヤー
     * @param mode   表示方式
     */
    public void setMode(Player player, DisplayMode mode) {
        player.getPersistentDataContainer()
                .set(modeKey, PersistentDataType.STRING, mode.name());

        DisplayMode prev = applied.get(player.getUniqueId());
        if (prev != null && prev != mode) {
            removeDisplay(player, prev);
            applyDisplay(player, mode);
            applied.put(player.getUniqueId(), mode);
        }
    }

    /** @return config のデフォルト表示方式 */
    public DisplayMode getDefaultMode() {
        return defaultMode;
    }

    // ══════════════════════════════════════
    //  ライフサイクル
    // ══════════════════════════════════════

    /**
     * 観客HP表示を開始する。
     *
     * <p>試合開始時に呼ばれる。フィールド未設定の場合は観客判定ができないため
     * 何もしない。
     *
     * @param session 開始するアリーナセッション
     */
    public void start(ArenaSession session) {
        if (!enabled) return;
        stop();

        if (session.getFieldConfig() == null) {
            plugin.getLogger().info(
                    "フィールド未設定のため観客HP表示をスキップします。");
            return;
        }

        this.session = session;

        // サイドバー用スコアボードを準備（不可でもボスバー方式は動作させる）
        ScoreboardManager manager = Bukkit.getScoreboardManager();
        if (manager != null) {
            board = manager.getNewScoreboard();
            objective = board.registerNewObjective(OBJECTIVE_NAME, Criteria.DUMMY,
                    ChatColor.RED + "❤ " + ChatColor.GOLD + ChatColor.BOLD + "競技者HP");
            objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        } else {
            plugin.getLogger().warning(
                    "ScoreboardManager が利用できません。サイドバー方式は無効です。");
        }

        task = new BukkitRunnable() {
            @Override
            public void run() {
                update();
            }
        }.runTaskTimer(plugin, 1L, updateInterval);
    }

    /**
     * 観客HP表示を停止し、全表示を除去する。
     *
     * <p>セッション終了・プラグイン停止時に呼ばれる。冪等。
     */
    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        for (BossBar bar : bars.values()) {
            bar.removeAll();
        }
        bars.clear();
        for (Map.Entry<UUID, DisplayMode> entry : applied.entrySet()) {
            if (entry.getValue() == DisplayMode.SIDEBAR) {
                Player p = Bukkit.getPlayer(entry.getKey());
                if (p != null && p.isOnline()) {
                    restoreMainScoreboard(p);
                }
            }
        }
        applied.clear();
        displayedNames.clear();
        board = null;
        objective = null;
        session = null;
    }

    // ══════════════════════════════════════
    //  更新処理
    // ══════════════════════════════════════

    /** 毎更新tickの処理: ボスバー・サイドバーの更新 → 観客の付け外し。 */
    private void update() {
        if (session == null) return;

        Set<UUID> fighterIds = new HashSet<>();
        Map<String, Player> aliveFighters = new LinkedHashMap<>();
        Set<UUID> eliminated = plugin.getArenaManager().getEliminatedPlayers();
        for (String team : session.getTeamNames()) {
            if (session.isMobTeam(team)) continue;
            ChatColor teamColor = session.getTeamColor(team);
            for (UUID memberId : session.getTeamMembers(team)) {
                fighterIds.add(memberId);
                Player p = updateFighterBar(memberId, teamColor, eliminated);
                if (p != null) {
                    aliveFighters.put(p.getName(), p);
                }
            }
        }

        removeStaleBars(fighterIds);
        updateSidebar(aliveFighters);
        updateSpectators(fighterIds);
    }

    /**
     * 1競技者分のボスバーを生成・更新する。
     *
     * <p>脱落者・オフラインの競技者はバーを除去する。
     *
     * @param memberId   競技者UUID
     * @param teamColor  チームカラー
     * @param eliminated 脱落者のUUID集合
     * @return 表示対象（生存・オンライン）の場合そのプレイヤー、対象外なら {@code null}
     */
    private Player updateFighterBar(UUID memberId, ChatColor teamColor,
                                    Set<UUID> eliminated) {
        Player p = Bukkit.getPlayer(memberId);
        if (eliminated.contains(memberId) || p == null || !p.isOnline()) {
            BossBar bar = bars.remove(memberId);
            if (bar != null) bar.removeAll();
            return null;
        }

        BossBar bar = bars.get(memberId);
        if (bar == null) {
            bar = Bukkit.createBossBar("", toBarColor(teamColor), BarStyle.SOLID);
            bars.put(memberId, bar);
            // 途中生成のバーにも既存のボスバー方式の観客を追加
            for (Map.Entry<UUID, DisplayMode> entry : applied.entrySet()) {
                if (entry.getValue() != DisplayMode.BOSSBAR) continue;
                Player spectator = Bukkit.getPlayer(entry.getKey());
                if (spectator != null && spectator.isOnline()) {
                    bar.addPlayer(spectator);
                }
            }
        }

        double maxHealth = getMaxHealth(p);
        double health = Math.max(0, Math.min(p.getHealth(), maxHealth));
        bar.setColor(toBarColor(teamColor));
        bar.setProgress(maxHealth > 0 ? health / maxHealth : 0);
        bar.setTitle(teamColor.toString() + ChatColor.BOLD + p.getName()
                + ChatColor.RESET + ChatColor.WHITE + "  ❤ "
                + (int) Math.ceil(health) + " / " + (int) Math.ceil(maxHealth));
        return p;
    }

    /** 競技者でなくなったプレイヤーのバーを除去する。 */
    private void removeStaleBars(Set<UUID> fighterIds) {
        Iterator<Map.Entry<UUID, BossBar>> it = bars.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, BossBar> entry = it.next();
            if (!fighterIds.contains(entry.getKey())) {
                entry.getValue().removeAll();
                it.remove();
            }
        }
    }

    /** サイドバーのHPスコアとチームミラーを更新する。 */
    private void updateSidebar(Map<String, Player> aliveFighters) {
        if (board == null) return;

        for (Map.Entry<String, Player> entry : aliveFighters.entrySet()) {
            int hp = (int) Math.ceil(entry.getValue().getHealth());
            objective.getScore(entry.getKey()).setScore(hp);
        }
        displayedNames.removeIf(name -> {
            if (!aliveFighters.containsKey(name)) {
                board.resetScores(name);
                return true;
            }
            return false;
        });
        displayedNames.addAll(aliveFighters.keySet());

        mirrorTeams();
    }

    /**
     * メインスコアボードのアリーナチームを専用スコアボードへミラーリングする。
     *
     * <p>チームカラーとエントリを同期し、脱落等でメイン側から外れたエントリは
     * ミラー側からも除去する。
     */
    private void mirrorTeams() {
        ScoreboardManager manager = Bukkit.getScoreboardManager();
        if (manager == null) return;
        Scoreboard main = manager.getMainScoreboard();

        for (String teamName : session.getTeamNames()) {
            if (session.isMobTeam(teamName)) continue;
            Team src = main.getTeam(teamName);
            Team dst = board.getTeam(teamName);
            if (src == null) {
                if (dst != null) dst.unregister();
                continue;
            }
            if (dst == null) {
                dst = board.registerNewTeam(teamName);
            }
            dst.setColor(src.getColor());
            dst.setPrefix(src.getPrefix());
            dst.setSuffix(src.getSuffix());
            for (String entry : src.getEntries()) {
                if (!dst.hasEntry(entry)) dst.addEntry(entry);
            }
            for (String entry : new ArrayList<>(dst.getEntries())) {
                if (!src.hasEntry(entry)) dst.removeEntry(entry);
            }
        }
    }

    /**
     * 観客判定を行い、各観客の選択方式で表示の付け外しを行う。
     *
     * <p>競技者以外でフィールドから半径 {@code radius} ブロック以内にいる
     * プレイヤーを観客とする。競技者は脱落者含め常に表示対象外。
     *
     * @param fighterIds 競技者のUUID集合
     */
    private void updateSpectators(Set<UUID> fighterIds) {
        ArenaFieldConfig field = session.getFieldConfig();
        Set<UUID> seen = new HashSet<>();

        for (Player p : Bukkit.getOnlinePlayers()) {
            if (fighterIds.contains(p.getUniqueId())) continue;
            if (!isWithinRadius(p.getLocation(), field)) continue;

            seen.add(p.getUniqueId());
            DisplayMode desired = getMode(p);
            DisplayMode prev = applied.get(p.getUniqueId());
            if (prev == null) {
                applyDisplay(p, desired);
                applied.put(p.getUniqueId(), desired);
            } else if (prev != desired) {
                removeDisplay(p, prev);
                applyDisplay(p, desired);
                applied.put(p.getUniqueId(), desired);
            }
        }

        // 範囲外に出た・退出した観客から表示を外す
        applied.entrySet().removeIf(entry -> {
            if (seen.contains(entry.getKey())) return false;
            Player p = Bukkit.getPlayer(entry.getKey());
            if (p != null && p.isOnline()) {
                removeDisplay(p, entry.getValue());
            }
            return true;
        });
    }

    /** 指定方式の表示をプレイヤーに適用する。 */
    private void applyDisplay(Player p, DisplayMode mode) {
        switch (mode) {
            case BOSSBAR -> {
                for (BossBar bar : bars.values()) {
                    bar.addPlayer(p);
                }
            }
            case SIDEBAR -> {
                if (board != null) p.setScoreboard(board);
            }
            case OFF -> { /* 何も表示しない */ }
        }
    }

    /** 指定方式の表示をプレイヤーから除去する。 */
    private void removeDisplay(Player p, DisplayMode mode) {
        switch (mode) {
            case BOSSBAR -> {
                for (BossBar bar : bars.values()) {
                    bar.removePlayer(p);
                }
            }
            case SIDEBAR -> restoreMainScoreboard(p);
            case OFF -> { /* 何もしていない */ }
        }
    }

    /** プレイヤーのスコアボードをメインに戻す。 */
    private void restoreMainScoreboard(Player p) {
        ScoreboardManager manager = Bukkit.getScoreboardManager();
        if (manager != null) {
            p.setScoreboard(manager.getMainScoreboard());
        }
    }

    // ══════════════════════════════════════
    //  ユーティリティ
    // ══════════════════════════════════════

    /**
     * 座標がフィールド境界（AABB）から半径 {@code radius} ブロック以内かを判定する。
     *
     * @param loc   判定する座標
     * @param field フィールド設定
     * @return 同一ワールドかつ半径内の場合 {@code true}
     */
    private boolean isWithinRadius(Location loc, ArenaFieldConfig field) {
        if (loc.getWorld() == null
                || !loc.getWorld().getName().equals(field.worldName())) {
            return false;
        }
        double dx = axisDistance(loc.getX(), field.minX(), field.maxX());
        double dy = axisDistance(loc.getY(), field.minY(), field.maxY());
        double dz = axisDistance(loc.getZ(), field.minZ(), field.maxZ());
        return dx * dx + dy * dy + dz * dz <= radius * radius;
    }

    /** 1軸上の値と区間 [min, max] の距離を返す（区間内なら 0）。 */
    private static double axisDistance(double value, double min, double max) {
        if (value < min) return min - value;
        if (value > max) return value - max;
        return 0;
    }

    /** プレイヤーの最大HPを返す（属性が取得できない場合はデフォルト値）。 */
    private static double getMaxHealth(Player p) {
        AttributeInstance attr = p.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        return attr != null ? attr.getValue() : DEFAULT_MAX_HEALTH;
    }

    /**
     * チームカラー（ChatColor）を近いボスバー色（BarColor）に変換する。
     *
     * @param color チームカラー
     * @return 対応する {@link BarColor}。対応色がない場合は WHITE
     */
    private static BarColor toBarColor(ChatColor color) {
        return switch (color) {
            case RED, DARK_RED -> BarColor.RED;
            case BLUE, DARK_BLUE, AQUA, DARK_AQUA -> BarColor.BLUE;
            case GREEN, DARK_GREEN -> BarColor.GREEN;
            case YELLOW, GOLD -> BarColor.YELLOW;
            case LIGHT_PURPLE -> BarColor.PINK;
            case DARK_PURPLE -> BarColor.PURPLE;
            default -> BarColor.WHITE;
        };
    }
}
