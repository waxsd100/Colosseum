package io.wax100.arenaCore.storage;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * インメモリ地形復元ストレージ。
 *
 * <p>{@link TerrainStorageProvider} のデフォルト実装。
 * 既存の {@code Deque<RestoreEntry>} ロジックをセッション単位の
 * {@code Map<String, Deque<BlockRestoreEntry>>} に移植したもの。
 *
 * <p>サーバークラッシュ時にはデータが失われるが、
 * Schematic ペーストによる復旧が最終フォールバックとして機能する。
 */
public class MemoryTerrainStorage implements TerrainStorageProvider {

    private final Map<String, Deque<BlockRestoreEntry>> sessions = new ConcurrentHashMap<>();

    /**
     * {@inheritDoc}
     */
    @Override
    public void recordBlockChange(String sessionId, String worldName,
                                  int x, int y, int z,
                                  String blockData, long restoreAtTick) {
        sessions.computeIfAbsent(sessionId, k -> new ArrayDeque<>())
                .add(new BlockRestoreEntry(worldName, x, y, z, blockData, restoreAtTick));
    }

    /**
     * {@inheritDoc}
     *
     * <p>キュー先頭から {@code restoreAtTick <= currentTick} のエントリを全て取り出す。
     */
    @Override
    public List<BlockRestoreEntry> pollReadyEntries(String sessionId, long currentTick) {
        Deque<BlockRestoreEntry> queue = sessions.get(sessionId);
        if (queue == null || queue.isEmpty()) {
            return List.of();
        }

        List<BlockRestoreEntry> ready = new ArrayList<>();
        while (!queue.isEmpty()) {
            BlockRestoreEntry entry = queue.peek();
            if (entry.restoreAtTick() > currentTick) break;
            queue.poll();
            ready.add(entry);
        }
        return ready;
    }

    /**
     * {@inheritDoc}
     *
     * <p>キュー先頭から最大 count 件を取り出す。
     */
    @Override
    public List<BlockRestoreEntry> pollBatch(String sessionId, int count) {
        Deque<BlockRestoreEntry> queue = sessions.get(sessionId);
        if (queue == null || queue.isEmpty()) {
            return List.of();
        }

        List<BlockRestoreEntry> batch = new ArrayList<>(count);
        for (int i = 0; i < count && !queue.isEmpty(); i++) {
            batch.add(queue.poll());
        }
        return batch;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isEmpty(String sessionId) {
        Deque<BlockRestoreEntry> queue = sessions.get(sessionId);
        return queue == null || queue.isEmpty();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void clearSession(String sessionId) {
        sessions.remove(sessionId);
    }

    /**
     * {@inheritDoc}
     *
     * <p>インメモリ実装のため、クラッシュ復旧には使用できない。
     * 同一プロセス内のアクティブセッションを返す。
     */
    @Override
    public Set<String> getPendingSessions() {
        return new HashSet<>(sessions.keySet());
    }

    /**
     * {@inheritDoc}
     *
     * <p>no-op: インメモリ実装のためシャットダウン処理は不要。
     */
    @Override
    public void shutdown() {
        // no-op
    }

    /**
     * 設置ブロック（{@link Long#MAX_VALUE}）をキュー先頭に並べ替える。
     *
     * <p>Stage 2（高速復元）開始前に呼び出し、設置ブロックを先に除去する。
     *
     * @param sessionId セッション識別子
     */
    public void reorderPlacedBlocksFirst(String sessionId) {
        Deque<BlockRestoreEntry> queue = sessions.get(sessionId);
        if (queue == null || queue.isEmpty()) return;

        Deque<BlockRestoreEntry> placed = new ArrayDeque<>();
        Deque<BlockRestoreEntry> broken = new ArrayDeque<>();
        for (BlockRestoreEntry entry : queue) {
            if (entry.restoreAtTick() == Long.MAX_VALUE) {
                placed.add(entry);
            } else {
                broken.add(entry);
            }
        }
        queue.clear();
        queue.addAll(placed);
        queue.addAll(broken);
    }
}
