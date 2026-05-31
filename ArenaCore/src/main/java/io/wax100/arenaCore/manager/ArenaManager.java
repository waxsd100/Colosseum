package io.wax100.arenaCore.manager;

import io.wax100.arenaCore.ArenaCore;
import io.wax100.arenaCore.event.ArenaBettingCloseEvent;
import io.wax100.arenaCore.event.ArenaBettingOpenEvent;
import io.wax100.arenaCore.event.ArenaWinnerDeclaredEvent;
import io.wax100.arenaCore.model.ArenaSession;
import io.wax100.arenaCore.model.ArenaState;
import io.wax100.arenaCore.model.TeamAreaConfig;
import io.wax100.arenaCore.model.ArenaFieldConfig;
import io.wax100.arenaCore.model.BettingRegion;
import io.wax100.arenaCore.util.ArenaMessages;
import io.wax100.arenaCore.wincondition.WinCondition;
import io.wax100.chipLib.ChipManager;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.*;

/**
 * 闘技場セッションのライフサイクルを管理するクラス。
 *
 * <p>セッションの作成・チーム編成・賭け受付・試合開始・勝者宣言・キャンセルの
 * 一連のフローを制御する。プレイヤーおよびモンスターの待機場管理も担当する。
 */
public class ArenaManager {

    private final ArenaCore plugin;
    private final BettingManager bettingManager;
    private final RegionManager regionManager;
    private final TerrainManager terrainManager;

    private ArenaSession activeSession;
    private BukkitTask oddsBroadcastTask;
    private final Set<UUID> eliminatedPlayers = new HashSet<>();

    public ArenaManager(ArenaCore plugin, BettingManager bettingManager,
                        RegionManager regionManager, TerrainManager terrainManager) {
        this.plugin = Objects.requireNonNull(plugin, "plugin must not be null");
        this.bettingManager = Objects.requireNonNull(bettingManager, "bettingManager must not be null");
        this.regionManager = Objects.requireNonNull(regionManager, "regionManager must not be null");
        this.terrainManager = Objects.requireNonNull(terrainManager, "terrainManager must not be null");
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
        for (String mob : data.mobTeams()) {
            session.markAsMobTeam(mob);
        }
        for (var entry : data.bettingRegions().entrySet()) {
            regionManager.registerBettingRegion(entry.getKey(), entry.getValue());
        }
        for (var colorEntry : data.teamColors().entrySet()) {
            session.setTeamColor(colorEntry.getKey(), colorEntry.getValue());
        }
        return session;
    }

    /**
     * 賭け受付を開始する。
     */
    public boolean openBetting() {
        if (activeSession == null || activeSession.getState() != ArenaState.SETUP) return false;

        int teamsWithMembers = 0;
        for (String team : activeSession.getTeamNames()) {
            if (activeSession.getTeamSize(team) > 0) {
                teamsWithMembers++;
            } else if (activeSession.isMobTeam(team)) {
                // Mobチームは待機場にMobが実際にいる場合のみカウント
                TeamAreaConfig config = activeSession.getTeamAreaConfig(team);
                if (config != null && !config.scanEntities().isEmpty()) {
                    teamsWithMembers++;
                }
            }
        }
        if (teamsWithMembers < 2) return false;

        // 設定漏れ警告
        for (String team : activeSession.getTeamNames()) {
            TeamAreaConfig areaConfig = activeSession.getTeamAreaConfig(team);
            ChatColor teamColor = activeSession.getTeamColor(team);
            if (areaConfig == null) {
                Bukkit.broadcastMessage(ArenaMessages.PREFIX + ChatColor.YELLOW
                        + "⚠ " + teamColor + team + ChatColor.YELLOW + " の待機場が未設定です。");
            } else if (areaConfig.getDestination() == null) {
                Bukkit.broadcastMessage(ArenaMessages.PREFIX + ChatColor.YELLOW
                        + "⚠ " + teamColor + team + ChatColor.YELLOW + " のTP先が未設定です。");
            }
        }
        if (activeSession.getFieldConfig() == null) {
            Bukkit.broadcastMessage(ArenaMessages.PREFIX + ChatColor.YELLOW
                    + "⚠ 戦闘エリアが未設定です。地形復元が行われません。");
        }

        // カスタムイベント発火（キャンセル可能）
        ArenaBettingOpenEvent openEvent = new ArenaBettingOpenEvent(activeSession);
        Bukkit.getPluginManager().callEvent(openEvent);
        if (openEvent.isCancelled()) {
            plugin.getLogger().info("賭け受付開始がイベントリスナーによりキャンセルされました。");
            return false;
        }

        activeSession.setState(ArenaState.BETTING);
        plugin.getLogger().info("賭け受付を開始しました: " + activeSession.getName());

        int interval = plugin.getConfig().getInt("odds-broadcast-interval", 30);
        if (interval <= 0) {
            plugin.getLogger().warning("odds-broadcast-interval が 0 以下のためデフォルト値 (200) を使用します。");
            interval = 200;
        }
        oddsBroadcastTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (activeSession != null && activeSession.getState() == ArenaState.BETTING) {
                bettingManager.broadcastOdds(activeSession);
            }
        }, interval * 20L, interval * 20L);

        return true;
    }

    /**
     * 試合を開始する（賭け締切）。
     *
     * <p>待機場が設定されているチームのプレイヤー・Mobを自動検出し、
     * TP先へ転送する。
     */
    public boolean startMatch() {
        if (activeSession == null || activeSession.getState() != ArenaState.BETTING) return false;
        activeSession.setState(ArenaState.ACTIVE);
        stopOddsBroadcast();
        eliminatedPlayers.clear();

        // 地形追跡開始
        terrainManager.startTracking(activeSession);

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
            if (activeSession.isMobTeam(team)) continue;

            TeamAreaConfig areaConfig = activeSession.getTeamAreaConfig(team);
            if (areaConfig == null) continue;

            // 待機場内のプレイヤーをチームに自動登録
            List<Player> playersInArea = areaConfig.scanPlayers();
            for (Player player : playersInArea) {
                if (!activeSession.isFighter(player.getUniqueId())) {
                    activeSession.addTeamMember(team, player.getUniqueId());
                }
            }

            // TP先が設定されていればチーム全員を転送
            Location dest = areaConfig.getDestination();
            if (dest != null && dest.getWorld() != null) {
                List<UUID> members = activeSession.getTeamMembers(team);
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
            if (!activeSession.isMobTeam(team)) continue;

            TeamAreaConfig config = activeSession.getTeamAreaConfig(team);
            if (config == null) {
                plugin.getLogger().warning("Mob待機場が未設定です: " + team);
                continue;
            }

            Location dest = config.getDestination();
            if (dest == null || dest.getWorld() == null) {
                plugin.getLogger().warning("MobのTP先が未設定です: " + team);
                continue;
            }

            List<LivingEntity> mobs = config.scanEntities();
            if (mobs.isEmpty()) {
                plugin.getLogger().warning("待機場にMobがいません: " + team);
                continue;
            }

            int count = 0;
            for (LivingEntity mob : mobs) {
                Location loc = applyTeleportOffset(dest);
                mob.teleport(loc);
                activeSession.trackMob(mob.getUniqueId(), team);
                count++;
            }

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
        if (activeSession == null || activeSession.getState() != ArenaState.ACTIVE) return false;
        if (!activeSession.hasTeam(winningTeam)) return false;

        stopOddsBroadcast();

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
            bettingManager.calculateAndDistributePayout(activeSession, winningTeam);

            // 地形復元開始（非同期。TerrainManager が sessionName/fieldConfig を独自保持するため、
            // この後の activeSession=null でも問題ない）
            terrainManager.finishAndFlush();

            // カスタムイベント発火（情報通知）
            Bukkit.getPluginManager().callEvent(new ArenaWinnerDeclaredEvent(activeSession, winningTeam));

            // 残存Mobをワールドから削除
            cleanupMobs();
        } finally {
            // バニラ Scoreboard Team を解除
            unregisterScoreboardTeams();
            cleanupMobs();
            activeSession.clearAllData();
            activeSession = null;
            eliminatedPlayers.clear();
            regionManager.clearRegions();
        }

        return true;
    }

    /**
     * セッションをキャンセルし、全額返金する。
     */
    public boolean cancelArena() {
        if (activeSession == null) return false;
        plugin.getLogger().warning("闘技場セッションがキャンセルされました: " + activeSession.getName());
        stopOddsBroadcast();

        try {
            bettingManager.refundAll(activeSession);

            // 地形復元開始
            terrainManager.finishAndFlush();

            long entryFee = plugin.getConfig().getLong("entry-fee", 0);
            if (entryFee > 0) {
                Economy economy = plugin.getEconomy();
                if (economy != null) {
                    for (String team : activeSession.getTeamNames()) {
                        if (activeSession.isMobTeam(team)) continue; // Mobチームは参加費不要
                        for (UUID playerId : activeSession.getTeamMembers(team)) {
                            // オフラインプレイヤーにも返金するため OfflinePlayer を使用
                            org.bukkit.OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(playerId);
                            economy.depositPlayer(offlinePlayer, entryFee);

                            Player onlinePlayer = Bukkit.getPlayer(playerId);
                            if (onlinePlayer != null && onlinePlayer.isOnline()) {
                                onlinePlayer.sendMessage(ArenaMessages.PREFIX + ChatColor.YELLOW
                                        + "参加費 " + ChipManager.formatAmount(entryFee) + " E を返金しました。");
                            }
                        }
                    }
                }
            }

            // スポーン済みモンスターを削除
            cleanupMobs();
        } finally {
            // バニラ Scoreboard Team を解除
            unregisterScoreboardTeams();
            cleanupMobs();
            activeSession.setState(ArenaState.FINISHED);
            activeSession.clearAllData();
            activeSession = null;
            eliminatedPlayers.clear();
            regionManager.clearRegions();
        }

        return true;
    }

    // ── 死亡処理 ──

    /**
     * 戦闘員の死亡を処理し、勝利条件を判定する。
     */
    public boolean onFighterDeath(UUID playerId) {
        if (activeSession == null || activeSession.getState() != ArenaState.ACTIVE) return false;
        String team = activeSession.getPlayerTeam(playerId);
        if (team == null) return false;

        eliminatedPlayers.add(playerId);

        WinCondition winCondition = plugin.getWinCondition();
        String winner = winCondition.checkWinOnDeath(activeSession, playerId, eliminatedPlayers);

        if (winner != null) {
            declareWinner(winner);
            return true;
        }

        // チーム全滅通知
        List<UUID> teamMembers = activeSession.getTeamMembers(team);
        boolean teamEliminated = true;
        for (UUID member : teamMembers) {
            if (!eliminatedPlayers.contains(member)) {
                teamEliminated = false;
                break;
            }
        }
        if (teamEliminated) {
            ChatColor teamColor = activeSession.getTeamColor(team);
            Bukkit.broadcastMessage(ArenaMessages.PREFIX + teamColor + ChatColor.BOLD
                    + team + ChatColor.RESET + ChatColor.RED + " が全滅しました！");
        }

        return false;
    }

    /**
     * モンスター死亡を処理し、チーム全滅判定を行う。
     *
     * @param entityId 死亡したエンティティの UUID
     * @return 勝敗が確定した場合 true
     */
    public boolean onMobDeath(UUID entityId) {
        if (activeSession == null || activeSession.getState() != ArenaState.ACTIVE) return false;

        String team = activeSession.getMobTeam(entityId);
        if (team == null) return false;

        activeSession.removeMob(entityId);

        if (!activeSession.hasAliveMobs(team)) {
            // Mobチーム全滅 → 仮想メンバーを全員脱落扱いにする
            for (UUID member : activeSession.getTeamMembers(team)) {
                eliminatedPlayers.add(member);
            }
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
                return true;
            }
        }

        return false;
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
        return base.clone().add(
                (Math.random() - TP_SPREAD_RANGE) * TP_SPREAD_MULTIPLIER,
                0,
                (Math.random() - TP_SPREAD_RANGE) * TP_SPREAD_MULTIPLIER);
    }

    private void stopOddsBroadcast() {
        if (oddsBroadcastTask != null) {
            oddsBroadcastTask.cancel();
            oddsBroadcastTask = null;
        }
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
     * バニラ Scoreboard Team にプレイヤーを登録する。
     *
     * <p>試合開始時に呼び出され、各チームに対応する Scoreboard Team を作成（または取得）し、
     * プレイヤーを登録する。チームカラーが自動設定され、味方討ち（Friendly Fire）は無効になる。
     */
    private void registerScoreboardTeams() {
        if (activeSession == null) return;
        if (Bukkit.getScoreboardManager() == null) {
            plugin.getLogger().warning("ScoreboardManager が利用できません。チーム登録をスキップします。");
            return;
        }
        Scoreboard sb = Bukkit.getScoreboardManager().getMainScoreboard();

        List<String> teamNames = activeSession.getTeamNames();
        for (int i = 0; i < teamNames.size(); i++) {
            String teamName = teamNames.get(i);
            if (activeSession.isMobTeam(teamName)) continue;

            // 既存チームがあれば取得、なければ新規作成
            Team sbTeam = sb.getTeam(teamName);
            if (sbTeam == null) {
                sbTeam = sb.registerNewTeam(teamName);
            }

            // チームカラーの設定
            ChatColor arenaColor = activeSession.getTeamColor(teamName);
            sbTeam.setColor(arenaColor);
            sbTeam.setPrefix(arenaColor.toString());

            // Friendly Fire を無効化
            sbTeam.setAllowFriendlyFire(false);

            // メンバーを登録
            for (UUID memberId : activeSession.getTeamMembers(teamName)) {
                Player player = Bukkit.getPlayer(memberId);
                if (player != null && player.isOnline()) {
                    sbTeam.addEntry(player.getName());
                }
            }

            plugin.getLogger().info("Scoreboard Team 登録: " + teamName
                    + " (" + activeSession.getTeamMembers(teamName).size() + "人)");
        }
    }

    /**
     * バニラ Scoreboard Team を解除する。
     *
     * <p>試合終了・キャンセル時に呼び出され、登録したチームを unregister する。
     */
    private void unregisterScoreboardTeams() {
        if (activeSession == null) return;
        if (Bukkit.getScoreboardManager() == null) {
            plugin.getLogger().warning("ScoreboardManager が利用できません。チーム解除をスキップします。");
            return;
        }
        Scoreboard sb = Bukkit.getScoreboardManager().getMainScoreboard();

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
        stopOddsBroadcast();
        terrainManager.cancelAndClear();
        if (activeSession != null) cancelArena();
    }
}
