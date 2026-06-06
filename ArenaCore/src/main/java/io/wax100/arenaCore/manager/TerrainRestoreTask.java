package io.wax100.arenaCore.manager;

import io.wax100.arenaCore.storage.BlockRestoreEntry;
import io.wax100.arenaCore.storage.TerrainStorageProvider;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.List;

/**
 * Stage 2: 試合後の高速復元タスク。
 *
 * <p>1tick あたり指定数のブロックをストレージから取り出して復元する。
 * ストレージが空になったら自身をキャンセルし、
 * {@link TerrainManager#onFlushComplete()} を呼んで Stage 3 に遷移する。
 */
public class TerrainRestoreTask extends BukkitRunnable {

    private final TerrainStorageProvider terrainStorage;
    private final TerrainManager manager;
    private final String sessionId;
    private final int blocksPerTick;
    private final boolean effects;

    /**
     * TerrainRestoreTask を生成する。
     *
     * @param terrainStorage 地形復元ストレージプロバイダ
     * @param manager        親マネージャ
     * @param sessionId      セッション識別子
     * @param blocksPerTick  1tick あたりの復元ブロック数
     * @param effects        エフェクトを再生するか
     */
    public TerrainRestoreTask(TerrainStorageProvider terrainStorage,
                              TerrainManager manager,
                              String sessionId,
                              int blocksPerTick, boolean effects) {
        this.terrainStorage = terrainStorage;
        this.manager = manager;
        this.sessionId = sessionId;
        this.blocksPerTick = blocksPerTick;
        this.effects = effects;
    }

    @Override
    public void run() {
        List<BlockRestoreEntry> batch = terrainStorage.pollBatch(sessionId, blocksPerTick);

        for (BlockRestoreEntry entry : batch) {
            World world = Bukkit.getWorld(entry.worldName());
            if (world == null) continue;

            Location loc = new Location(world, entry.x(), entry.y(), entry.z());

            // チャンクロード確認
            Chunk chunk = loc.getChunk();
            if (!chunk.isLoaded()) chunk.load();

            BlockData originalData = Bukkit.createBlockData(entry.blockDataString());
            Block block = loc.getBlock();
            if (!block.getBlockData().equals(originalData)) {
                block.setBlockData(originalData, false);
                if (effects) {
                    manager.playEffect(loc, originalData);
                }
            }
        }

        if (terrainStorage.isEmpty(sessionId)) {
            cancel();
            manager.onFlushComplete();
        }
    }
}
