package io.wax100.arenaCore.storage;

import io.wax100.chipLib.storage.RedisConnectionManager;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.exceptions.JedisException;
import redis.clients.jedis.resps.Tuple;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Redis Sorted Set による地形復元ストレージ。
 *
 * <p>{@link TerrainStorageProvider} の Redis 実装。
 * 各セッションの復元エントリを Redis Sorted Set に格納し、
 * {@code restoreAtTick} を score として昇順管理する。
 *
 * <h2>Redis キー設計</h2>
 * <ul>
 *   <li>エントリ: {@code {prefix}:terrain:{sessionId}} — Sorted Set</li>
 *   <li>アクティブセッション: {@code {prefix}:terrain:active} — Set</li>
 * </ul>
 *
 * <h2>エラーハンドリング</h2>
 * <p>全ての Redis 操作は {@link JedisException} を捕捉し、
 * 警告ログを出力して処理を継続する。Schematic ペーストが最終フォールバックとして機能する。
 *
 * <h2>score と Long.MAX_VALUE</h2>
 * <p>設置ブロック（{@code restoreAtTick == Long.MAX_VALUE}）は
 * {@link Double#MAX_VALUE} を score として使用する。
 */
public class RedisTerrainStorage implements TerrainStorageProvider {

    private static final Logger LOGGER = Logger.getLogger(RedisTerrainStorage.class.getName());

    private final RedisConnectionManager redisManager;
    private final String prefix;

    /**
     * RedisTerrainStorage を生成する。
     *
     * @param redisManager ChipLib の {@link RedisConnectionManager}
     * @param prefix       Redis キーのプレフィックス（例: {@code "colosseum"}）
     */
    public RedisTerrainStorage(RedisConnectionManager redisManager, String prefix) {
        this.redisManager = redisManager;
        this.prefix = prefix;
    }

    // ── キー生成 ──

    private String entryKey(String sessionId) {
        return prefix + ":terrain:" + sessionId;
    }

    private String activeSetKey() {
        return prefix + ":terrain:active";
    }

    /**
     * restoreAtTick を Redis score に変換する。
     *
     * <p>{@code Long.MAX_VALUE} は {@code Double.MAX_VALUE} に変換する。
     */
    private static double toScore(long restoreAtTick) {
        return restoreAtTick == Long.MAX_VALUE ? Double.MAX_VALUE : (double) restoreAtTick;
    }

    /**
     * Redis score を restoreAtTick に変換する。
     *
     * <p>{@code Double.MAX_VALUE} は {@code Long.MAX_VALUE} に変換する。
     */
    private static long fromScore(double score) {
        return score >= Double.MAX_VALUE ? Long.MAX_VALUE : (long) score;
    }

    // ── TerrainStorageProvider 実装 ──

    /**
     * {@inheritDoc}
     *
     * <p>Redis: ZADD でエントリを Sorted Set に追加し、SADD でアクティブセッションに登録する。
     */
    @Override
    public void recordBlockChange(String sessionId, String worldName,
                                  int x, int y, int z,
                                  String blockData, long restoreAtTick) {
        BlockRestoreEntry entry = new BlockRestoreEntry(worldName, x, y, z, blockData, restoreAtTick);
        String member = entry.serialize();
        double score = toScore(restoreAtTick);

        try (Jedis jedis = redisManager.getResource()) {
            jedis.zadd(entryKey(sessionId), score, member);
            jedis.sadd(activeSetKey(), sessionId);
        } catch (JedisException e) {
            LOGGER.log(Level.WARNING, "Redis ZADD 失敗 (session=" + sessionId + "): " + e.getMessage(), e);
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>Redis: ZRANGEBYSCORE で対象エントリを取得し、ZREMRANGEBYSCORE で削除する。
     */
    @Override
    public List<BlockRestoreEntry> pollReadyEntries(String sessionId, long currentTick) {
        String key = entryKey(sessionId);
        try (Jedis jedis = redisManager.getResource()) {
            // score <= currentTick のエントリを取得（score 付き）
            List<Tuple> tuples = jedis.zrangeByScoreWithScores(key, 0, (double) currentTick);
            if (tuples == null || tuples.isEmpty()) {
                return List.of();
            }

            // 取得したエントリをデシリアライズ
            List<BlockRestoreEntry> result = new ArrayList<>(tuples.size());
            for (Tuple tuple : tuples) {
                long tick = fromScore(tuple.getScore());
                result.add(BlockRestoreEntry.deserialize(tuple.getElement(), tick));
            }

            // 取得した範囲を削除
            jedis.zremrangeByScore(key, 0, (double) currentTick);

            return result;
        } catch (JedisException e) {
            LOGGER.log(Level.WARNING, "Redis ZRANGEBYSCORE 失敗 (session=" + sessionId + "): " + e.getMessage(), e);
            return List.of();
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>Redis: ZPOPMIN で先頭から count 件を取り出す。
     */
    @Override
    public List<BlockRestoreEntry> pollBatch(String sessionId, int count) {
        String key = entryKey(sessionId);
        try (Jedis jedis = redisManager.getResource()) {
            List<Tuple> tuples = jedis.zpopmin(key, count);
            if (tuples == null || tuples.isEmpty()) {
                return List.of();
            }

            List<BlockRestoreEntry> result = new ArrayList<>(tuples.size());
            for (Tuple tuple : tuples) {
                long tick = fromScore(tuple.getScore());
                result.add(BlockRestoreEntry.deserialize(tuple.getElement(), tick));
            }
            return result;
        } catch (JedisException e) {
            LOGGER.log(Level.WARNING, "Redis ZPOPMIN 失敗 (session=" + sessionId + "): " + e.getMessage(), e);
            return List.of();
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>Redis: ZCARD == 0 で判定する。
     */
    @Override
    public boolean isEmpty(String sessionId) {
        try (Jedis jedis = redisManager.getResource()) {
            return jedis.zcard(entryKey(sessionId)) == 0;
        } catch (JedisException e) {
            LOGGER.log(Level.WARNING, "Redis ZCARD 失敗 (session=" + sessionId + "): " + e.getMessage(), e);
            return true; // 安全側に倒す
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>Redis: DEL で Sorted Set を削除し、SREM でアクティブセッションから除去する。
     */
    @Override
    public void clearSession(String sessionId) {
        try (Jedis jedis = redisManager.getResource()) {
            jedis.del(entryKey(sessionId));
            jedis.srem(activeSetKey(), sessionId);
        } catch (JedisException e) {
            LOGGER.log(Level.WARNING, "Redis DEL/SREM 失敗 (session=" + sessionId + "): " + e.getMessage(), e);
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>Redis: SMEMBERS でアクティブセッション一覧を返す。
     */
    @Override
    public Set<String> getPendingSessions() {
        try (Jedis jedis = redisManager.getResource()) {
            Set<String> members = jedis.smembers(activeSetKey());
            return members != null ? members : Collections.emptySet();
        } catch (JedisException e) {
            LOGGER.log(Level.WARNING, "Redis SMEMBERS 失敗: " + e.getMessage(), e);
            return Collections.emptySet();
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>no-op: Redis 接続は ChipLib の {@link RedisConnectionManager} が管理する。
     */
    @Override
    public void shutdown() {
        // no-op — 接続は ChipLib が管理
    }
}
