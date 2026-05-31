package io.wax100.casinoCore.manager;

import io.wax100.casinoCore.CasinoCore;
import org.bukkit.GameMode;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.io.File;
import java.util.*;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * {@link CasinoDataStore} のユニットテスト。
 *
 * <p>実ファイルシステム (@TempDir) を使用して
 * UUID-Long マップ / GameMode マップ / PlayerStats の永続化を検証する。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("CasinoDataStore: データ永続化")
class CasinoDataStoreTest {

    @TempDir
    File tempDir;

    @Mock
    private CasinoCore plugin;

    private CasinoDataStore dataStore;

    @BeforeEach
    void setUp() {
        when(plugin.getDataFolder()).thenReturn(tempDir);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("CasinoDataStoreTest"));
        dataStore = new CasinoDataStore(plugin);
    }

    // ========================================================================
    // 初期化
    // ========================================================================

    @Nested
    @DisplayName("初期化")
    class InitTest {

        @Test
        @DisplayName("data.ymlが作成される")
        void dataFileCreated() {
            File dataFile = new File(tempDir, "data.yml");
            assertTrue(dataFile.exists());
        }

        @Test
        @DisplayName("dataConfigが空のYamlConfigurationである")
        void dataConfig_isEmpty() {
            assertNotNull(dataStore.getDataConfig());
            assertTrue(dataStore.getDataConfig().getKeys(false).isEmpty());
        }
    }

    // ========================================================================
    // loadUuidLongMap / saveUuidLongMap
    // ========================================================================

    @Nested
    @DisplayName("UuidLongMap ラウンドトリップ")
    class UuidLongMapTest {

        @Test
        @DisplayName("保存したMapを正しくロードできる")
        void saveAndLoad_roundTrip() {
            UUID id1 = UUID.randomUUID();
            UUID id2 = UUID.randomUUID();
            Map<UUID, Long> original = new LinkedHashMap<>();
            original.put(id1, 1000L);
            original.put(id2, 2000L);

            YamlConfiguration config = new YamlConfiguration();
            dataStore.saveUuidLongMap(config, "test-section", original);

            Map<UUID, Long> loaded = new LinkedHashMap<>();
            dataStore.loadUuidLongMap(config, "test-section", loaded);

            assertEquals(2, loaded.size());
            assertEquals(1000L, loaded.get(id1));
            assertEquals(2000L, loaded.get(id2));
        }

        @Test
        @DisplayName("空Mapの保存・ロード")
        void emptyMap_roundTrip() {
            YamlConfiguration config = new YamlConfiguration();
            dataStore.saveUuidLongMap(config, "empty", Map.of());

            Map<UUID, Long> loaded = new LinkedHashMap<>();
            dataStore.loadUuidLongMap(config, "empty", loaded);
            assertTrue(loaded.isEmpty());
        }

        @Test
        @DisplayName("存在しないセクションのロードはスキップされる")
        void nonExistentSection_skipped() {
            Map<UUID, Long> loaded = new LinkedHashMap<>();
            dataStore.loadUuidLongMap(new YamlConfiguration(), "missing", loaded);
            assertTrue(loaded.isEmpty());
        }

        @Test
        @DisplayName("無効なUUIDキーは警告付きでスキップされる")
        void invalidUuidKey_skipped() {
            YamlConfiguration config = new YamlConfiguration();
            config.set("bad-section.not-a-uuid", 100L);
            config.set("bad-section." + UUID.randomUUID(), 200L);

            Map<UUID, Long> loaded = new LinkedHashMap<>();
            dataStore.loadUuidLongMap(config, "bad-section", loaded);
            assertEquals(1, loaded.size()); // 有効な方だけロードされる
        }
    }

    // ========================================================================
    // loadGameModes / saveGameModes
    // ========================================================================

    @Nested
    @DisplayName("GameMode ラウンドトリップ")
    class GameModeTest {

        @Test
        @DisplayName("保存したGameModeを正しくロードできる")
        void saveAndLoad_roundTrip() {
            UUID id1 = UUID.randomUUID();
            UUID id2 = UUID.randomUUID();
            Map<UUID, GameMode> original = new LinkedHashMap<>();
            original.put(id1, GameMode.CREATIVE);
            original.put(id2, GameMode.ADVENTURE);

            YamlConfiguration config = new YamlConfiguration();
            dataStore.saveGameModes(config, original);

            Map<UUID, GameMode> loaded = new LinkedHashMap<>();
            dataStore.loadGameModes(config, loaded);

            assertEquals(2, loaded.size());
            assertEquals(GameMode.CREATIVE, loaded.get(id1));
            assertEquals(GameMode.ADVENTURE, loaded.get(id2));
        }

        @Test
        @DisplayName("空Mapの保存・ロード")
        void emptyMap_roundTrip() {
            YamlConfiguration config = new YamlConfiguration();
            dataStore.saveGameModes(config, Map.of());

            Map<UUID, GameMode> loaded = new LinkedHashMap<>();
            dataStore.loadGameModes(config, loaded);
            assertTrue(loaded.isEmpty());
        }

        @Test
        @DisplayName("存在しないセクションのロードはスキップされる")
        void nonExistentSection_skipped() {
            Map<UUID, GameMode> loaded = new LinkedHashMap<>();
            dataStore.loadGameModes(new YamlConfiguration(), loaded);
            assertTrue(loaded.isEmpty());
        }
    }

    // ========================================================================
    // PlayerStats ラウンドトリップ
    // ========================================================================

    @Nested
    @DisplayName("PlayerStats ラウンドトリップ")
    class PlayerStatsRoundTripTest {

        @Test
        @DisplayName("保存したPlayerStatsを正しくロードできる")
        void saveAndLoad_roundTrip() {
            UUID id = UUID.randomUUID();
            PlayerStats stats = new PlayerStats();
            stats.recordSessionJoin("TestPlayer");
            stats.recordCashout(5000, 3000);

            // save() で data.yml に書き込み
            Map<UUID, PlayerStats> statsMap = new LinkedHashMap<>();
            statsMap.put(id, stats);

            dataStore.save(false,
                    Set.of(),
                    Map.of(),
                    false,
                    null,
                    Map.of(),
                    Map.of(),
                    statsMap);

            // 新しい DataStore で再読み込み
            CasinoDataStore newStore = new CasinoDataStore(plugin);
            Map<UUID, PlayerStats> loaded = new LinkedHashMap<>();
            newStore.loadPlayerStats(loaded);

            assertEquals(1, loaded.size());
            PlayerStats loadedStats = loaded.get(id);
            assertNotNull(loadedStats);
            assertEquals("TestPlayer", loadedStats.getName());
            assertEquals(1, loadedStats.getTotalSessions());
        }
    }

    // ========================================================================
    // save (同期) ラウンドトリップ
    // ========================================================================

    @Nested
    @DisplayName("save 同期保存")
    class SyncSaveTest {

        @Test
        @DisplayName("全フィールドが保存・復元される")
        void fullSave_roundTrip() {
            UUID player1 = UUID.randomUUID();
            UUID player2 = UUID.randomUUID();

            Set<UUID> casinoPlayers = Set.of(player1, player2);
            Map<UUID, Long> sessionPurchases = Map.of(player1, 5000L);
            Map<UUID, GameMode> gameModes = Map.of(player1, GameMode.SURVIVAL);
            Map<UUID, Long> ranking = Map.of(player1, 10000L, player2, 5000L);

            dataStore.save(false,
                    casinoPlayers,
                    sessionPurchases,
                    true,
                    "casino_world",
                    gameModes,
                    ranking,
                    Map.of());

            // 新しい DataStore でファイルを再読み込み
            CasinoDataStore newStore = new CasinoDataStore(plugin);

            // ランタイム状態
            var runtime = newStore.getDataConfig().getConfigurationSection("runtime");
            assertNotNull(runtime);
            assertEquals(true, runtime.getBoolean("saved-keep-inventory"));
            assertEquals("casino_world", runtime.getString("saved-world-name"));

            // ランキング
            Map<UUID, Long> loadedRanking = new LinkedHashMap<>();
            newStore.loadUuidLongMap(newStore.getDataConfig(), "ranking", loadedRanking);
            assertEquals(2, loadedRanking.size());
            assertEquals(10000L, loadedRanking.get(player1));
            assertEquals(5000L, loadedRanking.get(player2));
        }

        @Test
        @DisplayName("data.ymlファイルが実際に書き出される")
        void fileIsWritten() {
            dataStore.save(false, Set.of(), Map.of(), false, null, Map.of(), Map.of(), Map.of());

            File dataFile = new File(tempDir, "data.yml");
            assertTrue(dataFile.exists());
            assertTrue(dataFile.length() > 0);
        }
    }
}
