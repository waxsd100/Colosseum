package io.wax100.arenaCore.manager;

import org.bukkit.Chunk;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Deque;

/**
 * Stage 2: 試合後の高速復元タスク。
 *
 * <p>1tick あたり指定数のブロックをキューから取り出して復元する。
 * キューが空になったら自身をキャンセルし、
 * {@link TerrainManager#onFlushComplete()} を呼んで Stage 3 に遷移する。
 */
public class TerrainRestoreTask extends BukkitRunnable {

    private final Deque<TerrainManager.RestoreEntry> queue;
    private final TerrainManager manager;
    private final int blocksPerTick;
    private final boolean effects;

    /**
     * TerrainRestoreTask を生成する。
     *
     * @param queue         復元キュー（TerrainManager と共有）
     * @param manager       親マネージャ
     * @param blocksPerTick 1tick あたりの復元ブロック数
     * @param effects       エフェクトを再生するか
     */
    public TerrainRestoreTask(Deque<TerrainManager.RestoreEntry> queue,
                              TerrainManager manager,
                              int blocksPerTick, boolean effects) {
        this.queue = queue;
        this.manager = manager;
        this.blocksPerTick = blocksPerTick;
        this.effects = effects;
    }

    @Override
    public void run() {
        int count = 0;
        while (!queue.isEmpty() && count < blocksPerTick) {
            TerrainManager.RestoreEntry entry = queue.poll();

            // チャンクロード確認
            Chunk chunk = entry.location.getChunk();
            if (!chunk.isLoaded()) chunk.load();

            Block block = entry.location.getBlock();
            if (!block.getBlockData().equals(entry.originalData)) {
                block.setBlockData(entry.originalData, false);
                if (effects) {
                    manager.playEffect(entry.location, entry.originalData);
                }
            }
            count++;
        }

        if (queue.isEmpty()) {
            cancel();
            manager.onFlushComplete();
        }
    }
}
