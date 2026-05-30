package io.wax100.arenaCore.manager;

import io.wax100.arenaCore.ArenaCore;
import io.wax100.arenaCore.model.ArenaSession;
import io.wax100.arenaCore.model.ArenaState;
import io.wax100.arenaCore.model.TeamAreaConfig;
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

import java.util.*;
import java.util.Objects;

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

    private ArenaSession activeSession;
    private BukkitTask oddsBroadcastTask;
    private final Set<UUID> eliminatedPlayers = new HashSet<>();

    public ArenaManager(ArenaCore plugin, BettingManager bettingManager, RegionManager regionManager) {
        this.plugin = Objects.requireNonNull(plugin, "plugin must not be null");
        this.bettingManager = Objects.requireNonNull(bettingManager, "bettingManager must not be null");
        this.regionManager = Objects.requireNonNull(regionManager, "regionManager must not be null");
    }

    public boolean hasActiveSession() { return activeSession != null; }
    public ArenaSession getActiveSession() { return activeSession; }

    /**
     * 闘技場セッションを作成する。
     */
    public ArenaSession createArena(String name, List<String> teamNames) {
        if (activeSession != null) return null;
        activeSession = new ArenaSession(name, teamNames);
        eliminatedPlayers.clear();
        regionManager.clearRegions();
        return activeSession;
    }

    /**
     * チームにメンバーを追加する。参加費がある場合は徴収する。
     */
    public boolean addTeamMember(String teamName, Player player) {
        if (activeSession == null || activeSession.getState() != ArenaState.SETUP) return false;
        if (!activeSession.hasTeam(teamName)) return false;
        if (activeSession.isFighter(player.getUniqueId())) return false;

        long entryFee = plugin.getConfig().getLong("entry-fee", 0);
        if (entryFee > 0) {
            Economy economy = plugin.getEconomy();
            if (economy == null || !economy.has(player, entryFee)) {
                player.sendMessage(ArenaMessages.PREFIX + ChatColor.RED
                        + "参加費 " + ChatColor.YELLOW
                        + ChipManager.formatAmount(entryFee) + " E"
                        + ChatColor.RED + " が不足しています。");
                return false;
            }
            economy.withdrawPlayer(player, entryFee);
            activeSession.addEntryFee(entryFee);
        }

        return activeSession.addTeamMember(teamName, player.getUniqueId());
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

        activeSession.setState(ArenaState.BETTING);

        int interval = plugin.getConfig().getInt("odds-broadcast-interval", 30);
        if (interval > 0) {
            oddsBroadcastTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
                if (activeSession != null && activeSession.getState() == ArenaState.BETTING) {
                    bettingManager.broadcastOdds(activeSession);
                }
            }, interval * 20L, interval * 20L);
        }

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

        // プレイヤー待機場からスキャンして登録＋TP
        scanAndTeleportPlayers();
        // モンスターチームのMobを待機場からスキャンしてTP
        scanAndTeleportMobs();
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
            // NOTE: startMatch で状態が ACTIVE に遷移済みのため、
            //       addTeamMember（SETUP ガード）ではなく直接セッションに追加する
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
                        double offsetX = (Math.random() - 0.5) * 3;
                        double offsetZ = (Math.random() - 0.5) * 3;
                        player.teleport(dest.clone().add(offsetX, 0, offsetZ));
                    }
                }

                int teamIndex = activeSession.getTeamNames().indexOf(team);
                ChatColor teamColor = ArenaMessages.getTeamColor(teamIndex);
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
                double offsetX = (Math.random() - 0.5) * 3;
                double offsetZ = (Math.random() - 0.5) * 3;
                Location loc = dest.clone().add(offsetX, 0, offsetZ);
                mob.teleport(loc);
                activeSession.trackMob(mob.getUniqueId(), team);
                count++;
            }

            int teamIndex = activeSession.getTeamNames().indexOf(team);
            ChatColor teamColor = ArenaMessages.getTeamColor(teamIndex);
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

        activeSession.setWinningTeam(winningTeam);
        activeSession.setState(ArenaState.FINISHED);

        int winnerIndex = activeSession.getTeamNames().indexOf(winningTeam);
        ChatColor winnerColor = ArenaMessages.getTeamColor(winnerIndex);

        Bukkit.broadcastMessage("");
        Bukkit.broadcastMessage(ArenaMessages.SEPARATOR);
        Bukkit.broadcastMessage(ArenaMessages.PREFIX + winnerColor + ChatColor.BOLD
                + winningTeam + " の勝利！");
        Bukkit.broadcastMessage(ArenaMessages.SEPARATOR);
        Bukkit.broadcastMessage("");

        bettingManager.calculateAndDistributePayout(activeSession, winningTeam);

        // 残存Mobをワールドから削除
        cleanupMobs();

        activeSession.clearAllData();
        activeSession = null;
        eliminatedPlayers.clear();
        regionManager.clearRegions();

        return true;
    }

    /**
     * セッションをキャンセルし、全額返金する。
     */
    public boolean cancelArena() {
        if (activeSession == null) return false;
        stopOddsBroadcast();

        bettingManager.refundAll(activeSession);

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

        activeSession.setState(ArenaState.FINISHED);
        activeSession.clearAllData();
        activeSession = null;
        eliminatedPlayers.clear();
        regionManager.clearRegions();

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
            int teamIndex = activeSession.getTeamNames().indexOf(team);
            ChatColor teamColor = ArenaMessages.getTeamColor(teamIndex);
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

            int teamIndex = activeSession.getTeamNames().indexOf(team);
            ChatColor teamColor = ArenaMessages.getTeamColor(teamIndex);
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

    public boolean isFighter(UUID playerId) {
        return activeSession != null && activeSession.isFighter(playerId);
    }

    public Set<UUID> getEliminatedPlayers() {
        return Collections.unmodifiableSet(eliminatedPlayers);
    }

    // ── 内部ユーティリティ ──

    private void stopOddsBroadcast() {
        if (oddsBroadcastTask != null) {
            oddsBroadcastTask.cancel();
            oddsBroadcastTask = null;
        }
    }

    /**
     * スポーン済みのトラッキングモンスターをワールドから削除する。
     */
    private void cleanupMobs() {
        if (activeSession == null) return;
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (activeSession.getMobTeam(entity.getUniqueId()) != null) {
                    entity.remove();
                }
            }
        }
    }

    public void shutdown() {
        stopOddsBroadcast();
        if (activeSession != null) cancelArena();
    }
}
