package io.wax100.arenaCore.listener;

import io.wax100.arenaCore.manager.TerrainManager;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.FallingBlock;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;

/**
 * 地形変更イベントリスナー。
 *
 * <p>戦闘エリア内のブロック破壊・設置を監視し、
 * {@link TerrainManager} に復元対象として記録する。
 *
 * <p>監視対象:
 * <ul>
 *   <li>プレイヤー手動破壊（{@link BlockBreakEvent}）</li>
 *   <li>プレイヤーブロック設置（{@link BlockPlaceEvent}）</li>
 *   <li>エンティティ爆発（{@link EntityExplodeEvent}）</li>
 *   <li>ブロック爆発（{@link BlockExplodeEvent}）</li>
 *   <li>エンティティによるブロック変更（{@link EntityChangeBlockEvent}）— 破壊・設置両方</li>
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
     * プレイヤーによるブロック設置を記録する。
     *
     * <p>設置前の状態（通常はAIR）を記録し、復元時にはAIRに戻す。
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent e) {
        terrainManager.recordPlace(
                e.getBlock().getLocation(),
                e.getBlockReplacedState().getBlockData());
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
     * <p>破壊（{@code getTo() == AIR}）は recordBreak、
     * 設置（{@code getTo() != AIR}）は recordPlace で記録する。
     * {@link FallingBlock} は除外（無限ループ防止）。
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityChangeBlock(EntityChangeBlockEvent e) {
        if (e.getEntity() instanceof FallingBlock) return;

        Block block = e.getBlock();
        if (e.getTo() == Material.AIR) {
            // 破壊: 元のブロックデータを記録
            terrainManager.recordBreak(block.getLocation(), block.getBlockData());
        } else {
            // 設置: 元の状態（設置前）を記録
            terrainManager.recordPlace(block.getLocation(), block.getBlockData());
        }
    }
}
