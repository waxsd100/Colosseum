package io.wax100.arenaCore.listener;

import io.wax100.arenaCore.ArenaCore;
import io.wax100.arenaCore.manager.ArenaManager;
import io.wax100.arenaCore.model.ArenaSession;
import io.wax100.arenaCore.model.ArenaState;
import io.wax100.arenaCore.util.ArenaMessages;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
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
        handleFighterElimination(event.getEntity(),
                ChatColor.RED + " が倒されました！");
    }

    /**
     * 試合中の戦闘員ログアウト（死亡扱い）。
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        handleFighterElimination(event.getPlayer(),
                ChatColor.YELLOW + " がログアウトしました（死亡扱い）");
    }

    /**
     * 戦闘員脱落の共通処理。
     *
     * <p>セッション状態・戦闘員チェックを行い、脱落メッセージを配信して
     * {@link ArenaManager#onFighterDeath} をトリガーする。
     *
     * @param player 脱落したプレイヤー
     * @param suffix ブロードキャストメッセージの末尾部分（色コード込み）
     */
    private void handleFighterElimination(Player player, String suffix) {
        ArenaManager manager = plugin.getArenaManager();
        if (!manager.hasActiveSession()) return;

        ArenaSession session = manager.getActiveSession();
        if (session.getState() != ArenaState.ACTIVE) return;

        if (!session.isFighter(player.getUniqueId())) return;
        if (manager.getEliminatedPlayers().contains(player.getUniqueId())) return;

        String team = session.getPlayerTeam(player.getUniqueId());
        if (team == null) return;

        ChatColor teamColor = session.getTeamColor(team);

        Bukkit.broadcastMessage(ArenaMessages.PREFIX + teamColor + player.getName()
                + ChatColor.GRAY + " (" + teamColor + team + ChatColor.GRAY + ")"
                + suffix);

        // Scoreboard Team からも削除（タブリスト表示をクリーンに保つ）
        plugin.getArenaManager().removeFromScoreboardTeam(team, player);

        manager.onFighterDeath(player.getUniqueId());
    }

    /**
     * モンスター死亡時の処理。
     *
     * <p>トラッキング中のモンスターが死亡した場合、チーム全滅判定をトリガーする。
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity instanceof Player) return; // プレイヤー死亡は onPlayerDeath で処理

        ArenaManager manager = plugin.getArenaManager();
        if (!manager.hasActiveSession()) return;
        if (!manager.isTrackedMob(entity.getUniqueId())) return;

        ArenaSession session = manager.getActiveSession();
        if (session.getState() != ArenaState.ACTIVE) return;

        String team = session.getMobTeam(entity.getUniqueId());
        if (team != null) {
            // onMobDeath の前にカウントを取得し、死亡した1体を引く
            int aliveAfterDeath = Math.max(0, session.getAliveMobCount(team) - 1);
            ChatColor teamColor = session.getTeamColor(team);
            Bukkit.broadcastMessage(ArenaMessages.PREFIX + teamColor + team
                    + ChatColor.GRAY + " の " + ChatColor.WHITE + entity.getName()
                    + ChatColor.RED + " が倒されました！"
                    + ChatColor.GRAY + " (残り " + ChatColor.WHITE
                    + aliveAfterDeath + "体" + ChatColor.GRAY + ")");
        }

        manager.onMobDeath(entity.getUniqueId());
    }
}
