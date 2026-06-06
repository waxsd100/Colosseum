package io.wax100.arenaCore.storage;

import com.github.fppt.jedismock.RedisServer;
import io.wax100.chipLib.storage.RedisConnectionManager;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import java.util.logging.Logger;

@DisplayName("RedisTerrainStorage 統合テスト")
class RedisTerrainStorageIT {

    private static RedisServer redisServer;
    private RedisConnectionManager redisManager;
    private RedisTerrainStorage storage;

    @BeforeAll
    static void startRedis() throws Exception {
        redisServer = RedisServer.newRedisServer().start();
    }

    @AfterAll
    static void stopRedis() throws Exception {
        if (redisServer != null) redisServer.stop();
    }

    @BeforeEach
    void setUp() {
        redisManager = new RedisConnectionManager(
                redisServer.getHost(), redisServer.getBindPort(),
                "", 0, 4, 2, Logger.getLogger("test"));
        storage = new RedisTerrainStorage(redisManager, "test");

        // Flush Redis between tests
        try (var jedis = new redis.clients.jedis.Jedis(
                redisServer.getHost(), redisServer.getBindPort())) {
            jedis.flushAll();
        }
    }

    @AfterEach
    void tearDown() {
        redisManager.shutdown();
    }

    // ==================== ブロック記録 ====================

    @Nested
    @DisplayName("ブロック記録")
    class BlockRecording {

        @Test
        @DisplayName("recordBlockChange_エントリがRedisに保存される")
        void recordBlockChange_エントリがRedisに保存される() {
            storage.recordBlockChange("arena_match_1", "world", 10, 64, -20,
                    "minecraft:stone", 100L);

            assertFalse(storage.isEmpty("arena_match_1"),
                    "ブロックを記録した後、セッションは空であってはならない");
        }

        @Test
        @DisplayName("recordBlockChange_セッションがアクティブセットに登録される")
        void recordBlockChange_セッションがアクティブセットに登録される() {
            storage.recordBlockChange("arena_match_1", "world", 5, 70, 5,
                    "minecraft:oak_planks", 200L);

            Set<String> sessions = storage.getPendingSessions();
            assertTrue(sessions.contains("arena_match_1"),
                    "記録後、getPendingSessionsにセッションIDが含まれるべき");
        }

        @Test
        @DisplayName("recordBlockChange_複数ブロックが記録される")
        void recordBlockChange_複数ブロックが記録される() {
            storage.recordBlockChange("arena_match_1", "world", 0, 60, 0,
                    "minecraft:stone", 100L);
            storage.recordBlockChange("arena_match_1", "world", 1, 61, 1,
                    "minecraft:oak_planks", 100L);
            storage.recordBlockChange("arena_match_1", "world", 2, 62, 2,
                    "minecraft:stone_bricks[waterlogged=false]", 100L);
            storage.recordBlockChange("arena_match_1", "world", 3, 63, 3,
                    "minecraft:glass", 100L);
            storage.recordBlockChange("arena_match_1", "world", 4, 64, 4,
                    "minecraft:dirt", 100L);

            // Poll all 5 entries to verify they are all stored
            List<BlockRestoreEntry> entries = storage.pollBatch("arena_match_1", 10);
            assertEquals(5, entries.size(),
                    "5つのブロックを記録したので、5エントリが取得されるべき");
        }
    }

    // ==================== 試合中復元 (pollReadyEntries) ====================

    @Nested
    @DisplayName("試合中復元 (pollReadyEntries)")
    class PollReadyEntries {

        @Test
        @DisplayName("pollReadyEntries_期限到達分のみ返される")
        void pollReadyEntries_期限到達分のみ返される() {
            storage.recordBlockChange("arena_match_1", "world", 0, 60, 0,
                    "minecraft:stone", 100L);
            storage.recordBlockChange("arena_match_1", "world", 1, 61, 1,
                    "minecraft:oak_planks", 200L);
            storage.recordBlockChange("arena_match_1", "world", 2, 62, 2,
                    "minecraft:glass", 300L);

            List<BlockRestoreEntry> ready = storage.pollReadyEntries("arena_match_1", 200L);
            assertEquals(2, ready.size(),
                    "tick 200 までに期限到達するエントリは2つであるべき");
        }

        @Test
        @DisplayName("pollReadyEntries_取得後にRedisから削除される")
        void pollReadyEntries_取得後にRedisから削除される() {
            storage.recordBlockChange("arena_match_1", "world", 0, 60, 0,
                    "minecraft:stone", 100L);
            storage.recordBlockChange("arena_match_1", "world", 1, 61, 1,
                    "minecraft:oak_planks", 200L);

            // First poll: should return both entries
            List<BlockRestoreEntry> firstPoll = storage.pollReadyEntries("arena_match_1", 200L);
            assertEquals(2, firstPoll.size(), "初回ポーリングで2エントリ取得されるべき");

            // Second poll: same range should return empty
            List<BlockRestoreEntry> secondPoll = storage.pollReadyEntries("arena_match_1", 200L);
            assertTrue(secondPoll.isEmpty(),
                    "取得済みエントリは削除されているため、再取得時は空であるべき");
        }

        @Test
        @DisplayName("pollReadyEntries_期限未到達は残る")
        void pollReadyEntries_期限未到達は残る() {
            storage.recordBlockChange("arena_match_1", "world", 0, 60, 0,
                    "minecraft:stone", 100L);
            storage.recordBlockChange("arena_match_1", "world", 1, 61, 1,
                    "minecraft:oak_planks", 200L);
            storage.recordBlockChange("arena_match_1", "world", 2, 62, 2,
                    "minecraft:glass", 300L);

            // Poll at tick 200 → entries with tick 100 and 200 are returned
            storage.pollReadyEntries("arena_match_1", 200L);

            // Tick 300 entry should still exist
            assertFalse(storage.isEmpty("arena_match_1"),
                    "tick 300 のエントリがまだ残っているため、セッションは空でないべき");

            List<BlockRestoreEntry> remaining = storage.pollReadyEntries("arena_match_1", 300L);
            assertEquals(1, remaining.size(),
                    "tick 300 のエントリ1つが残っているべき");
            assertEquals("minecraft:glass", remaining.get(0).blockDataString(),
                    "残りのエントリはglassであるべき");
        }

        @Test
        @DisplayName("pollReadyEntries_空セッションは空リストを返す")
        void pollReadyEntries_空セッションは空リストを返す() {
            List<BlockRestoreEntry> entries = storage.pollReadyEntries("nonexistent_session", 1000L);
            assertTrue(entries.isEmpty(),
                    "存在しないセッションのポーリングは空リストを返すべき");
        }
    }

    // ==================== 試合後復元 (pollBatch) ====================

    @Nested
    @DisplayName("試合後復元 (pollBatch)")
    class PollBatch {

        @Test
        @DisplayName("pollBatch_指定件数分返される")
        void pollBatch_指定件数分返される() {
            for (int i = 0; i < 10; i++) {
                storage.recordBlockChange("arena_match_1", "world", i, 60 + i, i,
                        "minecraft:stone", 100L + i);
            }

            List<BlockRestoreEntry> batch = storage.pollBatch("arena_match_1", 3);
            assertEquals(3, batch.size(),
                    "pollBatch(3) は3件のエントリを返すべき");
        }

        @Test
        @DisplayName("pollBatch_スコア昇順で返される")
        void pollBatch_スコア昇順で返される() {
            // Insert in non-sorted order
            storage.recordBlockChange("arena_match_1", "world", 0, 60, 0,
                    "minecraft:glass", 300L);
            storage.recordBlockChange("arena_match_1", "world", 1, 61, 1,
                    "minecraft:stone", 100L);
            storage.recordBlockChange("arena_match_1", "world", 2, 62, 2,
                    "minecraft:oak_planks", 200L);

            List<BlockRestoreEntry> batch = storage.pollBatch("arena_match_1", 3);
            assertEquals(3, batch.size(), "3エントリすべて取得されるべき");

            assertEquals(100L, batch.get(0).restoreAtTick(),
                    "最初のエントリは最小tick (100) であるべき");
            assertEquals(200L, batch.get(1).restoreAtTick(),
                    "2番目のエントリはtick 200 であるべき");
            assertEquals(300L, batch.get(2).restoreAtTick(),
                    "3番目のエントリは最大tick (300) であるべき");
        }

        @Test
        @DisplayName("pollBatch_キュー枯渇で空になる")
        void pollBatch_キュー枯渇で空になる() {
            storage.recordBlockChange("arena_match_1", "world", 0, 60, 0,
                    "minecraft:stone", 100L);
            storage.recordBlockChange("arena_match_1", "world", 1, 61, 1,
                    "minecraft:oak_planks", 200L);
            storage.recordBlockChange("arena_match_1", "world", 2, 62, 2,
                    "minecraft:glass", 300L);

            List<BlockRestoreEntry> batch = storage.pollBatch("arena_match_1", 5);
            assertEquals(3, batch.size(),
                    "3件しかないためpollBatch(5) は3件を返すべき");

            assertTrue(storage.isEmpty("arena_match_1"),
                    "すべて取得後、セッションは空であるべき");
        }
    }

    // ==================== セッション管理 ====================

    @Nested
    @DisplayName("セッション管理")
    class SessionManagement {

        @Test
        @DisplayName("isEmpty_エントリなしでtrue")
        void isEmpty_エントリなしでtrue() {
            assertTrue(storage.isEmpty("empty_session"),
                    "エントリがないセッションはtrueを返すべき");
        }

        @Test
        @DisplayName("isEmpty_エントリありでfalse")
        void isEmpty_エントリありでfalse() {
            storage.recordBlockChange("arena_match_1", "world", 0, 60, 0,
                    "minecraft:stone", 100L);

            assertFalse(storage.isEmpty("arena_match_1"),
                    "エントリがあるセッションはfalseを返すべき");
        }

        @Test
        @DisplayName("clearSession_全エントリとアクティブ登録が削除される")
        void clearSession_全エントリとアクティブ登録が削除される() {
            storage.recordBlockChange("arena_match_1", "world", 0, 60, 0,
                    "minecraft:stone", 100L);
            storage.recordBlockChange("arena_match_1", "world", 1, 61, 1,
                    "minecraft:oak_planks", 200L);
            storage.recordBlockChange("arena_match_1", "world", 2, 62, 2,
                    "minecraft:glass", 300L);

            // Verify data exists before clear
            assertFalse(storage.isEmpty("arena_match_1"), "クリア前はデータが存在するべき");
            assertTrue(storage.getPendingSessions().contains("arena_match_1"),
                    "クリア前はセッションが登録されているべき");

            storage.clearSession("arena_match_1");

            assertTrue(storage.isEmpty("arena_match_1"),
                    "クリア後、セッションは空であるべき");
            assertFalse(storage.getPendingSessions().contains("arena_match_1"),
                    "クリア後、getPendingSessionsにセッションが含まれないべき");
        }

        @Test
        @DisplayName("getPendingSessions_複数セッションが返される")
        void getPendingSessions_複数セッションが返される() {
            storage.recordBlockChange("arena_match_1", "world", 0, 60, 0,
                    "minecraft:stone", 100L);
            storage.recordBlockChange("arena_match_2", "world", 1, 61, 1,
                    "minecraft:oak_planks", 200L);
            storage.recordBlockChange("arena_match_3", "world", 2, 62, 2,
                    "minecraft:glass", 300L);

            Set<String> sessions = storage.getPendingSessions();
            assertEquals(3, sessions.size(), "3セッションが登録されているべき");
            assertTrue(sessions.containsAll(Set.of("arena_match_1", "arena_match_2", "arena_match_3")),
                    "すべてのセッションIDが含まれるべき");
        }

        @Test
        @DisplayName("getPendingSessions_クリア済みは含まれない")
        void getPendingSessions_クリア済みは含まれない() {
            storage.recordBlockChange("arena_match_1", "world", 0, 60, 0,
                    "minecraft:stone", 100L);
            storage.recordBlockChange("arena_match_2", "world", 1, 61, 1,
                    "minecraft:oak_planks", 200L);

            storage.clearSession("arena_match_1");

            Set<String> sessions = storage.getPendingSessions();
            assertEquals(1, sessions.size(), "クリア後、1セッションのみ残るべき");
            assertFalse(sessions.contains("arena_match_1"),
                    "クリア済みセッションは含まれないべき");
            assertTrue(sessions.contains("arena_match_2"),
                    "クリアされていないセッションは残るべき");
        }
    }

    // ==================== データ整合性 ====================

    @Nested
    @DisplayName("データ整合性")
    class DataIntegrity {

        @Test
        @DisplayName("serialize_deserialize_ラウンドトリップが正しい")
        void serialize_deserialize_ラウンドトリップが正しい() {
            BlockRestoreEntry original = new BlockRestoreEntry(
                    "world_nether", -128, 32, 256,
                    "minecraft:stone_bricks[waterlogged=false]", 500L);

            String serialized = original.serialize();
            BlockRestoreEntry deserialized = BlockRestoreEntry.deserialize(serialized, 500L);

            assertEquals(original.worldName(), deserialized.worldName(),
                    "ワールド名が一致するべき");
            assertEquals(original.x(), deserialized.x(), "X座標が一致するべき");
            assertEquals(original.y(), deserialized.y(), "Y座標が一致するべき");
            assertEquals(original.z(), deserialized.z(), "Z座標が一致するべき");
            assertEquals(original.blockDataString(), deserialized.blockDataString(),
                    "ブロックデータが一致するべき");
            assertEquals(original.restoreAtTick(), deserialized.restoreAtTick(),
                    "復元tickが一致するべき");
        }

        @Test
        @DisplayName("LongMaxValue_設置ブロックのスコア変換が正しい")
        void longMaxValue_設置ブロックのスコア変換が正しい() {
            // Record with Long.MAX_VALUE (permanent / placed block)
            storage.recordBlockChange("arena_match_1", "world", 10, 64, 10,
                    "minecraft:cobblestone", Long.MAX_VALUE);

            // pollReadyEntries with a normal tick should NOT return it
            List<BlockRestoreEntry> ready = storage.pollReadyEntries("arena_match_1", 1_000_000L);
            assertTrue(ready.isEmpty(),
                    "Long.MAX_VALUE のエントリは通常tickのpollReadyEntriesで返されないべき");

            // But pollBatch should return it
            List<BlockRestoreEntry> batch = storage.pollBatch("arena_match_1", 10);
            assertEquals(1, batch.size(),
                    "Long.MAX_VALUE のエントリはpollBatchで取得可能であるべき");
            assertEquals("minecraft:cobblestone", batch.get(0).blockDataString(),
                    "取得したエントリのブロックデータが正しいべき");
        }
    }
}
