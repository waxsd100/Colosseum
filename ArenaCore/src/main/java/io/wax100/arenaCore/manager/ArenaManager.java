package io.wax100.arenaCore.manager;

import io.wax100.arenaCore.ArenaCore;
import io.wax100.arenaCore.command.sub.DeathmatchSubCommand;
import io.wax100.arenaCore.event.ArenaBettingCloseEvent;
import io.wax100.arenaCore.event.ArenaBettingOpenEvent;
import io.wax100.arenaCore.event.ArenaWinnerDeclaredEvent;
import io.wax100.arenaCore.model.ArenaSession;
import io.wax100.arenaCore.model.ArenaState;
import io.wax100.arenaCore.model.DeathmatchChallenge;
import io.wax100.arenaCore.model.MatchMode;
import io.wax100.arenaCore.model.TeamAreaConfig;
import io.wax100.arenaCore.model.BettingRegion;
import io.wax100.arenaCore.util.ArenaMessages;
import io.wax100.arenaCore.wincondition.WinCondition;
import io.wax100.casinoCore.CasinoCore;
import io.wax100.chipLib.ChipManager;
import io.wax100.chipLib.ChipPlugin;
import io.wax100.chipLib.ranking.RankingManager;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.*;
import java.util.logging.Level;

/**
 * 闘技場セッションのライフサイクルを管理するクラス。
 *
 * <p>セッションの作成・チーム編成・ベット受付・試合開始・勝者宣言・キャンセルの
 * 一連のフローを制御する。プレイヤーおよびモンスターの待機場管理も担当する。
 */
public class ArenaManager {

    private final ArenaCore plugin;
    private final BettingManager bettingManager;
    private final RegionManager regionManager;
    private final TerrainManager terrainManager;
    private final ArenaGuardManager guardManager;

    private ArenaSession activeSession;
    private BukkitTask oddsBroadcastTask;
    private BukkitTask bettingTimerTask;
    private BukkitTask recruitingTimerTask;
    private BukkitTask regionParticleTask;
    private final Set<UUID> eliminatedPlayers = new HashSet<>();

    // ── デスマッチ投票管理 ──
    private DeathmatchChallenge activeChallenge;
    private int deathmatchProposalCount;
    private BukkitTask voteTimerTask;

    // ── 定数 ──
    private static final int VOTE_TIMEOUT_SECONDS = 20;
    private static final int[] VOTE_PROGRESS_NOTIFY_AT = {15, 10, 5};

    public ArenaManager(ArenaCore plugin, BettingManager bettingManager,
                        RegionManager regionManager, TerrainManager terrainManager) {
        this.plugin = Objects.requireNonNull(plugin, "plugin must not be null");
        this.bettingManager = Objects.requireNonNull(bettingManager, "bettingManager must not be null");
        this.regionManager = Objects.requireNonNull(regionManager, "regionManager must not be null");
        this.terrainManager = Objects.requireNonNull(terrainManager, "terrainManager must not be null");
        this.guardManager = new ArenaGuardManager(plugin);
    }

    public boolean hasActiveSession() { return activeSession != null; }
    public ArenaSession getActiveSession() { return activeSession; }

    /**
     * 闘技場セッションを作成する。
     */
    public ArenaSession createArena(String name, List<String> teamNames) {
        if (activeSession != null) {
            plugin.getLogger().warning("セッション作成失敗: 既にアクティブなセッションが存在します (" + activeSession.getName() + ")");
            return null;
        }
        if (terrainManager.isBlocking()) {
            plugin.getLogger().warning("セッション作成失敗: 地形復元中です");
            return null;
        }
        activeSession = new ArenaSession(name, teamNames);
        eliminatedPlayers.clear();
        regionManager.clearRegions();

        // バニラ Scoreboard Team を先に作成
        for (String team : teamNames) {
            ensureScoreboardTeam(team);
        }

        plugin.getLogger().info("闘技場セッション作成: " + name + " (チーム数: " + teamNames.size() + ")");
        return activeSession;
    }

    /**
     * プリセットデータからセッションを作成する。
     *
     * @param data プリセットデータ
     * @return 作成されたセッション。失敗時 {@code null}
     */
    public ArenaSession createFromPreset(ArenaPresetStore.PresetData data) {
        ArenaSession session = createArena(data.name(), data.teamNames());
        if (session == null) return null;

        session.setFieldConfig(data.fieldConfig());
        for (var entry : data.teamAreaConfigs().entrySet()) {
            session.setTeamAreaConfig(entry.getKey(), entry.getValue());
        }
        // mobTeams はプリセットから復元しない。
        // openBetting() 時に待機場のエンティティを走査して自動検出する。
        for (var entry : data.bettingRegions().entrySet()) {
            regionManager.registerBettingRegion(entry.getKey(), entry.getValue());
        }
        for (var colorEntry : data.teamColors().entrySet()) {
            session.setTeamColor(colorEntry.getKey(), colorEntry.getValue());
            ensureScoreboardTeam(colorEntry.getKey());
        }
        return session;
    }

    /**
     * ベット受付を開始する。
     */
    public boolean openBetting() {
        if (activeSession == null || activeSession.getState() != ArenaState.SETUP) return false;

        // Mobチーム自動検出（待機場にMobがいれば自動マーク）
        for (String team : activeSession.getTeamNames()) {
            if (activeSession.getTeamSize(team) > 0) continue;
            TeamAreaConfig config = activeSession.getTeamAreaConfig(team);
            if (config != null && !config.scanEntities().isEmpty()) {
                activeSession.markAsMobTeam(team);
            }
        }

        // TP先未設定チェック（エラー）
        List<String> missingDest = new ArrayList<>();
        for (String team : activeSession.getTeamNames()) {
            TeamAreaConfig areaConfig = activeSession.getTeamAreaConfig(team);
            if (areaConfig == null || areaConfig.getDestination() == null) {
                missingDest.add(team);
            }
        }
        if (!missingDest.isEmpty()) {
            for (String team : missingDest) {
                ChatColor teamColor = activeSession.getTeamColor(team);
                Bukkit.broadcastMessage(ArenaMessages.PREFIX + ChatColor.RED
                        + "✗ " + teamColor + team + ChatColor.RED + " のTP先が未設定です。"
                        + ChatColor.GRAY + " → /arena team dest " + team);
            }
            return false;
        }

        // 戦闘エリア未設定チェック（エラー）
        if (activeSession.getFieldConfig() == null) {
            Bukkit.broadcastMessage(ArenaMessages.PREFIX + ChatColor.RED
                    + "✗ 戦闘エリアが未設定です。" + ChatColor.GRAY + " → /arena field");
            return false;
        }

        // Open時にSchematicから地形を復元（前回の残骸をクリーンアップ）
        terrainManager.pasteSchematic(
                activeSession.getId().toString(),
                activeSession.getFieldConfig().worldName());

        // カスタムイベント発火（キャンセル可能）
        ArenaBettingOpenEvent openEvent = new ArenaBettingOpenEvent(activeSession);
        Bukkit.getPluginManager().callEvent(openEvent);
        if (openEvent.isCancelled()) {
            plugin.getLogger().info("ベット受付開始がイベントリスナーによりキャンセルされました。");
            return false;
        }

        activeSession.setState(ArenaState.RECRUITING);
        plugin.getLogger().info("参加者募集を開始しました: " + activeSession.getName());

        // 全オンラインプレイヤーにチップ使用を許可
        ChipPlugin chipPlugin = getChipPlugin();
        if (chipPlugin != null) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                chipPlugin.allowPlayer(p.getUniqueId());
            }
        }

        int interval = plugin.getConfig().getInt("odds-broadcast-interval", 30);
        if (interval <= 0) {
            plugin.getLogger().warning("odds-broadcast-interval が 0 以下のためデフォルト値 (30) を使用します。");
            interval = 30;
        }
        oddsBroadcastTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (activeSession != null
                    && (activeSession.getState() == ArenaState.BETTING
                        || activeSession.getState() == ArenaState.BLIND)) {
                bettingManager.broadcastOdds(activeSession);
            }
        }, interval * 20L, interval * 20L);

        return true;
    }

    /**
     * 参加者を締め切り、BETTING フェーズに移行する。
     *
     * <p>RECRUITING → BETTING に遷移し、確定した参加者一覧をブロードキャスト。
     * {@code bettingSeconds > 0} の場合はベット制限時間タイマーを開始する。
     *
     * @param bettingSeconds ベット制限時間（秒）。0以下の場合はタイマーなし
     */
    public void lockParticipants(int bettingSeconds) {
        if (activeSession == null || activeSession.getState() != ArenaState.RECRUITING) return;

        cancelRecruitingTimer();
        activeSession.setState(ArenaState.BETTING);
        plugin.getLogger().info("参加者を締め切りました: " + activeSession.getName());

        // ── 参加者一覧アナウンス ──
        Bukkit.broadcastMessage(ArenaMessages.SEPARATOR);
        Bukkit.broadcastMessage(ArenaMessages.PREFIX + ChatColor.GOLD + ChatColor.BOLD
                + "🔒 参加者締切 & ベット受付開始！");
        Bukkit.broadcastMessage("");

        for (String team : activeSession.getTeamNames()) {
            ChatColor color = activeSession.getTeamColor(team);
            String label = ArenaMessages.formatTeamLabel(activeSession, team);

            // メンバー名一覧を作成（Mobチームでもプレイヤーがいれば表示）
            StringBuilder members = new StringBuilder();
            List<UUID> memberIds = activeSession.getTeamMembers(team);
            for (int i = 0; i < memberIds.size(); i++) {
                Player p = Bukkit.getPlayer(memberIds.get(i));
                if (p != null) {
                    if (i > 0) members.append(", ");
                    members.append(p.getName());
                }
            }
            if (members.length() > 0) {
                members.append(" (").append(label).append(")");
            } else {
                members.append(label);
            }

            Bukkit.broadcastMessage(ArenaMessages.PREFIX + "  " + color + "■ " + team
                    + ChatColor.WHITE + ": " + ChatColor.GRAY + members);
        }

        Bukkit.broadcastMessage("");

        // ジャックポット残高表示
        JackpotManager jackpot = plugin.getJackpotManager();
        if (jackpot != null && jackpot.getBalance() > 0) {
            Bukkit.broadcastMessage(ArenaMessages.PREFIX + ChatColor.GOLD
                    + "🎰 ジャックポット積立: " + ChatColor.YELLOW
                    + ChipManager.formatAmount(jackpot.getBalance()) + " E");
            Bukkit.broadcastMessage("");
        }

        Bukkit.broadcastMessage(ArenaMessages.PREFIX + ChatColor.YELLOW
                + "/bet <チーム名> <金額>" + ChatColor.DARK_GRAY + " でコマンドからベットする");
        Bukkit.broadcastMessage(ArenaMessages.PREFIX + ChatColor.GRAY
                + "/chip <額面> <枚数>" + ChatColor.DARK_GRAY + " または "
                + ChatColor.GRAY + "/chip <金額>" + ChatColor.DARK_GRAY + " でチップを購入して設置");
        Bukkit.broadcastMessage("");
        Bukkit.broadcastMessage(ArenaMessages.PREFIX + ChatColor.AQUA
                + "💡 闘技者は /arena deathmatch <金額> でデスマッチ提案可能！");

        if (bettingSeconds > 0) {
            Bukkit.broadcastMessage(ArenaMessages.PREFIX + ChatColor.YELLOW + ChatColor.BOLD
                    + "⏱ ベット制限時間: " + bettingSeconds + "秒");
        }
        Bukkit.broadcastMessage(ArenaMessages.SEPARATOR);

        // ベットエリアのパーティクル表示開始
        startRegionParticles();

        // タイマー開始
        if (bettingSeconds > 0) {
            scheduleBettingTimer(bettingSeconds);
        }
    }

    /**
     * ベット受付を締め切る（試合は開始しない）。
     *
     * @return 成功した場合 {@code true}
     */
    public boolean closeBetting() {
        if (activeSession == null
                || (activeSession.getState() != ArenaState.BETTING
                    && activeSession.getState() != ArenaState.BLIND)) return false;

        // 投票中にCLOSED移行 → 自動却下
        if (activeChallenge != null) {
            cancelVoteTimer();
            activeChallenge = null;
            Bukkit.broadcastMessage(ArenaMessages.PREFIX + ChatColor.YELLOW
                    + "✗ ベット締切のため、デスマッチ投票が自動却下されました。");
        }

        activeSession.setState(ArenaState.CLOSED);
        cancelAllTasks();
        plugin.getLogger().info("ベットを締め切りました: " + activeSession.getName());
        return true;
    }

    /**
     * ベット受付の制限時間タイマーを開始する。
     *
     * <p>残り時間のカウントダウンをブロードキャストし、
     * 時間切れで自動的に {@link #closeBetting()} を呼ぶ。
     *
     * @param seconds 制限時間（秒）
     */
    public void scheduleBettingTimer(int seconds) {
        cancelBettingTimer();
        final int[] remaining = {seconds};

        // ブラインド移行タイミングを計算
        boolean blindEnabled = plugin.getConfig().getBoolean("blind.enabled", true);
        int blindSeconds = plugin.getConfig().getInt("blind.seconds-before-close", 30);
        // ブラインド移行残り秒数（タイマーの残り秒数がこの値以下でBLINDに移行）
        final int blindAt = (blindEnabled && blindSeconds > 0 && blindSeconds < seconds)
                ? blindSeconds : -1;

        bettingTimerTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (activeSession == null
                    || (activeSession.getState() != ArenaState.BETTING
                        && activeSession.getState() != ArenaState.BLIND)) {
                cancelBettingTimer();
                return;
            }

            int r = remaining[0];

            // ── ブラインド移行 ──
            if (blindAt > 0 && r == blindAt
                    && activeSession.getState() == ArenaState.BETTING) {
                activeSession.setState(ArenaState.BLIND);
                Bukkit.broadcastMessage(ArenaMessages.SEPARATOR);
                Bukkit.broadcastMessage(ArenaMessages.PREFIX + ChatColor.YELLOW
                        + "⏱ 残り " + r + "秒でベット締切");
                Bukkit.broadcastMessage(ArenaMessages.SEPARATOR);
            }

            // アナウンスタイミング: 60,30,10,5,4,3,2,1
            if (r == 60 || r == 30 || r == 10 || (r <= 5 && r >= 1)) {
                ChatColor urgency = r <= 5 ? ChatColor.RED : ChatColor.YELLOW;
                Bukkit.broadcastMessage(ArenaMessages.PREFIX + urgency + ChatColor.BOLD
                        + "残り " + r + "秒" + ChatColor.RESET + ChatColor.GRAY + " でベット締切！");
            }

            if (r <= 0) {
                // 自動締切
                closeBetting();
                ArenaSession session = activeSession;
                Bukkit.broadcastMessage(ArenaMessages.SEPARATOR);
                Bukkit.broadcastMessage(ArenaMessages.PREFIX + ChatColor.RED + ChatColor.BOLD
                        + "⏰ 時間切れ！ベット締め切り！");
                Bukkit.broadcastMessage(ArenaMessages.PREFIX + ChatColor.GRAY
                        + "これ以上ベットすることはできません。");
                Bukkit.broadcastMessage("");
                if (session != null) {
                    bettingManager.broadcastOdds(session);
                }
                Bukkit.broadcastMessage(ArenaMessages.PREFIX + ChatColor.GRAY
                        + "試合開始を待っています… " + ChatColor.YELLOW + "/arena start");
                Bukkit.broadcastMessage(ArenaMessages.SEPARATOR);
                return;
            }

            remaining[0]--;
        }, 0L, 20L); // 1秒ごと
    }

    /**
     * 参加者募集（RECRUITING）の制限時間タイマーを開始する。
     *
     * <p>残り時間のカウントダウンをブロードキャストし、
     * 時間切れで自動的に {@link #lockParticipants(int)} を呼ぶ。
     *
     * @param seconds 制限時間（秒）
     */
    public void scheduleRecruitingTimer(int seconds) {
        cancelRecruitingTimer();
        final int[] remaining = {seconds};

        recruitingTimerTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (activeSession == null || activeSession.getState() != ArenaState.RECRUITING) {
                cancelRecruitingTimer();
                return;
            }

            int r = remaining[0];

            // 残り10秒の通知
            if (r == 10) {
                Bukkit.broadcastMessage(ArenaMessages.PREFIX + ChatColor.YELLOW + ChatColor.BOLD
                        + "⏱ あと10秒で参加締切！");
            }

            if (r <= 0) {
                cancelRecruitingTimer();

                // バリデーション: 2チーム以上にメンバーが必要
                int teamsWithMembers = 0;
                for (String team : activeSession.getTeamNames()) {
                    if (activeSession.getEffectiveTeamSize(team) > 0) {
                        teamsWithMembers++;
                    }
                }
                if (teamsWithMembers < 2) {
                    Bukkit.broadcastMessage(ArenaMessages.PREFIX + ChatColor.RED
                            + "参加者が不足しているため、募集を延長します。");
                    return;
                }

                // 自動ロック: config の default-betting-duration を使用
                int bettingDuration = plugin.getConfig().getInt("default-betting-duration", 0);
                lockParticipants(bettingDuration);
                return;
            }

            remaining[0]--;
        }, 0L, 20L); // 1秒ごと
    }

    /**
     * 募集タイマーをキャンセルする。
     */
    private void cancelRecruitingTimer() {
        if (recruitingTimerTask != null) {
            recruitingTimerTask.cancel();
            recruitingTimerTask = null;
        }
    }

    /**
     * ベット制限時間タイマーをキャンセルする。
     */
    private void cancelBettingTimer() {
        if (bettingTimerTask != null) {
            bettingTimerTask.cancel();
            bettingTimerTask = null;
        }
    }

    /**
     * 試合を開始する（ベット締切）。
     *
     * <p>待機場が設定されているチームのプレイヤー・Mobを自動検出し、
     * TP先へ転送する。
     */
    public boolean startMatch() {
        if (activeSession == null) return false;
        ArenaState currentState = activeSession.getState();
        if (currentState != ArenaState.CLOSED) return false;

        // TP先未設定チェック
        for (String team : activeSession.getTeamNames()) {
            TeamAreaConfig areaConfig = activeSession.getTeamAreaConfig(team);
            if (areaConfig == null || areaConfig.getDestination() == null) {
                ChatColor teamColor = activeSession.getTeamColor(team);
                Bukkit.broadcastMessage(ArenaMessages.PREFIX + ChatColor.RED
                        + "✗ " + teamColor + team + ChatColor.RED + " のTP先が未設定です。"
                        + ChatColor.GRAY + " → /arena team dest " + team);
                return false;
            }
        }

        // ── 闘技者オンラインチェック ──
        List<String> offlineFighters = new ArrayList<>();
        for (String team : activeSession.getTeamNames()) {
            if (activeSession.isMobTeam(team)) continue;
            for (UUID fighterId : activeSession.getTeamMembers(team)) {
                Player fighter = Bukkit.getPlayer(fighterId);
                if (fighter == null || !fighter.isOnline()) {
                    OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(fighterId);
                    offlineFighters.add(offlinePlayer.getName() != null ? offlinePlayer.getName() : fighterId.toString());
                }
            }
        }
        if (!offlineFighters.isEmpty()) {
            Bukkit.broadcastMessage(ArenaMessages.PREFIX + ChatColor.RED
                    + "⚠ 以下の闘技者がオフラインのため開始できません:");
            for (String name : offlineFighters) {
                Bukkit.broadcastMessage(ArenaMessages.PREFIX + ChatColor.RED + "  - " + name);
            }
            return false;
        }

        // ── 参加費・DM参加費の所持金チェック＆徴収 ──
        Economy economy = plugin.getEconomy();
        long entryFee = plugin.getConfig().getLong("entry-fee", 0);
        boolean isDeathmatch = activeSession.getMatchMode() == MatchMode.DEATHMATCH;
        long dmFeePerPerson = isDeathmatch ? activeSession.getDeathmatchEntryFee() : 0;
        long requiredPerPerson = entryFee + dmFeePerPerson;

        // 残高チェック（entry-fee > 0 またはデスマッチの場合）
        if (requiredPerPerson > 0 && economy != null) {
            List<String> shortFighters = new ArrayList<>();
            for (String team : activeSession.getTeamNames()) {
                if (activeSession.isMobTeam(team)) continue;
                for (UUID fighterId : activeSession.getTeamMembers(team)) {
                    Player fighter = Bukkit.getPlayer(fighterId);
                    if (fighter == null) continue;
                    double balance = economy.getBalance(fighter);
                    if (balance < requiredPerPerson) {
                        String detail = "必要: " + ChipManager.formatAmount(requiredPerPerson) + " E";
                        if (entryFee > 0 && dmFeePerPerson > 0) {
                            detail += " [参加費" + ChipManager.formatAmount(entryFee)
                                    + "+DM" + ChipManager.formatAmount(dmFeePerPerson) + "]";
                        }
                        detail += " / 所持: " + ChipManager.formatAmount((long) balance) + " E";
                        shortFighters.add(fighter.getName() + " (" + detail + ")");
                    }
                }
            }
            if (!shortFighters.isEmpty()) {
                Bukkit.broadcastMessage(ArenaMessages.PREFIX + ChatColor.RED
                        + "⚠ 以下の闘技者の所持金が不足しています:");
                for (String info : shortFighters) {
                    Bukkit.broadcastMessage(ArenaMessages.PREFIX + ChatColor.RED + "  - " + info);
                }
                return false;
            }

            // ── 参加費徴収 ──
            for (String team : activeSession.getTeamNames()) {
                if (activeSession.isMobTeam(team)) continue;
                for (UUID fighterId : activeSession.getTeamMembers(team)) {
                    Player fighter = Bukkit.getPlayer(fighterId);
                    if (fighter == null) continue;

                    // entry-fee 徴収 → entryFeePool (後でジャックポットへ)
                    if (entryFee > 0) {
                        economy.withdrawPlayer(fighter, entryFee);
                        activeSession.addToEntryFeePool(entryFee);
                        fighter.sendMessage(ArenaMessages.PREFIX + ChatColor.YELLOW
                                + "参加費 " + ChipManager.formatAmount(entryFee) + " E を徴収しました。");
                    }

                    // DM参加費 徴収 → deathmatchPool
                    if (dmFeePerPerson > 0) {
                        economy.withdrawPlayer(fighter, dmFeePerPerson);
                        activeSession.setDeathmatchPool(
                                activeSession.getDeathmatchPool() + dmFeePerPerson);
                        fighter.sendMessage(ArenaMessages.PREFIX + ChatColor.YELLOW
                                + "DM参加費 " + ChipManager.formatAmount(dmFeePerPerson) + " E を徴収しました。");
                    }
                }
            }
        }

        activeSession.setState(ArenaState.ACTIVE);
        cancelAllTasks();
        eliminatedPlayers.clear();

        // 地形追跡開始
        terrainManager.startTracking(activeSession);

        // WorldGuard: 参加者のみ入場可・退場不可
        guardManager.lockField(activeSession);

        // カスタムイベント発火（情報通知・キャンセル不可）
        Bukkit.getPluginManager().callEvent(new ArenaBettingCloseEvent(activeSession));
        plugin.getLogger().info("試合を開始しました: " + activeSession.getName());

        // プレイヤー待機場からスキャンして登録＋TP
        scanAndTeleportPlayers();
        // モンスターチームのMobを待機場からスキャンしてTP
        scanAndTeleportMobs();

        // バニラ Scoreboard Team と連携
        registerScoreboardTeams();

        return true;
    }

    // ── 待機場スキャン＆TP ──

    /**
     * プレイヤー待機場からプレイヤーをスキャンし、チームに登録してTP先へ転送する。
     */
    private void scanAndTeleportPlayers() {
        if (activeSession == null) return;

        for (String team : activeSession.getTeamNames()) {
            TeamAreaConfig areaConfig = activeSession.getTeamAreaConfig(team);
            if (areaConfig == null) continue;

            // 待機場内のプレイヤーをチームに自動登録
            List<Player> playersInArea = areaConfig.scanPlayers();
            for (Player player : playersInArea) {
                if (!activeSession.isFighter(player.getUniqueId())) {
                    activeSession.addTeamMember(team, player.getUniqueId());
                }
            }

            List<UUID> members = activeSession.getTeamMembers(team);
            if (members.isEmpty()) continue;

            // TP先が設定されていればチーム全員を転送
            Location dest = areaConfig.getDestination();
            if (dest != null && dest.getWorld() != null) {
                for (UUID memberId : members) {
                    Player player = Bukkit.getPlayer(memberId);
                    if (player != null && player.isOnline()) {
                        player.teleport(applyTeleportOffset(dest));
                    }
                }

                ChatColor teamColor = activeSession.getTeamColor(team);
                Bukkit.broadcastMessage(ArenaMessages.PREFIX + teamColor + team
                        + ChatColor.GRAY + " の " + ChatColor.WHITE + members.size() + "人"
                        + ChatColor.GRAY + " がアリーナに入場しました！");
            }
        }
    }

    /**
     * モンスターチームのMobを待機場からスキャンし、アリーナへ転送する。
     */
    private void scanAndTeleportMobs() {
        if (activeSession == null) return;

        for (String team : activeSession.getTeamNames()) {
            TeamAreaConfig config = activeSession.getTeamAreaConfig(team);
            if (config == null) continue;

            List<LivingEntity> mobs = config.scanEntities();
            if (mobs.isEmpty()) continue;

            Location dest = config.getDestination();
            if (dest == null || dest.getWorld() == null) continue;

            int count = 0;
            for (LivingEntity mob : mobs) {
                Location loc = applyTeleportOffset(dest);
                mob.teleport(loc);
                activeSession.trackMob(mob.getUniqueId(), team);
                count++;
            }

            // Mobがいたら自動でMobチームマーク
            activeSession.markAsMobTeam(team);

            ChatColor teamColor = activeSession.getTeamColor(team);
            Bukkit.broadcastMessage(ArenaMessages.PREFIX + teamColor + team
                    + ChatColor.GRAY + " のモンスター " + ChatColor.WHITE + count + "体"
                    + ChatColor.GRAY + " が出現しました！");
        }
    }

    // ── 勝敗判定 ──

    /**
     * 勝者を宣言し、配当処理を行う。
     */
    public boolean declareWinner(String winningTeam) {
        if (winningTeam == null) return false;
        if (activeSession == null || activeSession.getState() != ArenaState.ACTIVE) return false;
        if (!activeSession.hasTeam(winningTeam)) return false;

        cancelAllTasks();
        guardManager.unlockField();

        activeSession.setWinningTeam(winningTeam);
        activeSession.setState(ArenaState.FINISHED);
        plugin.getLogger().info("勝者宣言: " + winningTeam + " (セッション: " + activeSession.getName() + ")");

        ChatColor winnerColor = activeSession.getTeamColor(winningTeam);

        Bukkit.broadcastMessage("");
        Bukkit.broadcastMessage(ArenaMessages.SEPARATOR);
        Bukkit.broadcastMessage(ArenaMessages.PREFIX + winnerColor + ChatColor.BOLD
                + winningTeam + " の勝利！");
        Bukkit.broadcastMessage(ArenaMessages.SEPARATOR);
        Bukkit.broadcastMessage("");

        try {
            try {
                bettingManager.calculateAndDistributePayout(activeSession, winningTeam);
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "配当処理中にエラー", e);
            }

            // 地形復元開始（非同期。TerrainManager が sessionName/fieldConfig を独自保持するため、
            // この後の activeSession=null でも問題ない）
            terrainManager.finishAndFlush();

            // カスタムイベント発火（情報通知）
            Bukkit.getPluginManager().callEvent(new ArenaWinnerDeclaredEvent(activeSession, winningTeam));

            // 全プレイヤーのチップを換金しランキングに登録
            cashoutAllPlayers();

            // 残存Mobをワールドから削除
            cleanupMobs();
        } finally {
            cleanupSession();
        }

        return true;
    }

    /**
     * セッションをキャンセルし、全額返金する。
     *
     * <p>任意の状態から実行可能。ACTIVE 状態の場合は全プレイヤーのチップも換金する。
     */
    public boolean cancelArena() {
        if (activeSession == null) return false;
        boolean wasActive = activeSession.getState() == ArenaState.ACTIVE;
        plugin.getLogger().warning("闘技場セッションがキャンセルされました: " + activeSession.getName());
        cancelAllTasks();
        guardManager.unlockField();
        cancelDeathmatch(); // 投票中ならキャンセル

        try {
            bettingManager.refundAll(activeSession);

            // 地形復元開始
            terrainManager.finishAndFlush();

            activeSession.setState(ArenaState.FINISHED);

            // 参加費返金（entry-fee + DM参加費）
            // 参加費返金: プールの合計を頭割りで返金
            // (startMatch時に均等額で徴収済みなので人数で割れば1人分に戻る)
            Economy economy = plugin.getEconomy();
            if (economy != null) {
                long entryFeePool = activeSession.getEntryFeePool();
                long dmPool = activeSession.getDeathmatchPool();
                int totalPlayerFighters = 0;
                for (String team : activeSession.getTeamNames()) {
                    if (activeSession.isMobTeam(team)) continue;
                    totalPlayerFighters += activeSession.getTeamMembers(team).size();
                }
                long entryFeePerPerson = totalPlayerFighters > 0 ? entryFeePool / totalPlayerFighters : 0;
                long dmFeePerPerson = totalPlayerFighters > 0 ? dmPool / totalPlayerFighters : 0;

                for (String team : activeSession.getTeamNames()) {
                    if (activeSession.isMobTeam(team)) continue;
                    for (UUID playerId : activeSession.getTeamMembers(team)) {
                        long refund = entryFeePerPerson + dmFeePerPerson;
                        if (refund > 0) {
                            OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(playerId);
                            economy.depositPlayer(offlinePlayer, refund);

                            Player onlinePlayer = Bukkit.getPlayer(playerId);
                            if (onlinePlayer != null && onlinePlayer.isOnline()) {
                                onlinePlayer.sendMessage(ArenaMessages.PREFIX + ChatColor.YELLOW
                                        + "参加費 " + ChipManager.formatAmount(refund) + " E を返金しました。");
                            }
                        }
                    }
                }
            }

            // スポーン済みモンスターを削除
            cleanupMobs();

            // 試合中だった場合は全プレイヤーのチップを換金
            if (wasActive) {
                cashoutAllPlayers();
            }
        } finally {
            cleanupSession();
        }

        return true;
    }

    // ── 死亡処理 ──

    /**
     * 戦闘員の死亡を処理し、勝利条件を判定する。
     */
    public void onFighterDeath(UUID playerId) {
        if (activeSession == null || activeSession.getState() != ArenaState.ACTIVE) return;
        String team = activeSession.getPlayerTeam(playerId);
        if (team == null) return;

        eliminatedPlayers.add(playerId);

        WinCondition winCondition = plugin.getWinCondition();
        String winner = winCondition.checkWinOnDeath(activeSession, playerId, eliminatedPlayers);

        if (winner != null) {
            declareWinner(winner);
            return;
        }

        // チーム全滅通知（Mobが生存していれば全滅ではない）
        if (activeSession.isMobTeam(team) && activeSession.hasAliveMobs(team)) {
            // Mobチームでモンスターがまだ生きている → チームは存続
            return;
        }
        List<UUID> teamMembers = activeSession.getTeamMembers(team);
        boolean teamEliminated = true;
        for (UUID member : teamMembers) {
            if (!eliminatedPlayers.contains(member)) {
                teamEliminated = false;
                break;
            }
        }
        if (teamEliminated && !(activeSession.isMobTeam(team) && activeSession.hasAliveMobs(team))) {
            activeSession.markTeamEliminated(team);
            ChatColor teamColor = activeSession.getTeamColor(team);
            Bukkit.broadcastMessage(ArenaMessages.PREFIX + teamColor + ChatColor.BOLD
                    + team + ChatColor.RESET + ChatColor.RED + " が全滅しました！");
        }
    }

    /**
     * モンスター死亡を処理し、チーム全滅判定を行う。
     *
     * @param entityId 死亡したエンティティの UUID
     */
    public void onMobDeath(UUID entityId) {
        if (activeSession == null || activeSession.getState() != ArenaState.ACTIVE) return;

        String team = activeSession.getMobTeam(entityId);
        if (team == null) return;

        // removeMob を勝敗判定の後に行う（先に除去すると checkWinOnDeath がチームを検出できない）
        if (!activeSession.hasAliveMobs(team, entityId)) {
            // Mobチーム全滅 → 仮想メンバーを全員脱落扱いにする
            eliminatedPlayers.addAll(activeSession.getTeamMembers(team));
            // Mobチームにプレイヤーメンバーがいない場合でも
            // チーム自体を全滅とマークする（sentinel UUID）
            activeSession.markTeamEliminated(team);

            ChatColor teamColor = activeSession.getTeamColor(team);
            Bukkit.broadcastMessage(ArenaMessages.PREFIX + teamColor + ChatColor.BOLD
                    + team + ChatColor.RESET + ChatColor.RED + " のモンスターが全滅しました！");

            WinCondition winCondition = plugin.getWinCondition();
            String winner = winCondition.checkWinOnDeath(activeSession, entityId, eliminatedPlayers);
            if (winner != null) {
                declareWinner(winner);
            }
        }
        activeSession.removeMob(entityId);
    }

    /**
     * 指定UUIDがトラッキング中のモンスターかどうかを返す。
     */
    public boolean isTrackedMob(UUID entityId) {
        return activeSession != null && activeSession.getMobTeam(entityId) != null;
    }


    public Set<UUID> getEliminatedPlayers() {
        return Collections.unmodifiableSet(eliminatedPlayers);
    }

    // ── 内部ユーティリティ ──

    private static final double TP_SPREAD_RANGE = 0.5;
    private static final int TP_SPREAD_MULTIPLIER = 3;

    /**
     * TP先座標にランダムオフセットを付与する。
     *
     * @param base 基準座標
     * @return オフセットが適用された新しい座標
     */
    private Location applyTeleportOffset(Location base) {
        Location loc = base.clone().add(
                (Math.random() - TP_SPREAD_RANGE) * TP_SPREAD_MULTIPLIER,
                0,
                (Math.random() - TP_SPREAD_RANGE) * TP_SPREAD_MULTIPLIER);
        // 安全なTP先を探す: 足元と頭の位置がソリッドでないこと
        org.bukkit.World world = loc.getWorld();
        if (world != null) {
            int baseY = loc.getBlockY();
            // 足元か頭がブロックに埋まっている場合、上方向に安全な場所を探す
            for (int dy = 0; dy <= 5; dy++) {
                Location check = loc.clone();
                check.setY(baseY + dy);
                if (!check.getBlock().getType().isSolid()
                        && !check.clone().add(0, 1, 0).getBlock().getType().isSolid()) {
                    loc.setY(baseY + dy);
                    return loc;
                }
            }
        }
        return loc;
    }

    /**
     * オッズブロードキャストおよび関連する全タイマーを停止する。
     *
     * <p>オッズ定期送信、ベットタイマー、募集タイマー、投票タイマー、
     * パーティクル表示をすべて停止する。セッション終了時の一括クリーンアップ用。
     */
    private void cancelAllTasks() {
        if (oddsBroadcastTask != null) {
            oddsBroadcastTask.cancel();
            oddsBroadcastTask = null;
        }
        cancelBettingTimer();
        cancelVoteTimer();
        cancelRecruitingTimer();
        stopRegionParticles();
    }

    // ══════════════════════════════════════
    //  ベットエリア パーティクル表示
    // ══════════════════════════════════════

    /**
     * ベットエリアの境界線をパーティクルで表示するタスクを開始する。
     *
     * <p>各チームのベットエリアの辺をチームカラーのダストパーティクルで描画する。
     * 1秒ごとに更新。
     */
    void startRegionParticles() {
        stopRegionParticles();
        if (activeSession == null) return;

        regionParticleTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (activeSession == null
                    || (activeSession.getState() != ArenaState.BETTING
                        && activeSession.getState() != ArenaState.BLIND)) {
                stopRegionParticles();
                return;
            }

            for (String team : activeSession.getTeamNames()) {
                if (!regionManager.hasBettingRegion(team)) continue;
                BettingRegion region = regionManager.getBettingRegion(team);
                if (region == null) continue;

                World world = Bukkit.getWorld(region.worldName());
                if (world == null) continue;

                Color color = chatColorToColor(activeSession.getTeamColor(team));
                Particle.DustOptions dust = new Particle.DustOptions(color, 1.0F);

                drawRegionEdges(world, region, dust);
            }
        }, 0L, 20L); // 1秒ごと
    }

    private void stopRegionParticles() {
        if (regionParticleTask != null) {
            regionParticleTask.cancel();
            regionParticleTask = null;
        }
    }

    /**
     * 直方体の12辺をパーティクルで描画する。
     */
    private void drawRegionEdges(World world, BettingRegion region, Particle.DustOptions dust) {
        double step = 0.5; // パーティクル間隔（ブロック）
        double x1 = region.minX();
        double y1 = region.minY();
        double z1 = region.minZ();
        double x2 = region.maxX() + 1.0;
        double y2 = region.maxY() + 1.0;
        double z2 = region.maxZ() + 1.0;

        // X軸に平行な4辺
        for (double x = x1; x <= x2; x += step) {
            world.spawnParticle(Particle.REDSTONE, x, y1, z1, 1, dust);
            world.spawnParticle(Particle.REDSTONE, x, y1, z2, 1, dust);
            world.spawnParticle(Particle.REDSTONE, x, y2, z1, 1, dust);
            world.spawnParticle(Particle.REDSTONE, x, y2, z2, 1, dust);
        }
        // Z軸に平行な4辺
        for (double z = z1; z <= z2; z += step) {
            world.spawnParticle(Particle.REDSTONE, x1, y1, z, 1, dust);
            world.spawnParticle(Particle.REDSTONE, x2, y1, z, 1, dust);
            world.spawnParticle(Particle.REDSTONE, x1, y2, z, 1, dust);
            world.spawnParticle(Particle.REDSTONE, x2, y2, z, 1, dust);
        }
        // Y軸に平行な4辺
        for (double y = y1; y <= y2; y += step) {
            world.spawnParticle(Particle.REDSTONE, x1, y, z1, 1, dust);
            world.spawnParticle(Particle.REDSTONE, x2, y, z1, 1, dust);
            world.spawnParticle(Particle.REDSTONE, x1, y, z2, 1, dust);
            world.spawnParticle(Particle.REDSTONE, x2, y, z2, 1, dust);
        }
    }

    /**
     * ChatColor を Bukkit Color に変換する。
     */
    private static Color chatColorToColor(ChatColor chatColor) {
        return switch (chatColor) {
            case RED, DARK_RED -> Color.RED;
            case BLUE, DARK_BLUE -> Color.BLUE;
            case GREEN, DARK_GREEN -> Color.GREEN;
            case YELLOW, GOLD -> Color.YELLOW;
            case AQUA, DARK_AQUA -> Color.AQUA;
            case LIGHT_PURPLE, DARK_PURPLE -> Color.PURPLE;
            case WHITE -> Color.WHITE;
            case GRAY, DARK_GRAY -> Color.GRAY;
            case BLACK -> Color.fromRGB(30, 30, 30);
            default -> Color.WHITE;
        };
    }

    /**
     * スポーン済みのトラッキングモンスターをワールドから削除する。
     *
     * <p>例外が発生した場合はログ出力のみ行い、呼び出し元へは伝搬しない。
     * finally ブロックからも安全に呼び出すことができる。
     */
    private void cleanupMobs() {
        if (activeSession == null) return;
        try {
            for (World world : Bukkit.getWorlds()) {
                for (Entity entity : world.getEntities()) {
                    if (activeSession.getMobTeam(entity.getUniqueId()) != null) {
                        entity.remove();
                    }
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("トラッキングMob除去中にエラーが発生しました: " + e.getMessage());
        }
    }

    /**
     * セッション終了時の共通クリーンアップ処理。
     *
     * <p>バニラ Scoreboard Team の解除、トラッキングMobの除去、
     * セッションデータのクリアを行う。{@code declareWinner} および
     * {@code cancelArena} の finally ブロックから呼び出される。
     */
    private void cleanupSession() {
        unregisterScoreboardTeams();
        // チップ使用許可を全解除
        ChipPlugin chipPlugin = getChipPlugin();
        if (chipPlugin != null) chipPlugin.clearAllAllowed();
        if (activeSession != null) {
            if (activeSession.getState() != ArenaState.FINISHED) {
                activeSession.setState(ArenaState.FINISHED);
            }
            activeSession.resetSession();
            activeSession = null;
        }
        eliminatedPlayers.clear();
        regionManager.clearRegions();
        // デスマッチ投票状態をリセット
        activeChallenge = null;
        deathmatchProposalCount = 0;
        cancelVoteTimer();
    }

    /**
     * バニラ Scoreboard Team にプレイヤーを登録する。
     *
     * <p>試合開始時に呼び出され、各チームに対応する Scoreboard Team を作成（または取得）し、
     * プレイヤーを登録する。チームカラーが自動設定され、味方討ち（Friendly Fire）は無効になる。
     */
    private void registerScoreboardTeams() {
        if (activeSession == null) return;
        if (getMainScoreboard() == null) {
            plugin.getLogger().warning("ScoreboardManager が利用できません。チーム登録をスキップします。");
            return;
        }

        List<String> teamNames = activeSession.getTeamNames();
        for (String teamName : teamNames) {
            if (activeSession.isMobTeam(teamName)) continue;
            ensureScoreboardTeam(teamName);

            // メンバーを登録
            for (UUID memberId : activeSession.getTeamMembers(teamName)) {
                Player player = Bukkit.getPlayer(memberId);
                if (player != null && player.isOnline()) {
                    addToScoreboardTeam(teamName, player);
                }
            }
        }
    }

    /**
     * バニラ Scoreboard Team を作成（または取得）してカラー・FF設定を行う。
     *
     * @param teamName チーム名
     */
    public void ensureScoreboardTeam(String teamName) {
        if (activeSession == null) return;
        try {
            Scoreboard sb = getMainScoreboard();
            if (sb == null) return;

            Team sbTeam = sb.getTeam(teamName);
            if (sbTeam == null) {
                sbTeam = sb.registerNewTeam(teamName);
            }

            ChatColor arenaColor = activeSession.getTeamColor(teamName);
            sbTeam.setColor(arenaColor);
            sbTeam.setPrefix(arenaColor.toString());
            sbTeam.setAllowFriendlyFire(false);
        } catch (Exception e) {
            plugin.getLogger().fine("Scoreboard操作スキップ: " + e.getMessage());
        }
    }

    /**
     * バニラ Scoreboard Team にプレイヤーを追加する。
     *
     * @param teamName チーム名
     * @param player   追加するプレイヤー
     */
    public void addToScoreboardTeam(String teamName, Player player) {
        try {
            Scoreboard sb = getMainScoreboard();
            if (sb == null) return;
            Team sbTeam = sb.getTeam(teamName);
            if (sbTeam == null) {
                ensureScoreboardTeam(teamName);
                sbTeam = sb.getTeam(teamName);
            }
            if (sbTeam != null) {
                sbTeam.addEntry(player.getName());
            }
        } catch (Exception e) {
            plugin.getLogger().fine("Scoreboard操作スキップ: " + e.getMessage());
        }
    }

    /**
     * バニラ Scoreboard Team からプレイヤーを削除する。
     *
     * @param teamName チーム名
     * @param player   削除するプレイヤー
     */
    public void removeFromScoreboardTeam(String teamName, Player player) {
        try {
            Scoreboard sb = getMainScoreboard();
            if (sb == null) return;
            Team sbTeam = sb.getTeam(teamName);
            if (sbTeam != null) {
                sbTeam.removeEntry(player.getName());
            }
        } catch (Exception e) {
            plugin.getLogger().fine("Scoreboard操作スキップ: " + e.getMessage());
        }
    }

    /**
     * バニラ Scoreboard Team を解除する。
     *
     * <p>試合終了・キャンセル時に呼び出され、登録したチームを unregister する。
     */
    private void unregisterScoreboardTeams() {
        if (activeSession == null) return;
        Scoreboard sb = getMainScoreboard();
        if (sb == null) {
            plugin.getLogger().warning("ScoreboardManager が利用できません。チーム解除をスキップします。");
            return;
        }

        for (String teamName : activeSession.getTeamNames()) {
            Team sbTeam = sb.getTeam(teamName);
            if (sbTeam != null) {
                try {
                    sbTeam.unregister();
                } catch (IllegalStateException e) {
                    plugin.getLogger().warning("Scoreboard Team 解除失敗: " + teamName);
                }
            }
        }
    }

    public void shutdown() {
        cancelAllTasks();
        cancelVoteTimer();
        terrainManager.cancelAndClear();
        if (activeSession != null) cancelArena();
    }

    // ══════════════════════════════════════
    //  デスマッチ投票管理
    // ══════════════════════════════════════

    /**
     * デスマッチ提案アクティブチャレンジを返す。
     *
     * @return アクティブなデスマッチ提案。なければ {@code null}
     */
    public DeathmatchChallenge getDeathmatchChallenge() {
        return activeChallenge;
    }

    /**
     * 現セッションのデスマッチ提案回数を返す。
     *
     * @return 提案回数
     */
    public int getProposalCount() {
        return deathmatchProposalCount;
    }

    /**
     * デスマッチを提案する。
     *
     * <p>バリデーションを行い、問題がなければ {@link DeathmatchChallenge} を作成し、
     * 投票UIをブロードキャストし、20秒の投票タイマーを開始する。
     *
     * @param proposer    提案者のUUID
     * @param totalAmount 提案総額
     * @return エラーメッセージ。成功時は {@code null}
     */
    public String proposeDeathmatch(UUID proposer, long totalAmount) {
        if (activeSession == null) return ArenaMessages.MSG_NO_SESSION;

        ArenaState state = activeSession.getState();
        if (state != ArenaState.BETTING && state != ArenaState.BLIND) {
            return "デスマッチ提案はベット受付中（BETTING/BLIND）のみ可能です。";
        }

        String proposerTeam = activeSession.getPlayerTeam(proposer);
        if (proposerTeam == null) return "闘技者のみデスマッチを提案できます。";

        // 全闘技者数を計算（プレイヤーのみ）
        int totalFighters = 0;
        Map<String, Integer> teamSizes = new LinkedHashMap<>();
        for (String team : activeSession.getTeamNames()) {
            if (activeSession.isMobTeam(team)) continue;
            int size = activeSession.getTeamMembers(team).size();
            teamSizes.put(team, size);
            totalFighters += size;
        }
        if (totalFighters <= 0) return "闘技者がいません。";

        // 1人あたりDM参加費
        long perPerson = totalAmount / totalFighters;
        if (perPerson <= 0) return "1人あたりの参加費が0になります。金額を大きくしてください。";

        // 通常参加費
        long entryFee = plugin.getConfig().getLong("entry-fee", 0);
        long requiredPerPerson = perPerson + entryFee;

        // 全闘技者の所持金チェック
        Economy economy = plugin.getEconomy();
        if (economy != null) {
            for (String team : teamSizes.keySet()) {
                for (UUID fighterId : activeSession.getTeamMembers(team)) {
                    Player fighter = Bukkit.getPlayer(fighterId);
                    if (fighter == null || !fighter.isOnline()) continue; // ログアウト中は投票時に再チェック
                    double balance = economy.getBalance(fighter);
                    if (balance < requiredPerPerson) {
                        String detail = "必要: " + ChipManager.formatAmount(requiredPerPerson) + " E";
                        if (entryFee > 0) {
                            detail += " [参加費" + ChipManager.formatAmount(entryFee)
                                    + "+DM" + ChipManager.formatAmount(perPerson) + "]";
                        }
                        detail += " / 所持: " + ChipManager.formatAmount((long) balance) + " E";
                        return "⚠ " + fighter.getName() + " の所持金が不足（" + detail + "）";
                    }
                }
            }
        }

        // DM総額を再計算（端数切り捨て後）
        long actualTotalPool = perPerson * totalFighters;

        // チャレンジ作成
        activeChallenge = new DeathmatchChallenge(
                proposer, proposerTeam, perPerson, actualTotalPool, teamSizes);
        deathmatchProposalCount++;

        // 投票UIブロードキャスト
        DeathmatchSubCommand.broadcastVoteUI(activeSession, activeChallenge, entryFee);

        // 投票タイマー開始（20秒）
        scheduleVoteTimer();

        // 即時判定（提案者の自動賛成で決着する場合: 1人チーム×2 等）
        DeathmatchChallenge.VoteResult result = activeChallenge.evaluateResult();
        if (result != DeathmatchChallenge.VoteResult.PENDING) {
            handleVoteResult(result);
        }

        return null; // 成功
    }

    /**
     * デスマッチ投票を処理する。
     *
     * @param player プレイヤーUUID
     * @param accept 賛成なら {@code true}
     * @return エラーメッセージ。成功時は {@code null}
     */
    public String castDeathmatchVote(UUID player, boolean accept) {
        if (activeChallenge == null) return "現在投票中のデスマッチ提案はありません。";
        if (activeSession == null) return ArenaMessages.MSG_NO_SESSION;

        String team = activeSession.getPlayerTeam(player);
        if (team == null) return "闘技者のみ投票できます。";

        if (activeChallenge.hasVoted(player)) return "既に投票済みです。";

        DeathmatchChallenge.VoteResult result = activeChallenge.vote(player, team, accept);

        // 投票者に確認メッセージ
        Player voter = Bukkit.getPlayer(player);
        if (voter != null && voter.isOnline()) {
            String voteStr = accept ? (ChatColor.GREEN + "賛成") : (ChatColor.RED + "反対");
            voter.sendMessage(ArenaMessages.PREFIX + voteStr + ChatColor.GRAY + " 票を投じました。");
        }

        // 結果判定
        if (result != DeathmatchChallenge.VoteResult.PENDING) {
            handleVoteResult(result);
        } else {
            // 進捗更新ブロードキャスト
            DeathmatchSubCommand.broadcastVoteProgress(activeSession, activeChallenge, -1);
        }

        return null; // 成功
    }

    /**
     * アクティブなデスマッチ提案をキャンセルする。
     */
    public void cancelDeathmatch() {
        cancelVoteTimer();
        activeChallenge = null;
    }

    /**
     * 投票結果を処理する。
     *
     * @param result 投票結果
     */
    private void handleVoteResult(DeathmatchChallenge.VoteResult result) {
        cancelVoteTimer();

        if (result == DeathmatchChallenge.VoteResult.ACCEPTED) {
            // デスマッチ成立
            if (activeSession != null && activeChallenge != null) {
                activeSession.setMatchMode(MatchMode.DEATHMATCH);
                activeSession.setDeathmatchEntryFee(activeChallenge.getPerPersonFee());
                // DMプールは /arena start 時に実際に徴収して設定する

                Bukkit.broadcastMessage(ArenaMessages.SEPARATOR);
                Bukkit.broadcastMessage(ArenaMessages.PREFIX + ChatColor.RED + ChatColor.BOLD
                        + "🔥🔥🔥 デスマッチ成立！ 🔥🔥🔥");
                Bukkit.broadcastMessage(ArenaMessages.PREFIX + ChatColor.YELLOW
                        + "参加費: " + ChipManager.formatAmount(activeChallenge.getPerPersonFee())
                        + " E / 人（試合開始時に徴収）");
                Bukkit.broadcastMessage(ArenaMessages.SEPARATOR);
            }
        } else if (result == DeathmatchChallenge.VoteResult.REJECTED) {
            // デスマッチ却下
            Bukkit.broadcastMessage(ArenaMessages.PREFIX + ChatColor.YELLOW
                    + "✗ デスマッチは却下されました。通常試合で続行します。");

            int maxProposals = plugin.getConfig().getInt("deathmatch.max-proposals", 2);
            int remaining = maxProposals - deathmatchProposalCount;
            if (remaining > 0) {
                Bukkit.broadcastMessage(ArenaMessages.PREFIX + ChatColor.GRAY
                        + "再提案可能（残り" + remaining + "回）");
            }
        }

        activeChallenge = null;
    }

    /**
     * 投票タイマーを開始する。
     *
     * <p>タイムアウト時に自動で却下処理を行う。
     */
    private void scheduleVoteTimer() {
        cancelVoteTimer();
        final int[] remaining = {VOTE_TIMEOUT_SECONDS};

        voteTimerTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (activeChallenge == null || activeSession == null) {
                cancelVoteTimer();
                return;
            }

            int r = remaining[0];

            // 進捗通知
            if (matchesVoteProgressNotify(r)) {
                DeathmatchSubCommand.broadcastVoteProgress(activeSession, activeChallenge, r);
            }

            if (r <= 0) {
                // タイムアウト → 自動却下
                Bukkit.broadcastMessage(ArenaMessages.PREFIX + ChatColor.YELLOW
                        + "デスマッチは自動却下されました。");
                handleVoteResult(DeathmatchChallenge.VoteResult.REJECTED);
                return;
            }

            remaining[0]--;
        }, 0L, 20L); // 1秒ごと
    }

    /**
     * 投票タイマーをキャンセルする。
     */
    private void cancelVoteTimer() {
        if (voteTimerTask != null) {
            voteTimerTask.cancel();
            voteTimerTask = null;
        }
    }

    /**
     * 全オンラインプレイヤーのチップを換金し、アリーナランキングに記録する。
     *
     * <p>アリーナ終了時に呼び出される。ChipPlugin でチップを換金し、
     * RankingManager にアリーナカテゴリの損益を記録する。
     */
    private void cashoutAllPlayers() {
        try {
            ChipPlugin chipPlugin = getChipPlugin();
            if (chipPlugin == null) return;

            RankingManager rankingManager = chipPlugin.getRankingManager();

            for (Player p : Bukkit.getOnlinePlayers()) {
                long chipValue = plugin.getChipManager().calculateTotalValue(p);
                if (chipValue <= 0) continue;

                // セッション購入額を CasinoManager から取得
                long purchased = 0;
                try {
                    Plugin casinoPlugin = Bukkit.getPluginManager().getPlugin("CasinoCore");
                    if (casinoPlugin instanceof CasinoCore casinoCore) {
                        purchased = casinoCore.getCasinoManager().getSessionPurchases(p.getUniqueId());
                    }
                } catch (Exception e) {
                    plugin.getLogger().fine("CasinoCore連携スキップ: " + e.getMessage());
                }

                // ChipPlugin で換金
                long cashoutAmount = chipPlugin.cashoutPlayer(p);

                // アリーナランキングに記録
                if (rankingManager != null && purchased > 0) {
                    long netResult = cashoutAmount - purchased;
                    rankingManager.updateRanking("arena", p.getUniqueId(), netResult);
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("チップ換金中にエラーが発生: " + e.getMessage());
        }
    }

    /**
     * ChipLib プラグインインスタンスを取得するヘルパー。
     *
     * @return ChipPlugin インスタンス。未ロードの場合は null
     */
    private ChipPlugin getChipPlugin() {
        Plugin p = Bukkit.getPluginManager().getPlugin("ChipLib");
        return (p instanceof ChipPlugin) ? (ChipPlugin) p : null;
    }

    /**
     * メインスコアボードを安全に取得するヘルパー。
     *
     * @return メインスコアボード。ScoreboardManager が利用不可の場合は {@code null}
     */
    private org.bukkit.scoreboard.Scoreboard getMainScoreboard() {
        var manager = Bukkit.getScoreboardManager();
        return manager != null ? manager.getMainScoreboard() : null;
    }

    /**
     * 指定秒数が投票進捗通知タイミングに該当するか判定する。
     */
    private static boolean matchesVoteProgressNotify(int seconds) {
        for (int t : VOTE_PROGRESS_NOTIFY_AT) {
            if (seconds == t) return true;
        }
        return false;
    }
}
