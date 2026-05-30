package io.wax100.arenaCore.listener;

import io.wax100.arenaCore.ArenaCore;
import io.wax100.arenaCore.model.ArenaSession;
import io.wax100.arenaCore.model.TeamAreaConfig;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.entity.EntityBreakDoorEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;

import java.util.Iterator;

/**
 * 待機場のブロック保護リスナー。
 *
 * <p>待機エリア内でのMobによるブロック破壊・爆発・延焼を防止する。
 * セッションが存在し、待機エリアが設定されている間のみ有効。
 * プレイヤー待機場・モンスター待機場の両方を保護する。
 */
public class MobAreaProtectionListener implements Listener {

    private final ArenaCore plugin;

    public MobAreaProtectionListener(ArenaCore plugin) {
        this.plugin = plugin;
    }

    /**
     * クリーパー・ガスト・ウィザー等の爆発によるブロック破壊を防止する。
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        if (!plugin.getArenaManager().hasActiveSession()) return;

        Iterator<Block> it = event.blockList().iterator();
        while (it.hasNext()) {
            Block block = it.next();
            if (isInAnyWaitingArea(block.getLocation())) {
                it.remove();
            }
        }
    }

    /**
     * エンダーマンのブロック持ち去りを防止する。
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        if (!plugin.getArenaManager().hasActiveSession()) return;

        if (isInAnyWaitingArea(event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    /**
     * ゾンビのドア破壊を防止する。
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityBreakDoor(EntityBreakDoorEvent event) {
        if (!plugin.getArenaManager().hasActiveSession()) return;

        if (isInAnyWaitingArea(event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    /**
     * 待機場内のブロック延焼を防止する。
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBurn(BlockBurnEvent event) {
        if (!plugin.getArenaManager().hasActiveSession()) return;

        if (isInAnyWaitingArea(event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    /**
     * Mob起因の着火を防止する。
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockIgnite(BlockIgniteEvent event) {
        if (!plugin.getArenaManager().hasActiveSession()) return;

        if (event.getCause() == BlockIgniteEvent.IgniteCause.FLINT_AND_STEEL) return;

        if (isInAnyWaitingArea(event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    /**
     * 指定座標がいずれかの待機場内にあるかを判定する。
     */
    private boolean isInAnyWaitingArea(Location location) {
        ArenaSession session = plugin.getArenaManager().getActiveSession();
        if (session == null) return false;

        for (String team : session.getTeamNames()) {
            TeamAreaConfig config = session.getTeamAreaConfig(team);
            if (config != null && config.contains(location)) {
                return true;
            }
        }
        return false;
    }
}
