package io.wax100.arenaCore.listener;

import io.wax100.arenaCore.ArenaCore;
import io.wax100.arenaCore.model.ArenaSession;
import io.wax100.arenaCore.model.ArenaState;
import io.wax100.arenaCore.model.TeamAreaConfig;
import io.wax100.arenaCore.util.ArenaMessages;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.UUID;

/**
 * 待機エリアへの入退場を監視し、チームメンバーの自動追加/削除を行うリスナー。
 *
 * <p>セッション状態が {@link ArenaState#SETUP} または {@link ArenaState#BETTING} の間のみ動作する。
 * プレイヤーが待機エリアに入ると自動的にチームに追加され、退場すると削除される。
 * モンスターチームのエリアには追加しない。
 */
public class ArenaTeamAreaListener implements Listener {

    private final ArenaCore plugin;

    public ArenaTeamAreaListener(ArenaCore plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        // ブロック単位で変化がない場合はスキップ（パフォーマンス対策）
        if (event.getFrom().getBlockX() == event.getTo().getBlockX()
                && event.getFrom().getBlockY() == event.getTo().getBlockY()
                && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
            return;
        }
        handleMovement(event.getPlayer(), event.getFrom(), event.getTo());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        handleMovement(event.getPlayer(), event.getFrom(), event.getTo());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        ArenaSession session = getActiveSession();
        if (session == null) return;
        if (!isAutoJoinState(session)) return;

        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        String currentTeam = session.getPlayerTeam(playerId);
        if (currentTeam != null && !session.isMobTeam(currentTeam)) {
            session.removeTeamMember(currentTeam, playerId);
            plugin.getArenaManager().removeFromScoreboardTeam(currentTeam, player);
        }
    }

    @EventHandler
    public void onPlayerJoin(org.bukkit.event.player.PlayerJoinEvent event) {
        // 1tick 遅延: ログイン直後は位置情報が確定していない場合がある
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            Player player = event.getPlayer();
            if (!player.isOnline()) return;

            ArenaSession session = getActiveSession();
            if (session == null) return;
            if (!isAutoJoinState(session)) return;

            UUID playerId = player.getUniqueId();
            if (session.isFighter(playerId)) return;

            String area = findTeamArea(session, player.getLocation());
            if (area != null) {
                session.addTeamMember(area, playerId);
                plugin.getArenaManager().addToScoreboardTeam(area, player);
                ChatColor color = session.getTeamColor(area);
                player.sendMessage(ArenaMessages.PREFIX + color + area
                        + ChatColor.GRAY + " に参加しました！");
            }
        }, 1L);
    }

    // ── 内部ロジック ──

    private void handleMovement(Player player, Location from, Location to) {
        ArenaSession session = getActiveSession();
        if (session == null) return;
        if (!isAutoJoinState(session)) return;

        UUID playerId = player.getUniqueId();
        String currentTeam = session.getPlayerTeam(playerId);

        // 現在入っているエリアと移動先エリアを判定
        String fromArea = findTeamArea(session, from);
        String toArea = findTeamArea(session, to);

        // 同じエリア内 → 何もしない
        if (java.util.Objects.equals(fromArea, toArea)) return;

        // エリアから退場
        if (currentTeam != null && !currentTeam.equals(toArea)) {
            session.removeTeamMember(currentTeam, playerId);
            plugin.getArenaManager().removeFromScoreboardTeam(currentTeam, player);
            ChatColor color = session.getTeamColor(currentTeam);
            player.sendMessage(ArenaMessages.PREFIX + color + currentTeam
                    + ChatColor.GRAY + " から離脱しました。");
        }

        // エリアに入場
        if (toArea != null && !session.isFighter(playerId)) {
            session.addTeamMember(toArea, playerId);
            plugin.getArenaManager().addToScoreboardTeam(toArea, player);
            ChatColor color = session.getTeamColor(toArea);
            player.sendMessage(ArenaMessages.PREFIX + color + toArea
                    + ChatColor.GRAY + " に参加しました！");
        }
    }

    /**
     * 指定座標がどのチームの待機エリアに含まれるかを返す。
     * モンスターチームのエリアは対象外。
     */
    private String findTeamArea(ArenaSession session, Location loc) {
        for (String team : session.getTeamNames()) {
            if (session.isMobTeam(team)) continue;
            TeamAreaConfig config = session.getTeamAreaConfig(team);
            if (config != null && config.contains(loc)) {
                return team;
            }
        }
        return null;
    }

    private ArenaSession getActiveSession() {
        return plugin.getArenaManager().getActiveSession();
    }

    private boolean isAutoJoinState(ArenaSession session) {
        ArenaState state = session.getState();
        return state == ArenaState.SETUP || state == ArenaState.BETTING;
    }
}
