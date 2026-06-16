package io.wax100.chipLib.storage;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.exceptions.JedisException;
import redis.clients.jedis.resps.Tuple;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Redis バックエンドによる Write-Through {@link StorageProvider} 実装。
 *
 * <p>書き込み時は Redis と YAML の両方に反映し、読み取り時は Redis を優先して
 * フォールバックとして YAML から読み取る。Redis 接続障害時はフォールバックモードに
 * 自動遷移し、再接続を試行する。
 *
 * <h2>Redis キーパターン</h2>
 * <ul>
 *   <li>ランキング: Sorted Set {@code {prefix}:ranking:{category}}</li>
 *   <li>プレイヤー統計: Hash {@code {prefix}:stats:{uuid}}</li>
 *   <li>セッション購入: Hash {@code {prefix}:session:purchases}</li>
 *   <li>オフライン精算: Hash {@code {prefix}:offline:{uuid}}</li>
 * </ul>
 *
 * @author wax100
 * @see StorageProvider
 * @see YamlStorageProvider
 * @see RedisConnectionManager
 */
public class RedisBackedStorageProvider implements StorageProvider {

    /**
     * YAML バックエンドプロバイダー（常に書き込み先として使用）
     */
    private final YamlStorageProvider yamlProvider;

    /**
     * Redis 接続マネージャ
     */
    private final RedisConnectionManager redis;

    /**
     * Redis キーのプレフィックス
     */
    private final String prefix;

    /**
     * ロガー
     */
    private final Logger logger;

    /**
     * フォールバックモードフラグ。
     * Redis 接続障害時に {@code true} になり、YAML のみで動作する。
     */
    private volatile boolean fallbackMode = false;

    /**
     * コンストラクタ。
     *
     * @param yamlProvider YAML バックエンドプロバイダー
     * @param redis        Redis 接続マネージャ
     * @param prefix       Redis キーのプレフィックス
     * @param logger       ロガーインスタンス
     * @throws NullPointerException いずれかの引数が {@code null} の場合
     */
    public RedisBackedStorageProvider(@NotNull YamlStorageProvider yamlProvider,
                                     @NotNull RedisConnectionManager redis,
                                     @NotNull String prefix,
                                     @NotNull Logger logger) {
        this.yamlProvider = Objects.requireNonNull(yamlProvider, "yamlProvider");
        this.redis = Objects.requireNonNull(redis, "redis");
        this.prefix = Objects.requireNonNull(prefix, "prefix");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    // ── ランキング ──

    @Override
    public void updateRanking(@NotNull String category, @NotNull UUID playerId, long netResult) {
        yamlProvider.updateRanking(category, playerId, netResult);

        if (fallbackMode) return;
        try (Jedis jedis = redis.getResource()) {
            jedis.zincrby(rankingKey(category), netResult, playerId.toString());
        } catch (JedisException e) {
            handleRedisFailure("updateRanking", e);
        }
    }

    @Override
    @NotNull
    public Map<UUID, Long> getRankingData(@NotNull String category) {
        if (!fallbackMode) {
            try (Jedis jedis = redis.getResource()) {
                List<Tuple> tuples = jedis.zrangeWithScores(rankingKey(category), 0, -1);
                if (tuples != null && !tuples.isEmpty()) {
                    Map<UUID, Long> result = new LinkedHashMap<>();
                    for (Tuple tuple : tuples) {
                        try {
                            result.put(UUID.fromString(tuple.getElement()), (long) tuple.getScore());
                        } catch (IllegalArgumentException ignored) {
                            // 無効な UUID エントリをスキップ
                        }
                    }
                    return Collections.unmodifiableMap(result);
                }
            } catch (JedisException e) {
                handleRedisFailure("getRankingData", e);
            }
        }
        return yamlProvider.getRankingData(category);
    }

    @Override
    @NotNull
    public List<Map.Entry<UUID, Long>> getSortedRanking(@NotNull String category, int limit) {
        if (!fallbackMode) {
            try (Jedis jedis = redis.getResource()) {
                List<Tuple> tuples = jedis.zrevrangeWithScores(rankingKey(category), 0, limit - 1L);
                if (tuples != null && !tuples.isEmpty()) {
                    List<Map.Entry<UUID, Long>> result = new ArrayList<>();
                    for (Tuple tuple : tuples) {
                        try {
                            result.add(new AbstractMap.SimpleImmutableEntry<>(
                                    UUID.fromString(tuple.getElement()), (long) tuple.getScore()));
                        } catch (IllegalArgumentException ignored) {
                            // 無効な UUID エントリをスキップ
                        }
                    }
                    return result;
                }
            } catch (JedisException e) {
                handleRedisFailure("getSortedRanking", e);
            }
        }
        return yamlProvider.getSortedRanking(category, limit);
    }

    @Override
    @NotNull
    public List<Map.Entry<UUID, Long>> getTotalRanking(int limit) {
        // 総合ランキングは複数の Sorted Set の合算が必要なため、YAML に委譲
        return yamlProvider.getTotalRanking(limit);
    }

    @Override
    public void resetRanking(@NotNull String category) {
        yamlProvider.resetRanking(category);

        if (fallbackMode) return;
        try (Jedis jedis = redis.getResource()) {
            jedis.del(rankingKey(category));
        } catch (JedisException e) {
            handleRedisFailure("resetRanking", e);
        }
    }

    @Override
    public void resetAllRankings() {
        yamlProvider.resetAllRankings();

        if (fallbackMode) return;
        try (Jedis jedis = redis.getResource()) {
            // パターンマッチでランキングキーを削除
            @SuppressWarnings("unchecked")
            var keys = jedis.keys(prefix + ":ranking:*");
            if (keys != null && !keys.isEmpty()) {
                jedis.del(keys.toArray(new String[0]));
            }
        } catch (JedisException e) {
            handleRedisFailure("resetAllRankings", e);
        }
    }

    // ── プレイヤー統計 ──

    @Override
    @Nullable
    public PlayerStatsSnapshot loadPlayerStats(@NotNull UUID playerId) {
        if (!fallbackMode) {
            try (Jedis jedis = redis.getResource()) {
                Map<String, String> data = jedis.hgetAll(statsKey(playerId));
                if (data != null && !data.isEmpty()) {
                    return deserializeStats(data);
                }
            } catch (JedisException e) {
                handleRedisFailure("loadPlayerStats", e);
            }
        }
        return yamlProvider.loadPlayerStats(playerId);
    }

    @Override
    public void savePlayerStats(@NotNull UUID playerId, @NotNull PlayerStatsSnapshot snapshot) {
        yamlProvider.savePlayerStats(playerId, snapshot);

        if (fallbackMode) return;
        try (Jedis jedis = redis.getResource()) {
            jedis.hset(statsKey(playerId), serializeStats(snapshot));
        } catch (JedisException e) {
            handleRedisFailure("savePlayerStats", e);
        }
    }

    @Override
    @NotNull
    public Map<UUID, PlayerStatsSnapshot> loadAllPlayerStats() {
        // 全プレイヤー統計はスキャンが必要なため YAML に委譲
        return yamlProvider.loadAllPlayerStats();
    }

    // ── セッション購入 ──

    @Override
    public void addPurchase(@NotNull UUID playerId, long amount) {
        yamlProvider.addPurchase(playerId, amount);

        if (fallbackMode) return;
        try (Jedis jedis = redis.getResource()) {
            jedis.hincrBy(purchasesKey(), playerId.toString(), amount);
        } catch (JedisException e) {
            handleRedisFailure("addPurchase", e);
        }
    }

    @Override
    public long getPurchase(@NotNull UUID playerId) {
        if (!fallbackMode) {
            try (Jedis jedis = redis.getResource()) {
                String value = jedis.hget(purchasesKey(), playerId.toString());
                if (value != null) {
                    return Long.parseLong(value);
                }
            } catch (JedisException e) {
                handleRedisFailure("getPurchase", e);
            } catch (NumberFormatException e) {
                logger.warning("Redis の購入データが数値ではありません: player=" + playerId);
            }
        }
        return yamlProvider.getPurchase(playerId);
    }

    @Override
    @NotNull
    public Map<UUID, Long> getAllPurchases() {
        if (!fallbackMode) {
            try (Jedis jedis = redis.getResource()) {
                Map<String, String> data = jedis.hgetAll(purchasesKey());
                if (data != null && !data.isEmpty()) {
                    Map<UUID, Long> result = new HashMap<>();
                    for (Map.Entry<String, String> entry : data.entrySet()) {
                        try {
                            result.put(UUID.fromString(entry.getKey()), Long.parseLong(entry.getValue()));
                        } catch (IllegalArgumentException ignored) {
                            // 無効なエントリをスキップ
                        }
                    }
                    return Collections.unmodifiableMap(result);
                }
            } catch (JedisException e) {
                handleRedisFailure("getAllPurchases", e);
            }
        }
        return yamlProvider.getAllPurchases();
    }

    @Override
    public void clearPurchases() {
        yamlProvider.clearPurchases();

        if (fallbackMode) return;
        try (Jedis jedis = redis.getResource()) {
            jedis.del(purchasesKey());
        } catch (JedisException e) {
            handleRedisFailure("clearPurchases", e);
        }
    }

    // ── オフライン精算 ──

    @Override
    public void addOfflineResult(@NotNull UUID playerId, long bet, long won) {
        yamlProvider.addOfflineResult(playerId, bet, won);

        if (fallbackMode) return;
        try (Jedis jedis = redis.getResource()) {
            String key = offlineKey(playerId);
            jedis.hincrBy(key, "bet", bet);
            jedis.hincrBy(key, "won", won);
        } catch (JedisException e) {
            handleRedisFailure("addOfflineResult", e);
        }
    }

    @Override
    @Nullable
    public long[] getAndClearOfflineResult(@NotNull UUID playerId) {
        long[] result = null;

        if (!fallbackMode) {
            try (Jedis jedis = redis.getResource()) {
                String key = offlineKey(playerId);
                Map<String, String> data = jedis.hgetAll(key);
                if (data != null && !data.isEmpty()) {
                    long bet = Long.parseLong(data.getOrDefault("bet", "0"));
                    long won = Long.parseLong(data.getOrDefault("won", "0"));
                    jedis.del(key);
                    result = new long[]{bet, won};
                }
            } catch (JedisException e) {
                handleRedisFailure("getAndClearOfflineResult", e);
            } catch (NumberFormatException e) {
                logger.warning("Redis のオフライン精算データが数値ではありません: player=" + playerId);
            }
        }

        // YAML 側もクリア（Write-Through の一貫性維持）
        long[] yamlResult = yamlProvider.getAndClearOfflineResult(playerId);

        // Redis から取得できた場合はそちらを優先
        return result != null ? result : yamlResult;
    }

    // ── ライフサイクル ──

    @Override
    public void flush() {
        yamlProvider.flush();
    }

    @Override
    public void shutdown() {
        yamlProvider.shutdown();
        redis.shutdown();
    }

    /**
     * フォールバックモードかどうかを返す。
     *
     * @return フォールバックモードの場合 {@code true}
     */
    public boolean isFallbackMode() {
        return fallbackMode;
    }

    /**
     * フォールバックモードからの復旧を試行する。
     *
     * <p>Redis への接続が回復した場合、フォールバックモードを解除する。
     * 外部から定期的に呼び出されることを想定する。
     *
     * @return 復旧に成功した場合 {@code true}
     */
    public boolean tryRecover() {
        if (!fallbackMode) return true;
        if (redis.isAvailable()) {
            fallbackMode = false;
            logger.info("Redis 接続が回復しました。フォールバックモードを解除します。");
            return true;
        }
        return false;
    }

    // ── Redis キー生成 ──

    private String rankingKey(String category) {
        return prefix + ":ranking:" + category;
    }

    private String statsKey(UUID playerId) {
        return prefix + ":stats:" + playerId.toString();
    }

    private String purchasesKey() {
        return prefix + ":session:purchases";
    }

    private String offlineKey(UUID playerId) {
        return prefix + ":offline:" + playerId.toString();
    }

    // ── 統計シリアライズ ──

    /**
     * {@link PlayerStatsSnapshot} を Redis Hash 用の Map に変換する。
     */
    private Map<String, String> serializeStats(PlayerStatsSnapshot s) {
        Map<String, String> map = new HashMap<>();
        map.put("name", s.name() != null ? s.name() : "");
        map.put("totalSessions", String.valueOf(s.totalSessions()));
        map.put("totalPurchases", String.valueOf(s.totalPurchases()));
        map.put("totalCashouts", String.valueOf(s.totalCashouts()));
        map.put("netProfit", String.valueOf(s.netProfit()));
        map.put("wins", String.valueOf(s.wins()));
        map.put("losses", String.valueOf(s.losses()));
        map.put("draws", String.valueOf(s.draws()));
        map.put("biggestWin", String.valueOf(s.biggestWin()));
        map.put("biggestLoss", String.valueOf(s.biggestLoss()));
        if (s.firstPlayed() != null) map.put("firstPlayed", s.firstPlayed());
        if (s.lastPlayed() != null) map.put("lastPlayed", s.lastPlayed());
        return map;
    }

    /**
     * Redis Hash データを {@link PlayerStatsSnapshot} にデシリアライズする。
     */
    private PlayerStatsSnapshot deserializeStats(Map<String, String> data) {
        return new PlayerStatsSnapshot(
                data.getOrDefault("name", ""),
                parseInt(data.get("totalSessions")),
                parseLong(data.get("totalPurchases")),
                parseLong(data.get("totalCashouts")),
                parseLong(data.get("netProfit")),
                parseInt(data.get("wins")),
                parseInt(data.get("losses")),
                parseInt(data.get("draws")),
                parseLong(data.get("biggestWin")),
                parseLong(data.get("biggestLoss")),
                data.get("firstPlayed"),
                data.get("lastPlayed")
        );
    }

    private static int parseInt(@Nullable String value) {
        if (value == null) return 0;
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static long parseLong(@Nullable String value) {
        if (value == null) return 0L;
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    // ── エラーハンドリング ──

    /**
     * Redis 接続障害時の共通処理。
     *
     * <p>警告をログに記録し、フォールバックモードに移行する。
     *
     * @param operation 失敗した操作名
     * @param e         発生した例外
     */
    private void handleRedisFailure(String operation, JedisException e) {
        if (!fallbackMode) {
            fallbackMode = true;
            logger.log(Level.WARNING,
                    "Redis 接続障害を検出しました（操作: " + operation + "）。"
                            + "フォールバックモード（YAML のみ）に移行します。", e);
        }
    }
}
