package io.wax100.chipLib.storage;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.exceptions.JedisException;

import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Redis 接続プールの管理クラス。
 *
 * <p>{@link JedisPool} のライフサイクルを管理し、接続の取得・ヘルスチェック・
 * シャットダウンを提供する。接続障害時はグレースフルに処理する。
 *
 * @author wax100
 * @see RedisBackedStorageProvider
 */
public class RedisConnectionManager {

    /**
     * Jedis 接続プール
     */
    private final JedisPool pool;

    /**
     * ロガー
     */
    private final Logger logger;

    /**
     * コンストラクタ。
     *
     * <p>指定されたパラメータで {@link JedisPool} を構築する。
     *
     * @param host     Redis ホスト名
     * @param port     Redis ポート番号
     * @param password Redis パスワード（空文字列の場合はパスワードなし）
     * @param database Redis データベース番号
     * @param maxTotal プールの最大接続数
     * @param maxIdle  プールの最大アイドル接続数
     * @param logger   ロガーインスタンス
     * @throws NullPointerException {@code host} または {@code logger} が {@code null} の場合
     */
    public RedisConnectionManager(String host, int port, String password, int database,
                                  int maxTotal, int maxIdle, Logger logger) {
        Objects.requireNonNull(host, "host");
        this.logger = Objects.requireNonNull(logger, "logger");

        JedisPoolConfig poolConfig = new JedisPoolConfig();
        poolConfig.setMaxTotal(maxTotal);
        poolConfig.setMaxIdle(maxIdle);
        poolConfig.setTestOnBorrow(true);
        poolConfig.setTestOnReturn(true);

        if (password != null && !password.isEmpty()) {
            this.pool = new JedisPool(poolConfig, host, port, 2000, password, database);
        } else {
            this.pool = new JedisPool(poolConfig, host, port, 2000, null, database);
        }
    }

    /**
     * プールから Jedis 接続を取得する。
     *
     * <p>取得した {@link Jedis} インスタンスは try-with-resources で使用すること。
     *
     * @return Jedis インスタンス
     * @throws JedisException 接続取得に失敗した場合
     */
    public Jedis getResource() {
        return pool.getResource();
    }

    /**
     * Redis サーバーへの接続が利用可能かヘルスチェックを行う。
     *
     * @return 接続可能な場合 {@code true}
     */
    public boolean isAvailable() {
        try (Jedis jedis = pool.getResource()) {
            return "PONG".equals(jedis.ping());
        } catch (JedisException e) {
            logger.log(Level.WARNING, "Redis ヘルスチェックに失敗しました", e);
            return false;
        }
    }

    /**
     * 接続プールをシャットダウンする。
     *
     * <p>プール内のすべての接続を閉じ、リソースを解放する。
     */
    public void shutdown() {
        if (pool != null && !pool.isClosed()) {
            try {
                pool.close();
                logger.info("Redis 接続プールをシャットダウンしました。");
            } catch (Exception e) {
                logger.log(Level.WARNING, "Redis 接続プールのシャットダウン中にエラーが発生しました", e);
            }
        }
    }
}
