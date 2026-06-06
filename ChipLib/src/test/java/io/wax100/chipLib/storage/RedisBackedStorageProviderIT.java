package io.wax100.chipLib.storage;

import com.github.fppt.jedismock.RedisServer;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.File;
import java.nio.file.Files;
import java.util.*;
import java.util.logging.Logger;

/**
 * {@link RedisBackedStorageProvider} の統合テスト。
 *
 * <p>jedis-mock による組み込み Redis サーバーを使用して、
 * Write-Through 動作・フォールバック・各種 CRUD 操作を検証する。
 */
@DisplayName("RedisBackedStorageProvider 統合テスト")
class RedisBackedStorageProviderIT {

    private static RedisServer redisServer;
    private RedisConnectionManager redisManager;
    private YamlStorageProvider yamlProvider;
    private RedisBackedStorageProvider provider;
    private File tempDir;

    @BeforeAll
    static void startRedis() throws Exception {
        redisServer = RedisServer.newRedisServer().start();
    }

    @AfterAll
    static void stopRedis() throws Exception {
        if (redisServer != null) {
            redisServer.stop();
        }
    }

    @BeforeEach
    void setUp() throws Exception {
        tempDir = Files.createTempDirectory("chiplib-redis-it").toFile();

        JavaPlugin mockPlugin = mock(JavaPlugin.class);
        when(mockPlugin.getLogger()).thenReturn(Logger.getLogger("test"));
        // isEnabled() returns false by default in mock — forces synchronous YAML saves

        yamlProvider = new YamlStorageProvider(mockPlugin, tempDir);
        redisManager = new RedisConnectionManager(
                redisServer.getHost(), redisServer.getBindPort(),
                "", 0, 4, 2, Logger.getLogger("test"));
        provider = new RedisBackedStorageProvider(
                yamlProvider, redisManager, "test", Logger.getLogger("test"));

        // Redis をフラッシュしてテスト間の干渉を防止
        flushRedis();
    }

    @AfterEach
    void tearDown() {
        if (provider != null) {
            provider.shutdown();
        }
        deleteRecursive(tempDir);
    }

    /**
     * Redis 内の全データを消去する。
     */
    private void flushRedis() {
        try (var jedis = new redis.clients.jedis.Jedis(redisServer.getHost(), redisServer.getBindPort())) {
            jedis.flushAll();
        }
    }

    /**
     * ディレクトリを再帰的に削除する。
     */
    private static void deleteRecursive(File file) {
        if (file == null || !file.exists()) return;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursive(child);
                }
            }
        }
        file.delete();
    }

    // ═══════════════════════════════════════════════════════════════
    // ランキング
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("ランキング")
    class RankingTests {

        @Test
        @DisplayName("updateRanking: 正の値でスコアが加算される")
        void updateRanking_正の値でスコアが加算される() {
            UUID player = UUID.randomUUID();
            String category = "casino";

            provider.updateRanking(category, player, 1000L);

            Map<UUID, Long> data = provider.getRankingData(category);
            assertAll("ランキングデータの確認",
                    () -> assertFalse(data.isEmpty(), "データが空でないこと"),
                    () -> assertTrue(data.containsKey(player), "プレイヤーが含まれること"),
                    () -> assertEquals(1000L, data.get(player), "スコアが1000であること")
            );
        }

        @Test
        @DisplayName("updateRanking: 負の値でスコアが減算される")
        void updateRanking_負の値でスコアが減算される() {
            UUID player = UUID.randomUUID();
            String category = "casino";

            provider.updateRanking(category, player, 1000L);
            provider.updateRanking(category, player, -300L);

            Map<UUID, Long> data = provider.getRankingData(category);
            assertAll("減算後のスコア確認",
                    () -> assertTrue(data.containsKey(player), "プレイヤーが含まれること"),
                    () -> assertEquals(700L, data.get(player), "スコアが700であること (1000 - 300)")
            );
        }

        @Test
        @DisplayName("getSortedRanking: 降順でリミット件数分返される")
        void getSortedRanking_降順でリミット件数分返される() {
            String category = "arena";
            UUID p1 = UUID.randomUUID();
            UUID p2 = UUID.randomUUID();
            UUID p3 = UUID.randomUUID();
            UUID p4 = UUID.randomUUID();
            UUID p5 = UUID.randomUUID();

            provider.updateRanking(category, p1, 100L);
            provider.updateRanking(category, p2, 500L);
            provider.updateRanking(category, p3, 300L);
            provider.updateRanking(category, p4, 200L);
            provider.updateRanking(category, p5, 400L);

            List<Map.Entry<UUID, Long>> top3 = provider.getSortedRanking(category, 3);

            assertAll("ソート済みランキングの確認",
                    () -> assertEquals(3, top3.size(), "上位3件であること"),
                    () -> assertEquals(p2, top3.get(0).getKey(), "1位が最高スコアのプレイヤーであること"),
                    () -> assertEquals(500L, top3.get(0).getValue(), "1位のスコアが500であること"),
                    () -> assertEquals(p5, top3.get(1).getKey(), "2位が2番目に高いプレイヤーであること"),
                    () -> assertEquals(400L, top3.get(1).getValue(), "2位のスコアが400であること"),
                    () -> assertEquals(p3, top3.get(2).getKey(), "3位が3番目に高いプレイヤーであること"),
                    () -> assertEquals(300L, top3.get(2).getValue(), "3位のスコアが300であること")
            );
        }

        @Test
        @DisplayName("resetRanking: カテゴリ単位でリセットされる")
        void resetRanking_カテゴリ単位でリセットされる() {
            UUID player = UUID.randomUUID();
            String cat1 = "casino";
            String cat2 = "arena";

            provider.updateRanking(cat1, player, 1000L);
            provider.updateRanking(cat2, player, 2000L);

            provider.resetRanking(cat1);

            assertAll("カテゴリリセットの確認",
                    () -> assertTrue(provider.getRankingData(cat1).isEmpty(),
                            "リセットしたカテゴリは空であること"),
                    () -> assertFalse(provider.getRankingData(cat2).isEmpty(),
                            "別カテゴリは影響を受けないこと"),
                    () -> assertEquals(2000L, provider.getRankingData(cat2).get(player),
                            "別カテゴリのスコアが維持されること")
            );
        }

        @Test
        @DisplayName("resetAllRankings: 全カテゴリがリセットされる")
        void resetAllRankings_全カテゴリがリセットされる() {
            UUID player = UUID.randomUUID();

            provider.updateRanking("casino", player, 1000L);
            provider.updateRanking("arena", player, 2000L);

            provider.resetAllRankings();

            assertAll("全カテゴリリセットの確認",
                    () -> assertTrue(provider.getRankingData("casino").isEmpty(),
                            "casino カテゴリが空であること"),
                    () -> assertTrue(provider.getRankingData("arena").isEmpty(),
                            "arena カテゴリが空であること")
            );
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // プレイヤー統計
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("プレイヤー統計")
    class PlayerStatsTests {

        @Test
        @DisplayName("saveAndLoad: 統計が正しくラウンドトリップする")
        void saveAndLoad_統計が正しくラウンドトリップする() {
            UUID playerId = UUID.randomUUID();
            PlayerStatsSnapshot original = new PlayerStatsSnapshot(
                    "TestPlayer", 42, 100000L, 85000L,
                    -15000L, 20, 18, 4,
                    50000L, -30000L,
                    "2025-01-15T10:30:00", "2026-06-06T22:00:00"
            );

            provider.savePlayerStats(playerId, original);
            PlayerStatsSnapshot loaded = provider.loadPlayerStats(playerId);

            assertNotNull(loaded, "ロードされた統計がnullでないこと");
            assertAll("全フィールドの一致確認",
                    () -> assertEquals(original.name(), loaded.name(), "name"),
                    () -> assertEquals(original.totalSessions(), loaded.totalSessions(), "totalSessions"),
                    () -> assertEquals(original.totalPurchases(), loaded.totalPurchases(), "totalPurchases"),
                    () -> assertEquals(original.totalCashouts(), loaded.totalCashouts(), "totalCashouts"),
                    () -> assertEquals(original.netProfit(), loaded.netProfit(), "netProfit"),
                    () -> assertEquals(original.wins(), loaded.wins(), "wins"),
                    () -> assertEquals(original.losses(), loaded.losses(), "losses"),
                    () -> assertEquals(original.draws(), loaded.draws(), "draws"),
                    () -> assertEquals(original.biggestWin(), loaded.biggestWin(), "biggestWin"),
                    () -> assertEquals(original.biggestLoss(), loaded.biggestLoss(), "biggestLoss"),
                    () -> assertEquals(original.firstPlayed(), loaded.firstPlayed(), "firstPlayed"),
                    () -> assertEquals(original.lastPlayed(), loaded.lastPlayed(), "lastPlayed")
            );
        }

        @Test
        @DisplayName("loadPlayerStats: 未登録プレイヤーはnullを返す")
        void loadPlayerStats_未登録プレイヤーはnullを返す() {
            UUID unknownPlayer = UUID.randomUUID();

            PlayerStatsSnapshot result = provider.loadPlayerStats(unknownPlayer);

            assertNull(result, "未登録プレイヤーの統計はnullであること");
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // セッション購入
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("セッション購入")
    class PurchaseTests {

        @Test
        @DisplayName("addPurchase: 累積加算される")
        void addPurchase_累積加算される() {
            UUID player = UUID.randomUUID();

            provider.addPurchase(player, 100L);
            provider.addPurchase(player, 200L);

            long total = provider.getPurchase(player);
            assertEquals(300L, total, "購入額が累積加算されること (100 + 200 = 300)");
        }

        @Test
        @DisplayName("getAllPurchases: 全プレイヤー分返される")
        void getAllPurchases_全プレイヤー分返される() {
            UUID p1 = UUID.randomUUID();
            UUID p2 = UUID.randomUUID();
            UUID p3 = UUID.randomUUID();

            provider.addPurchase(p1, 100L);
            provider.addPurchase(p2, 200L);
            provider.addPurchase(p3, 300L);

            Map<UUID, Long> all = provider.getAllPurchases();

            assertAll("全プレイヤー購入データの確認",
                    () -> assertEquals(3, all.size(), "3プレイヤー分のデータがあること"),
                    () -> assertEquals(100L, all.get(p1), "p1の購入額"),
                    () -> assertEquals(200L, all.get(p2), "p2の購入額"),
                    () -> assertEquals(300L, all.get(p3), "p3の購入額")
            );
        }

        @Test
        @DisplayName("clearPurchases: 全データ削除される")
        void clearPurchases_全データ削除される() {
            UUID p1 = UUID.randomUUID();
            UUID p2 = UUID.randomUUID();

            provider.addPurchase(p1, 500L);
            provider.addPurchase(p2, 600L);

            provider.clearPurchases();

            assertAll("購入データクリアの確認",
                    () -> assertTrue(provider.getAllPurchases().isEmpty(),
                            "全購入データが空であること"),
                    () -> assertEquals(0L, provider.getPurchase(p1),
                            "p1の購入額が0であること"),
                    () -> assertEquals(0L, provider.getPurchase(p2),
                            "p2の購入額が0であること")
            );
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // オフライン配当
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("オフライン配当")
    class OfflineResultTests {

        @Test
        @DisplayName("addAndGet: ベットと勝利金が正しく記録される")
        void addAndGet_ベットと勝利金が正しく記録される() {
            UUID player = UUID.randomUUID();

            provider.addOfflineResult(player, 500L, 1200L);

            long[] result = provider.getAndClearOfflineResult(player);

            assertNotNull(result, "オフライン結果がnullでないこと");
            assertAll("ベットと勝利金の確認",
                    () -> assertEquals(2, result.length, "配列長が2であること"),
                    () -> assertEquals(500L, result[0], "ベット額が500であること"),
                    () -> assertEquals(1200L, result[1], "勝利金が1200であること")
            );
        }

        @Test
        @DisplayName("getAndClear: 取得後にデータが削除される")
        void getAndClear_取得後にデータが削除される() {
            UUID player = UUID.randomUUID();

            provider.addOfflineResult(player, 300L, 800L);

            // 1回目の取得
            long[] firstGet = provider.getAndClearOfflineResult(player);
            assertNotNull(firstGet, "1回目の取得はnullでないこと");

            // 2回目の取得 — クリア済みなのでnull
            long[] secondGet = provider.getAndClearOfflineResult(player);
            assertNull(secondGet, "2回目の取得はnullであること（クリア済み）");
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Write-Through とフォールバック
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("WriteThroughとフォールバック")
    class WriteThroughAndFallbackTests {

        @Test
        @DisplayName("writeThrough: YAMLにもデータが書き込まれる")
        void writeThrough_YAMLにもデータが書き込まれる() {
            UUID player = UUID.randomUUID();
            String category = "casino";

            // provider 経由で更新（Redis + YAML に書き込まれるはず）
            provider.updateRanking(category, player, 5000L);

            // YAML 側を直接参照して確認
            Map<UUID, Long> yamlData = yamlProvider.getRankingData(category);
            assertAll("YAML Write-Through の確認",
                    () -> assertFalse(yamlData.isEmpty(), "YAMLにデータが書き込まれていること"),
                    () -> assertEquals(5000L, yamlData.get(player),
                            "YAMLのスコアが5000であること")
            );
        }

        @Test
        @DisplayName("readFromRedis: YAMLより優先される")
        void readFromRedis_YAMLより優先される() {
            UUID player = UUID.randomUUID();
            String category = "casino";

            // YAML に直接異なる値を書き込む
            yamlProvider.updateRanking(category, player, 1000L);

            // Redis に直接異なる値を書き込む
            try (var jedis = new redis.clients.jedis.Jedis(
                    redisServer.getHost(), redisServer.getBindPort())) {
                jedis.zincrby("test:ranking:" + category, 9999.0, player.toString());
            }

            // provider 経由で読み込み → Redis の値が優先される
            Map<UUID, Long> data = provider.getRankingData(category);
            assertAll("Redis 優先読み込みの確認",
                    () -> assertTrue(data.containsKey(player), "プレイヤーが含まれること"),
                    () -> assertEquals(9999L, data.get(player),
                            "Redisの値(9999)がYAMLの値(1000)より優先されること")
            );
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // 接続管理
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("接続管理")
    class ConnectionManagementTests {

        @Test
        @DisplayName("isAvailable: 接続可能な場合trueを返す")
        void isAvailable_接続可能な場合trueを返す() {
            boolean available = redisManager.isAvailable();

            assertTrue(available, "モックRedisサーバーに接続可能であること");
        }

        @Test
        @DisplayName("shutdown: 正常にシャットダウンできる")
        void shutdown_正常にシャットダウンできる() {
            // 別のマネージャを作成してシャットダウンをテスト（メインを壊さないため）
            RedisConnectionManager tempManager = new RedisConnectionManager(
                    redisServer.getHost(), redisServer.getBindPort(),
                    "", 0, 2, 1, Logger.getLogger("test-shutdown"));

            // シャットダウン前は接続可能
            assertTrue(tempManager.isAvailable(), "シャットダウン前は接続可能であること");

            // シャットダウン実行 — 例外がスローされないこと
            assertDoesNotThrow(tempManager::shutdown,
                    "shutdown() が例外をスローしないこと");
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // フォールバックモード
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("フォールバックモード")
    class FallbackModeTests {

        @Test
        @DisplayName("isFallbackMode: 正常時はfalseを返す")
        void isFallbackMode_正常時はfalseを返す() {
            assertFalse(provider.isFallbackMode(),
                    "正常接続時はフォールバックモードでないこと");
        }

        @Test
        @DisplayName("tryRecover: 接続可能時はtrueを返す")
        void tryRecover_接続可能時はtrueを返す() {
            boolean recovered = provider.tryRecover();

            assertTrue(recovered, "Redisが利用可能な場合、tryRecoverはtrueを返すこと");
        }
    }
}
