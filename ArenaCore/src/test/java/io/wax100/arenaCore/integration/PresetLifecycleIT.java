package io.wax100.arenaCore.integration;

import io.wax100.arenaCore.ArenaCore;
import io.wax100.arenaCore.manager.ArenaManager;
import io.wax100.arenaCore.manager.ArenaPresetStore;
import io.wax100.arenaCore.manager.ArenaPresetStore.PresetData;
import io.wax100.arenaCore.manager.BettingManager;
import io.wax100.arenaCore.manager.RegionManager;
import io.wax100.arenaCore.manager.TerrainManager;
import io.wax100.arenaCore.model.ArenaFieldConfig;
import io.wax100.arenaCore.model.ArenaSession;
import io.wax100.arenaCore.model.ArenaState;
import io.wax100.arenaCore.model.BettingRegion;
import io.wax100.arenaCore.model.TeamAreaConfig;
import org.bukkit.Server;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.PluginManager;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * プリセット保存 → ロード → セッション復元の一気通貫テスト。
 *
 * <p>ArenaPresetStore で保存したデータを ArenaManager.createFromPreset で
 * 復元し、セッションの全プロパティが正しく再現されることを検証する。
 */
@DisplayName("プリセット → セッション復元 ライフサイクルIT")
class PresetLifecycleIT {

    @TempDir
    File dataFolder;

    private ArenaCore plugin;
    private ArenaManager arenaManager;
    private ArenaPresetStore presetStore;
    private RegionManager regionManager;
    private TerrainManager terrainManager;

    @BeforeEach
    void setUp() {
        plugin = mock(ArenaCore.class);
        Server server = mock(Server.class);
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        PluginManager pluginManager = mock(PluginManager.class);

        when(plugin.getServer()).thenReturn(server);
        when(plugin.getDataFolder()).thenReturn(dataFolder);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("PresetLifecycleIT"));
        when(server.getPluginManager()).thenReturn(pluginManager);
        when(server.getScheduler()).thenReturn(scheduler);

        YamlConfiguration config = new YamlConfiguration();
        config.set("terrain-restore.enabled", false); // 地形復元無効（WorldEdit不要に）
        config.set("entry-fee", 0L);
        when(plugin.getConfig()).thenReturn(config);

        regionManager = new RegionManager(false);
        terrainManager = new TerrainManager(plugin);
        BettingManager bettingManager = mock(BettingManager.class);

        arenaManager = new ArenaManager(plugin, bettingManager, regionManager, terrainManager);
        presetStore = new ArenaPresetStore(dataFolder, Logger.getLogger("PresetLifecycleIT"));
    }

    // ── テスト用データ生成 ──

    private ArenaSession createConfiguredSession() {
        ArenaSession session = arenaManager.createArena("Colosseum", List.of("Warriors", "Monsters"));
        assertNotNull(session);

        // フィールド設定
        session.setFieldConfig(ArenaFieldConfig.of("world", -50, 0, -50, 50, 100, 50));

        // 待機場設定（Warriors のみ）
        session.setTeamAreaConfig("Warriors",
                new TeamAreaConfig("world", -100, 60, -100, -80, 70, -80));

        // Monsters チームをMobチームに
        session.markAsMobTeam("Monsters");
        session.setTeamAreaConfig("Monsters",
                new TeamAreaConfig("world", 80, 60, 80, 100, 70, 100));

        return session;
    }

    // ========================================================================
    // ラウンドトリップ: 保存 → ロード → セッション復元
    // ========================================================================

    @Nested
    @DisplayName("保存 → ロード → セッション復元")
    class RoundTripTest {

        @Test
        @DisplayName("全プロパティが正しく復元される")
        void fullRoundTrip_allPropertiesRestored() {
            // ── STEP 1: セッション作成＆設定 ──
            ArenaSession original = createConfiguredSession();

            // ── STEP 2: プリセット保存 ──
            presetStore.save("Colosseum", original, regionManager);

            // ── STEP 3: セッション破棄 ──
            // cancelArena は Bukkit.broadcastMessage を呼ぶので、直接 null 化
            // arenaManager 内部状態をリセット
            original.setState(ArenaState.RECRUITING);
            original.setState(ArenaState.BETTING);
            original.setState(ArenaState.CLOSED);
            original.setState(ArenaState.ACTIVE);
            original.setState(ArenaState.FINISHED);
            original.resetSession();

            // 新しい ArenaManager でクリーンな状態から復元
            BettingManager bettingManager2 = mock(BettingManager.class);
            RegionManager regionManager2 = new RegionManager(false);
            ArenaManager newManager = new ArenaManager(
                    plugin, bettingManager2, regionManager2, terrainManager);

            // ── STEP 4: プリセットロード ──
            PresetData data = presetStore.load("Colosseum");
            assertNotNull(data, "プリセットデータがロードされること");

            // ── STEP 5: セッション復元 ──
            ArenaSession restored = newManager.createFromPreset(data);
            assertNotNull(restored, "セッションが復元されること");

            // ── STEP 6: 検証 ──
            // 基本情報
            assertEquals("Colosseum", restored.getName());
            assertEquals(List.of("Warriors", "Monsters"), restored.getTeamNames());
            assertEquals(ArenaState.SETUP, restored.getState());

            // Mobチーム
            assertFalse(restored.isMobTeam("Warriors"));
            assertTrue(restored.isMobTeam("Monsters"));

            // フィールド
            ArenaFieldConfig field = restored.getFieldConfig();
            assertNotNull(field);
            assertEquals("world", field.worldName());
            assertEquals(-50, field.minX());
            assertEquals(0, field.minY());
            assertEquals(-50, field.minZ());
            assertEquals(50, field.maxX());
            assertEquals(100, field.maxY());
            assertEquals(50, field.maxZ());

            // 待機場 (Warriors)
            TeamAreaConfig warriorsArea = restored.getTeamAreaConfig("Warriors");
            assertNotNull(warriorsArea);
            assertEquals("world", warriorsArea.worldName());
            assertEquals(-100, warriorsArea.minX());
            assertEquals(60, warriorsArea.minY());
            assertEquals(-100, warriorsArea.minZ());
            assertEquals(-80, warriorsArea.maxX());
            assertEquals(70, warriorsArea.maxY());
            assertEquals(-80, warriorsArea.maxZ());

            // 待機場 (Monsters)
            TeamAreaConfig monstersArea = restored.getTeamAreaConfig("Monsters");
            assertNotNull(monstersArea);
            assertEquals(80, monstersArea.minX());
        }
    }

    // ========================================================================
    // BettingRegion 付きラウンドトリップ
    // ========================================================================

    @Nested
    @DisplayName("BettingRegion付きラウンドトリップ")
    class BettingRegionRoundTripTest {

        @Test
        @DisplayName("BettingRegionも含めて正しく復元される")
        void withBettingRegions_restored() {
            ArenaSession session = createConfiguredSession();

            // BettingRegion 登録
            BettingRegion warRegion = BettingRegion.of(
                    "Warriors", "world", -30, 60, -30, -20, 65, -20);
            BettingRegion monRegion = BettingRegion.of(
                    "Monsters", "world", 20, 60, 20, 30, 65, 30);
            regionManager.registerBettingRegion("Warriors", warRegion);
            regionManager.registerBettingRegion("Monsters", monRegion);

            // 保存
            presetStore.save("BetArena", session, regionManager);

            // ロード＆復元
            PresetData data = presetStore.load("BetArena");
            assertNotNull(data);

            RegionManager newRegionManager = new RegionManager(false);
            BettingManager bm2 = mock(BettingManager.class);
            ArenaManager newManager = new ArenaManager(
                    plugin, bm2, newRegionManager, terrainManager);
            ArenaSession restored = newManager.createFromPreset(data);
            assertNotNull(restored);

            // BettingRegion 検証
            assertTrue(newRegionManager.hasBettingRegion("Warriors"));
            assertTrue(newRegionManager.hasBettingRegion("Monsters"));

            BettingRegion loadedWarRegion = newRegionManager.getBettingRegion("Warriors");
            assertNotNull(loadedWarRegion);
            assertEquals("world", loadedWarRegion.worldName());
            assertEquals(-30, loadedWarRegion.minX());
            assertEquals(60, loadedWarRegion.minY());
        }
    }

    // ========================================================================
    // エッジケース
    // ========================================================================

    @Nested
    @DisplayName("エッジケース")
    class EdgeCaseTest {

        @Test
        @DisplayName("既存セッションがある場合にcreateFromPresetがnullを返す")
        void existingSession_returnsNull() {
            arenaManager.createArena("Existing", List.of("A", "B"));

            PresetData data = new PresetData("Another",
                    List.of("C", "D"), Set.of(), null,
                    Map.of(), Map.of(), Map.of());

            ArenaSession result = arenaManager.createFromPreset(data);
            assertNull(result);
        }

        @Test
        @DisplayName("存在しないプリセットのロードでnullを返す")
        void loadNonExistent_returnsNull() {
            assertNull(presetStore.load("ghost"));
        }

        @Test
        @DisplayName("上書き保存が正しく動作する")
        void overwriteSave_works() {
            ArenaSession session = createConfiguredSession();
            presetStore.save("Colosseum", session, regionManager);

            // fieldConfig を変更して再保存
            session.setFieldConfig(ArenaFieldConfig.of("world", 0, 0, 0, 200, 200, 200));
            presetStore.save("Colosseum", session, regionManager);

            PresetData data = presetStore.load("Colosseum");
            assertNotNull(data);
            assertEquals(200, data.fieldConfig().maxX());
            assertEquals(200, data.fieldConfig().maxY());
        }

        @Test
        @DisplayName("削除後にloadがnullを返す")
        void deleteAndLoad_returnsNull() {
            ArenaSession session = createConfiguredSession();
            presetStore.save("ToDelete", session, regionManager);

            assertTrue(presetStore.delete("ToDelete"));
            assertNull(presetStore.load("ToDelete"));
        }

        @Test
        @DisplayName("list がファイルシステムと同期している")
        void list_reflectsFileSystem() {
            assertEquals(0, presetStore.list().size());

            ArenaSession session = createConfiguredSession();
            presetStore.save("Arena1", session, regionManager);
            presetStore.save("Arena2", session, regionManager);

            List<String> list = presetStore.list();
            assertEquals(2, list.size());
            assertTrue(list.contains("Arena1"));
            assertTrue(list.contains("Arena2"));

            presetStore.delete("Arena1");
            assertEquals(1, presetStore.list().size());
            assertEquals("Arena2", presetStore.list().get(0));
        }
    }

    // ========================================================================
    // 複合シナリオ: 複数回保存→ロード
    // ========================================================================

    @Nested
    @DisplayName("複数回保存→ロード")
    class MultipleRoundTripTest {

        @Test
        @DisplayName("3つの異なるプリセットを保存→個別にロードできる")
        void threePresets_loadIndependently() {
            // 3つのプリセットを作成
            for (int i = 1; i <= 3; i++) {
                // 前のセッションをクリア
                if (arenaManager.hasActiveSession()) {
                    ArenaSession current = arenaManager.getActiveSession();
                    current.setState(ArenaState.RECRUITING);
                    current.setState(ArenaState.BETTING);
                    current.setState(ArenaState.CLOSED);
                    current.setState(ArenaState.ACTIVE);
                    current.setState(ArenaState.FINISHED);
                    current.resetSession();
                    // リフレクションを避けるため、新しい ArenaManager を使用
                }

                BettingManager bm = mock(BettingManager.class);
                RegionManager rm = new RegionManager(false);
                ArenaManager mgr = new ArenaManager(plugin, bm, rm, terrainManager);

                ArenaSession session = mgr.createArena(
                        "Arena" + i, List.of("Team" + i + "A", "Team" + i + "B"));
                assertNotNull(session);

                session.setFieldConfig(
                        ArenaFieldConfig.of("world", i * 100, 0, 0, i * 100 + 50, 64, 50));
                presetStore.save("Arena" + i, session, rm);
            }

            // 個別にロード・検証
            for (int i = 1; i <= 3; i++) {
                PresetData data = presetStore.load("Arena" + i);
                assertNotNull(data, "Arena" + i + " がロードできること");
                assertEquals("Arena" + i, data.name());
                assertEquals(List.of("Team" + i + "A", "Team" + i + "B"), data.teamNames());

                ArenaFieldConfig field = data.fieldConfig();
                assertNotNull(field);
                assertEquals(i * 100, field.minX());
            }

            // 一覧検証
            List<String> list = presetStore.list();
            assertEquals(3, list.size());
            assertEquals("Arena1", list.get(0));
            assertEquals("Arena2", list.get(1));
            assertEquals("Arena3", list.get(2));
        }
    }
}
