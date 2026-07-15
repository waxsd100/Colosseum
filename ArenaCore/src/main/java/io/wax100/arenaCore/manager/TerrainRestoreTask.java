package io.wax100.arenaCore.manager;

import io.wax100.arenaCore.storage.BlockRestoreEntry;
import io.wax100.arenaCore.storage.TerrainStorageProvider;
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
            manager.restoreBlock(entry, effects);
        }

        if (terrainStorage.isEmpty(sessionId)) {
            cancel();
            manager.onFlushComplete();
        }
    }
}
