package io.wax100.arenaCore.integration;

import io.wax100.arenaCore.ArenaCore;
import io.wax100.arenaCore.manager.TerrainManager;
import io.wax100.arenaCore.model.ArenaFieldConfig;
import io.wax100.arenaCore.model.ArenaSession;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.PluginManager;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.lang.reflect.Field;
import java.util.List;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * TerrainManager の一気通貫テスト。
 *
 * <p>IDLE → TRACKING → FLUSHING → IDLE の完全な状態遷移を検証する。
 * Bukkit API は最小限のモックで代替し、ファイルI/Oは実ファイルシステムを使用する。
 */
@DisplayName("TerrainManager: 地形復元ライフサイクルIT")
class TerrainRestoreIT {

    @TempDir
    File dataFolder;

    private ArenaCore plugin;
    private TerrainManager terrainManager;
    private Server server;

    @BeforeEach
    void setUp() throws Exception {
        plugin = mock(ArenaCore.class);
        server = mock(Server.class);
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        PluginManager pluginManager = mock(PluginManager.class);

        when(plugin.getServer()).thenReturn(server);
        when(plugin.getDataFolder()).thenReturn(dataFolder);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("TerrainRestoreIT"));
        when(server.getPluginManager()).thenReturn(pluginManager);
        when(server.getScheduler()).thenReturn(scheduler);
        when(server.getLogger()).thenReturn(Logger.getLogger("MockServer"));

        // Bukkit static method が Server を参照するので set する
        setBukkitServer(server);

        // WorldEdit プラグインが存在するとモック
        org.bukkit.plugin.Plugin wePlugin = mock(org.bukkit.plugin.Plugin.class);
        when(pluginManager.getPlugin("WorldEdit")).thenReturn(wePlugin);

        // config: 地形復元有効、遅延は短く
        YamlConfiguration config = new YamlConfiguration();
        config.set("terrain-restore.enabled", true);
        config.set("terrain-restore.during-match-delay", 5);
        config.set("terrain-restore.post-match-delay", 1);
        config.set("terrain-restore.post-match-blocks-per-tick", 100);
        config.set("terrain-restore.effects", false);
        when(plugin.getConfig()).thenReturn(config);

        // BukkitRunnable.runTaskTimer のモック
        BukkitTask mockTask = mock(BukkitTask.class);
        when(mockTask.isCancelled()).thenReturn(false);
        when(scheduler.runTaskTimer(eq(plugin), any(Runnable.class), anyLong(), anyLong()))
                .thenReturn(mockTask);

        terrainManager = new TerrainManager(plugin, new io.wax100.arenaCore.storage.MemoryTerrainStorage());
    }

    @AfterEach
    void tearDown() throws Exception {
        // Bukkit server をクリアして他のテストへの影響を防止
        setBukkitServer(null);
    }

    /**
     * リフレクションで Bukkit の内部 server フィールドを設定する。
     * BukkitRunnable.runTaskTimer() や Bukkit.broadcastMessage() が
     * 内部的に Bukkit.getServer() を呼ぶため必要。
     */
    private static void setBukkitServer(Server server) throws Exception {
        Field serverField = Bukkit.class.getDeclaredField("server");
        serverField.setAccessible(true);
        serverField.set(null, server);
    }

    // ── ヘルパー ──

    private ArenaSession createSessionWithField() {
        ArenaSession session = new ArenaSession("ITArena", List.of("Red", "Blue"));
        session.setFieldConfig(ArenaFieldConfig.of("world", 0, 0, 0, 10, 10, 10));
        return session;
    }

    private Location mockLocation(String worldName, int x, int y, int z) {
        World mockWorld = mock(World.class);
        when(mockWorld.getName()).thenReturn(worldName);
        Location loc = mock(Location.class);
        when(loc.getWorld()).thenReturn(mockWorld);
        when(loc.getBlockX()).thenReturn(x);
        when(loc.getBlockY()).thenReturn(y);
        when(loc.getBlockZ()).thenReturn(z);

        Block block = mock(Block.class);
        BlockData currentData = mock(BlockData.class);
        when(block.getBlockData()).thenReturn(currentData);
        when(loc.getBlock()).thenReturn(block);

        return loc;
    }

    // ========================================================================
    // 状態遷移
    // ========================================================================

    @Nested
    @DisplayName("状態遷移テスト")
    class StateTransitionTest {

        @Test
        @DisplayName("初期状態ではblockingがfalse")
        void initialState_notBlocking() {
            assertFalse(terrainManager.isBlocking());
        }

        @Test
        @DisplayName("startTracking後にblockingがtrue")
        void afterStartTracking_isBlocking() {
            ArenaSession session = createSessionWithField();
            terrainManager.startTracking(session);
            assertTrue(terrainManager.isBlocking());
        }

        @Test
        @DisplayName("finishAndFlush後にblockingがtrue（FLUSHINGへ遷移）")
        void afterFinishAndFlush_isBlocking() {
            ArenaSession session = createSessionWithField();
            terrainManager.startTracking(session);
            terrainManager.finishAndFlush();
            assertTrue(terrainManager.isBlocking());
        }

        @Test
        @DisplayName("cancelAndClear後にblockingがfalse")
        void afterCancelAndClear_notBlocking() {
            ArenaSession session = createSessionWithField();
            terrainManager.startTracking(session);
            terrainManager.cancelAndClear();
            assertFalse(terrainManager.isBlocking());
        }
    }

    // ========================================================================
    // recordBreak
    // ========================================================================

    @Nested
    @DisplayName("ブロック記録テスト")
    class RecordBreakTest {

        @Test
        @DisplayName("TRACKING状態かつ範囲内のブロックは記録される")
        void tracking_insideField_recorded() {
            ArenaSession session = createSessionWithField();
            terrainManager.startTracking(session);

            Location loc = mockLocation("world", 5, 5, 5);
            BlockData original = mock(BlockData.class);
            assertDoesNotThrow(() -> terrainManager.recordBreak(loc, original));
        }

        @Test
        @DisplayName("IDLE状態ではブロックが記録されない（例外なし）")
        void idle_noRecord() {
            Location loc = mockLocation("world", 5, 5, 5);
            BlockData original = mock(BlockData.class);
            assertDoesNotThrow(() -> terrainManager.recordBreak(loc, original));
        }

        @Test
        @DisplayName("範囲外のブロックは記録されない")
        void outsideField_noRecord() {
            ArenaSession session = createSessionWithField();
            terrainManager.startTracking(session);

            Location loc = mockLocation("world", 99, 99, 99);
            BlockData original = mock(BlockData.class);
            assertDoesNotThrow(() -> terrainManager.recordBreak(loc, original));
        }

        @Test
        @DisplayName("異なるワールドのブロックは記録されない")
        void differentWorld_noRecord() {
            ArenaSession session = createSessionWithField();
            terrainManager.startTracking(session);

            Location loc = mockLocation("nether", 5, 5, 5);
            BlockData original = mock(BlockData.class);
            assertDoesNotThrow(() -> terrainManager.recordBreak(loc, original));
        }
    }

    // ========================================================================
    // fieldConfig未設定
    // ========================================================================

    @Nested
    @DisplayName("fieldConfig未設定")
    class NoFieldConfigTest {

        @Test
        @DisplayName("fieldConfig未設定のセッションではblockingにならない")
        void noField_remainsIdle() {
            ArenaSession session = new ArenaSession("NoField", List.of("A", "B"));
            terrainManager.startTracking(session);
            assertFalse(terrainManager.isBlocking());
        }
    }

    // ========================================================================
    // enabled=false
    // ========================================================================

    @Nested
    @DisplayName("地形復元無効時")
    class DisabledTest {

        @Test
        @DisplayName("enabled=falseでstartTrackingしてもblockingにならない")
        void disabled_remainsIdle() {
            YamlConfiguration disabledConfig = new YamlConfiguration();
            disabledConfig.set("terrain-restore.enabled", false);
            when(plugin.getConfig()).thenReturn(disabledConfig);

            TerrainManager disabledTm = new TerrainManager(plugin, new io.wax100.arenaCore.storage.MemoryTerrainStorage());

            ArenaSession session = createSessionWithField();
            disabledTm.startTracking(session);
            assertFalse(disabledTm.isBlocking());
        }
    }

    // ========================================================================
    // .active マーカーファイル
    // ========================================================================

    @Nested
    @DisplayName("マーカーファイルテスト")
    class MarkerFileTest {

        @Test
        @DisplayName("startTracking後に.activeファイルが作成される")
        void tracking_createsActiveFile() {
            ArenaSession session = createSessionWithField();
            terrainManager.startTracking(session);

            File activeFile = new File(dataFolder, "arenas/" + session.getName() + ".active");
            assertTrue(activeFile.exists(), ".activeファイルが存在すること");
        }

        @Test
        @DisplayName(".activeファイルにarena名とworld名が含まれる")
        void activeFile_containsArenaAndWorld() {
            ArenaSession session = createSessionWithField();
            terrainManager.startTracking(session);

            File activeFile = new File(dataFolder, "arenas/" + session.getName() + ".active");
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(activeFile);
            assertEquals(session.getName(), yaml.getString("arena"));
            assertEquals("world", yaml.getString("world"));
        }

        @Test
        @DisplayName("cancelAndClear後も.activeファイルは残る（次回復旧用）")
        void cancelAndClear_activeFileRemains() {
            ArenaSession session = createSessionWithField();
            terrainManager.startTracking(session);
            terrainManager.cancelAndClear();

            File activeFile = new File(dataFolder, "arenas/" + session.getName() + ".active");
            assertTrue(activeFile.exists(), "cancelAndClear後も.activeが残ること");
        }
    }

    // ========================================================================
    // 完全ライフサイクル
    // ========================================================================

    @Nested
    @DisplayName("完全ライフサイクル")
    class FullLifecycleTest {

        @Test
        @DisplayName("IDLE → startTracking → recordBreak → finishAndFlush → cancelAndClear")
        void fullCycle_noExceptions() {
            ArenaSession session = createSessionWithField();

            // Step 1: IDLE → TRACKING
            assertFalse(terrainManager.isBlocking());
            terrainManager.startTracking(session);
            assertTrue(terrainManager.isBlocking());

            // Step 2: ブロック記録
            Location loc = mockLocation("world", 5, 5, 5);
            BlockData original = mock(BlockData.class);
            terrainManager.recordBreak(loc, original);

            // Step 3: TRACKING → FLUSHING
            terrainManager.finishAndFlush();
            assertTrue(terrainManager.isBlocking());

            // Step 4: クリーンアップ
            terrainManager.cancelAndClear();
            assertFalse(terrainManager.isBlocking());
        }

        @Test
        @DisplayName("2回連続でライフサイクルを実行できる")
        void doubleCycle_works() {
            // 1回目
            ArenaSession session1 = createSessionWithField();
            terrainManager.startTracking(session1);
            terrainManager.cancelAndClear();
            assertFalse(terrainManager.isBlocking());

            // 2回目
            ArenaSession session2 = new ArenaSession("Arena2", List.of("A", "B"));
            session2.setFieldConfig(ArenaFieldConfig.of("world", -10, 0, -10, 10, 20, 10));
            terrainManager.startTracking(session2);
            assertTrue(terrainManager.isBlocking());
            terrainManager.cancelAndClear();
            assertFalse(terrainManager.isBlocking());
        }
    }
}
