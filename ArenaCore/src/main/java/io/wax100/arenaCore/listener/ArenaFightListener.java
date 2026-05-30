package io.wax100.arenaCore.listener;

import io.wax100.arenaCore.ArenaCore;
import io.wax100.arenaCore.manager.ArenaManager;
import io.wax100.arenaCore.model.ArenaSession;
import io.wax100.arenaCore.model.ArenaState;
import io.wax100.arenaCore.util.ArenaMessages;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * 戦闘イベントリスナー。
 *
 * <p>戦闘員の死亡・ログアウトを監視し、勝利条件判定をトリガーする。
 */
public class ArenaFightListener implements Listener {

    private final ArenaCore plugin;

    public ArenaFightListener(ArenaCore plugin) {
        this.plugin = plugin;
    }

    /**
     * 戦闘員死亡時の処理。
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        ArenaManager manager = plugin.getArenaManager();
        if (!manager.hasActiveSession()) return;

        ArenaSession session = manager.getActiveSession();
        if (session.getState() != ArenaState.ACTIVE) return;

        Player player = event.getEntity();
        if (!session.isFighter(player.getUniqueId())) return;

        String team = session.getPlayerTeam(player.getUniqueId());
        if (team == null) return;

        int teamIndex = session.getTeamNames().indexOf(team);
        ChatColor teamColor = ArenaMessages.getTeamColor(teamIndex);

        Bukkit.broadcastMessage(ArenaMessages.PREFIX + teamColor + player.getName()
                + ChatColor.GRAY + " (" + teamColor + team + ChatColor.GRAY + ")"
                + ChatColor.RED + " が倒されました！");

        manager.onFighterDeath(player.getUniqueId());
    }

    /**
     * 試合中の戦闘員ログアウト（死亡扱い）。
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        ArenaManager manager = plugin.getArenaManager();
        if (!manager.hasActiveSession()) return;

        ArenaSession session = manager.getActiveSession();
        if (session.getState() != ArenaState.ACTIVE) return;

        Player player = event.getPlayer();
        if (!session.isFighter(player.getUniqueId())) return;
        if (manager.getEliminatedPlayers().contains(player.getUniqueId())) return;

        String team = session.getPlayerTeam(player.getUniqueId());
        int teamIndex = session.getTeamNames().indexOf(team);
        ChatColor teamColor = ArenaMessages.getTeamColor(teamIndex);

        Bukkit.broadcastMessage(ArenaMessages.PREFIX + teamColor + player.getName()
                + ChatColor.GRAY + " (" + teamColor + team + ChatColor.GRAY + ")"
                + ChatColor.YELLOW + " がログアウトしました（死亡扱い）");

        manager.onFighterDeath(player.getUniqueId());
    }
}
