package io.wax100.chipLib.storage;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * ストレージプロバイダーのファクトリクラス。
 *
 * <p>プラグインの設定ファイル（{@code config.yml}）に基づいて、適切な
 * {@link StorageProvider} 実装を生成する。
 *
 * <h2>設定例</h2>
 * <pre>
 * storage:
 *   type: redis          # redis (デフォルト) / yaml
 *   redis:
 *     host: localhost
 *     port: 6379
 *     password: ""
 *     database: 0
 *     pool:
 *       max-total: 8
 *       max-idle: 4
 *     prefix: "colosseum"
 * </pre>
 *
 * @author wax100
 * @see StorageProvider
 * @see YamlStorageProvider
 * @see RedisBackedStorageProvider
 */
public final class StorageFactory {

    /**
     * 最後に生成された {@link RedisConnectionManager} のインスタンス。
     * ArenaCore のテレインストレージなど外部モジュールから参照可能。
     */
    private static volatile RedisConnectionManager lastRedisConnectionManager;

    private StorageFactory() {
        // ユーティリティクラスのためインスタンス化を禁止
    }

    /**
     * プラグインの設定に基づいてストレージプロバイダーを生成する。
     *
     * <p>{@code storage.type} が {@code "redis"}（デフォルト）の場合は
     * {@link RedisBackedStorageProvider} を生成し、それ以外は
     * {@link YamlStorageProvider} を返す。
     *
     * <p>Redis 接続に失敗した場合はフォールバックとして
     * {@link YamlStorageProvider} を返す。
     *
     * @param plugin プラグインインスタンス
     * @return 生成されたストレージプロバイダー
     * @throws NullPointerException {@code plugin} が {@code null} の場合
     */
    @NotNull
    public static StorageProvider create(@NotNull JavaPlugin plugin) {
        Objects.requireNonNull(plugin, "plugin");

        String type = plugin.getConfig().getString("storage.type", "redis");

        if (!"redis".equalsIgnoreCase(type)) {
            plugin.getLogger().info("ストレージタイプ: YAML");
            return new YamlStorageProvider(plugin, plugin.getDataFolder());
        }

        return createRedisProvider(plugin);
    }

    /**
     * 最後に生成された {@link RedisConnectionManager} を取得する。
     *
     * <p>ストレージタイプが {@code "redis"} でない場合や、Redis 接続に失敗した場合は
     * {@code null} を返す。ArenaCore のテレインストレージなど、外部モジュールが
     * Redis 接続を共有するために使用する。
     *
     * @return {@link RedisConnectionManager} インスタンス、または {@code null}
     */
    @Nullable
    public static RedisConnectionManager getRedisConnectionManager() {
        return lastRedisConnectionManager;
    }

    /**
     * Redis バックエンドのストレージプロバイダーを生成する。
     *
     * @param plugin プラグインインスタンス
     * @return Redis バックエンドプロバイダー。接続失敗時は YAML プロバイダー
     */
    private static StorageProvider createRedisProvider(JavaPlugin plugin) {
        ConfigurationSection redisSection = plugin.getConfig().getConfigurationSection("storage.redis");

        String host = "localhost";
        int port = 6379;
        String password = "";
        int database = 0;
        int maxTotal = 8;
        int maxIdle = 4;
        String prefix = "colosseum";

        if (redisSection != null) {
            host = redisSection.getString("host", host);
            port = redisSection.getInt("port", port);
            password = redisSection.getString("password", password);
            database = redisSection.getInt("database", database);
            prefix = redisSection.getString("prefix", prefix);

            ConfigurationSection poolSection = redisSection.getConfigurationSection("pool");
            if (poolSection != null) {
                maxTotal = poolSection.getInt("max-total", maxTotal);
                maxIdle = poolSection.getInt("max-idle", maxIdle);
            }
        }

        // Redis 接続マネージャの生成
        RedisConnectionManager redisManager;
        try {
            redisManager = new RedisConnectionManager(host, port, password, database,
                    maxTotal, maxIdle, plugin.getLogger());
        } catch (Exception e) {
            plugin.getLogger().warning("Redis 接続プールの作成に失敗しました。YAML モードにフォールバックします: "
                    + e.getMessage());
            return new YamlStorageProvider(plugin, plugin.getDataFolder());
        }

        // ヘルスチェック
        if (!redisManager.isAvailable()) {
            plugin.getLogger().warning("Redis サーバーに接続できません (" + host + ":" + port
                    + ")。YAML モードにフォールバックします。");
            redisManager.shutdown();
            return new YamlStorageProvider(plugin, plugin.getDataFolder());
        }

        lastRedisConnectionManager = redisManager;

        YamlStorageProvider yamlProvider = new YamlStorageProvider(plugin, plugin.getDataFolder());
        plugin.getLogger().info("ストレージタイプ: Redis (Write-Through) — " + host + ":" + port
                + " [DB=" + database + ", prefix=" + prefix + "]");

        return new RedisBackedStorageProvider(yamlProvider, redisManager, prefix, plugin.getLogger());
    }
}
