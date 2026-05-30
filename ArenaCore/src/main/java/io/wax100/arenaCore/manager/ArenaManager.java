package io.wax100.arenaCore.manager;

import io.wax100.arenaCore.ArenaCore;
import io.wax100.arenaCore.model.ArenaSession;
import io.wax100.arenaCore.model.ArenaState;
import io.wax100.arenaCore.util.ArenaMessages;
import io.wax100.arenaCore.wincondition.WinCondition;
import io.wax100.chipLib.ChipManager;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

/**
 * 闘技場セッションのライフサイクルを管理するクラス。
 */
public class ArenaManager {

    private final ArenaCore plugin;
    private final BettingManager bettingManager;
    private final RegionManager regionManager;

    private ArenaSession activeSession;
    private BukkitTask oddsBroadcastTask;
    private final Set<UUID> eliminatedPlayers = new HashSet<>();

    public ArenaManager(ArenaCore plugin, BettingManager bettingManager, RegionManager regionManager) {
        this.plugin = plugin;
        this.bettingManager = bettingManager;
        this.regionManager = regionManager;
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
            if (activeSession.getTeamSize(team) > 0) teamsWithMembers++;
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
     */
    public boolean startMatch() {
        if (activeSession == null || activeSession.getState() != ArenaState.BETTING) return false;
        activeSession.setState(ArenaState.ACTIVE);
        stopOddsBroadcast();
        eliminatedPlayers.clear();
        return true;
    }

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
                + "🏆 " + winningTeam + " の勝利！ 🏆");
        Bukkit.broadcastMessage(ArenaMessages.SEPARATOR);
        Bukkit.broadcastMessage("");

        bettingManager.calculateAndDistributePayout(activeSession, winningTeam);

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
                    for (UUID playerId : activeSession.getTeamMembers(team)) {
                        Player player = Bukkit.getPlayer(playerId);
                        if (player != null && player.isOnline()) {
                            economy.depositPlayer(player, entryFee);
                            player.sendMessage(ArenaMessages.PREFIX + ChatColor.YELLOW
                                    + "参加費 " + ChipManager.formatAmount(entryFee) + " E を返金しました。");
                        }
                    }
                }
            }
        }

        activeSession.setState(ArenaState.FINISHED);
        activeSession = null;
        eliminatedPlayers.clear();
        regionManager.clearRegions();

        return true;
    }

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

    public boolean isFighter(UUID playerId) {
        return activeSession != null && activeSession.isFighter(playerId);
    }

    public Set<UUID> getEliminatedPlayers() {
        return Collections.unmodifiableSet(eliminatedPlayers);
    }

    private void stopOddsBroadcast() {
        if (oddsBroadcastTask != null) {
            oddsBroadcastTask.cancel();
            oddsBroadcastTask = null;
        }
    }

    public void shutdown() {
        stopOddsBroadcast();
        if (activeSession != null) cancelArena();
    }
}
