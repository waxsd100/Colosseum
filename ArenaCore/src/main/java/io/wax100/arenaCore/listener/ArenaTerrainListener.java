package io.wax100.arenaCore.listener;

import io.wax100.arenaCore.manager.TerrainManager;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.FallingBlock;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;

/**
 * 地形変更イベントリスナー。
 *
 * <p>戦闘エリア内のブロック破壊を監視し、
 * {@link TerrainManager} に復元対象として記録する。
 *
 * <p>監視対象:
 * <ul>
 *   <li>プレイヤー手動破壊（{@link BlockBreakEvent}）</li>
 *   <li>エンティティ爆発（{@link EntityExplodeEvent}）</li>
 *   <li>ブロック爆発（{@link BlockExplodeEvent}）</li>
 *   <li>エンティティによるブロック変更（{@link EntityChangeBlockEvent}）— 破壊のみ、落下砂除外</li>
 * </ul>
 */
public class ArenaTerrainListener implements Listener {

    private final TerrainManager terrainManager;

    public ArenaTerrainListener(TerrainManager terrainManager) {
        this.terrainManager = terrainManager;
    }

    /**
     * プレイヤーによるブロック破壊を記録する。
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent e) {
        terrainManager.recordBreak(
                e.getBlock().getLocation(), e.getBlock().getBlockData());
    }

    /**
     * エンティティ爆発によるブロック破壊を記録する。
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent e) {
        for (Block block : e.blockList()) {
            terrainManager.recordBreak(
                    block.getLocation(), block.getBlockData());
        }
    }

    /**
     * ブロック爆発（TNTチェーンなど）によるブロック破壊を記録する。
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent e) {
        for (Block block : e.blockList()) {
            terrainManager.recordBreak(
                    block.getLocation(), block.getBlockData());
        }
    }

    /**
     * エンティティによるブロック変更を記録する。
     *
     * <p>破壊（{@code getTo() == AIR}）のみ記録し、
     * {@link FallingBlock} は除外する（無限ループ防止）。
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityChangeBlock(EntityChangeBlockEvent e) {
        if (e.getTo() != Material.AIR) return;
        if (e.getEntity() instanceof FallingBlock) return;
        terrainManager.recordBreak(
                e.getBlock().getLocation(), e.getBlock().getBlockData());
    }
}
