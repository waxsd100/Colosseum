package io.wax100.arenaCore.manager;

import io.wax100.arenaCore.model.ArenaFieldConfig;
import io.wax100.arenaCore.model.ArenaSession;
import io.wax100.arenaCore.model.TeamAreaConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.util.List;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("ArenaPresetStore: プリセットの永続化")
class ArenaPresetStoreTest {

    @TempDir
    File tempDir;

    private ArenaPresetStore store;
    private Logger logger;

    @BeforeEach
    void setUp() {
        logger = Logger.getLogger("ArenaPresetStoreTest");
        store = new ArenaPresetStore(tempDir, logger);
    }

    // ── テスト用ヘルパー ──

    private ArenaSession mockSession(String name, ArenaFieldConfig field) {
        ArenaSession session = mock(ArenaSession.class);
        when(session.getName()).thenReturn(name);
        when(session.getTeamNames()).thenReturn(List.of("Red", "Blue"));
        when(session.isMobTeam("Red")).thenReturn(false);
        when(session.isMobTeam("Blue")).thenReturn(true);
        when(session.getFieldConfig()).thenReturn(field);
        when(session.getTeamAreaConfig("Red")).thenReturn(
                new TeamAreaConfig("world", 10, 0, 10, 20, 5, 20));
        when(session.getTeamAreaConfig("Blue")).thenReturn(null);
        return session;
    }

    private RegionManager mockRegionManager() {
        RegionManager rm = mock(RegionManager.class);
        when(rm.hasBettingRegion(anyString())).thenReturn(false);
        return rm;
    }

    // ========================================================================
    // コンストラクタ
    // ========================================================================

    @Nested
    @DisplayName("コンストラクタ")
    class ConstructorTest {

        @Test
        @DisplayName("nullのdataFolderでNPEが発生する")
        void nullDataFolder_throwsNPE() {
            assertThrows(NullPointerException.class,
                    () -> new ArenaPresetStore(null, logger));
        }

        @Test
        @DisplayName("nullのloggerでNPEが発生する")
        void nullLogger_throwsNPE() {
            assertThrows(NullPointerException.class,
                    () -> new ArenaPresetStore(tempDir, null));
        }

        @Test
        @DisplayName("arenasディレクトリが自動作成される")
        void arenasDir_isCreated() {
            new ArenaPresetStore(tempDir, logger);
            File arenasDir = new File(tempDir, "arenas");
            assertTrue(arenasDir.exists());
            assertTrue(arenasDir.isDirectory());
        }
    }

    // ========================================================================
    // 保存 + ロード (ラウンドトリップ)
    // ========================================================================

    @Nested
    @DisplayName("save + load ラウンドトリップ")
    class SaveLoadTest {

        @Test
        @DisplayName("保存したプリセットを正しくロードできる")
        void saveAndLoad_roundTrip() {
            ArenaFieldConfig field = ArenaFieldConfig.of("world", 0, 0, 0, 100, 64, 100);
            ArenaSession session = mockSession("test_arena", field);
            RegionManager rm = mockRegionManager();

            store.save("test_arena", session, rm);

            ArenaPresetStore.PresetData data = store.load("test_arena");
            assertNotNull(data);
            assertEquals("test_arena", data.name());
            assertEquals(List.of("Red", "Blue"), data.teamNames());

            // mob-teams
            assertFalse(data.mobTeams().contains("Red"));
            assertTrue(data.mobTeams().contains("Blue"));

            // field
            ArenaFieldConfig loadedField = data.fieldConfig();
            assertNotNull(loadedField);
            assertEquals("world", loadedField.worldName());
            assertEquals(0, loadedField.minX());
            assertEquals(0, loadedField.minY());
            assertEquals(0, loadedField.minZ());
            assertEquals(100, loadedField.maxX());
            assertEquals(64, loadedField.maxY());
            assertEquals(100, loadedField.maxZ());

            // team-areas（Redのみ設定済み）
            TeamAreaConfig redArea = data.teamAreaConfigs().get("Red");
            assertNotNull(redArea);
            assertEquals("world", redArea.worldName());
            assertEquals(10, redArea.minX());
            assertEquals(0, redArea.minY());
            assertEquals(10, redArea.minZ());
            assertEquals(20, redArea.maxX());
            assertEquals(5, redArea.maxY());
            assertEquals(20, redArea.maxZ());
            assertNull(data.teamAreaConfigs().get("Blue"));
        }

        @Test
        @DisplayName("fieldConfigがnullでも保存・ロードできる")
        void nullField_saveAndLoad() {
            ArenaSession session = mockSession("no_field", null);
            RegionManager rm = mockRegionManager();

            store.save("no_field", session, rm);

            ArenaPresetStore.PresetData data = store.load("no_field");
            assertNotNull(data);
            assertNull(data.fieldConfig());
        }

        @Test
        @DisplayName("カスタム名で保存できる")
        void customName_savesWithThatName() {
            ArenaSession session = mockSession("original", null);
            RegionManager rm = mockRegionManager();

            store.save("custom_name", session, rm);

            assertNull(store.load("original")); // 元の名前では見つからない
            assertNotNull(store.load("custom_name")); // カスタム名で見つかる
            assertEquals("custom_name", store.load("custom_name").name());
        }
    }

    // ========================================================================
    // ロード
    // ========================================================================

    @Nested
    @DisplayName("load - プリセット読み込み")
    class LoadTest {

        @Test
        @DisplayName("存在しないプリセット名でnullを返す")
        void nonExistent_returnsNull() {
            assertNull(store.load("does_not_exist"));
        }

        @Test
        @DisplayName("nullの名前でNPEが発生する")
        void nullName_throwsNPE() {
            assertThrows(NullPointerException.class,
                    () -> store.load(null));
        }
    }

    // ========================================================================
    // 一覧
    // ========================================================================

    @Nested
    @DisplayName("list - プリセット一覧")
    class ListTest {

        @Test
        @DisplayName("空ディレクトリで空リストを返す")
        void emptyDir_returnsEmptyList() {
            List<String> list = store.list();
            assertTrue(list.isEmpty());
        }

        @Test
        @DisplayName("保存後にプリセット名が一覧に含まれる")
        void afterSave_containsName() {
            ArenaSession session = mockSession("arena1", null);
            RegionManager rm = mockRegionManager();
            store.save("arena1", session, rm);

            List<String> list = store.list();
            assertEquals(1, list.size());
            assertEquals("arena1", list.get(0));
        }

        @Test
        @DisplayName("複数プリセットがソート済みで返される")
        void multiplePresets_sortedList() {
            RegionManager rm = mockRegionManager();

            store.save("colosseum", mockSession("colosseum", null), rm);
            store.save("arena", mockSession("arena", null), rm);
            store.save("battlefield", mockSession("battlefield", null), rm);

            List<String> list = store.list();
            assertEquals(3, list.size());
            assertEquals("arena", list.get(0));
            assertEquals("battlefield", list.get(1));
            assertEquals("colosseum", list.get(2));
        }
    }

    // ========================================================================
    // 削除
    // ========================================================================

    @Nested
    @DisplayName("delete - プリセット削除")
    class DeleteTest {

        @Test
        @DisplayName("存在するプリセットを削除するとtrueを返す")
        void existing_returnsTrue() {
            ArenaSession session = mockSession("to_delete", null);
            store.save("to_delete", session, mockRegionManager());

            assertTrue(store.delete("to_delete"));
        }

        @Test
        @DisplayName("削除後にロードするとnullを返す")
        void afterDelete_loadReturnsNull() {
            ArenaSession session = mockSession("to_delete", null);
            store.save("to_delete", session, mockRegionManager());
            store.delete("to_delete");

            assertNull(store.load("to_delete"));
        }

        @Test
        @DisplayName("削除後に一覧から消える")
        void afterDelete_removedFromList() {
            ArenaSession session = mockSession("to_delete", null);
            store.save("to_delete", session, mockRegionManager());
            store.delete("to_delete");

            assertTrue(store.list().isEmpty());
        }

        @Test
        @DisplayName("存在しないプリセットを削除するとfalseを返す")
        void nonExistent_returnsFalse() {
            assertFalse(store.delete("does_not_exist"));
        }

        @Test
        @DisplayName("nullの名前でNPEが発生する")
        void nullName_throwsNPE() {
            assertThrows(NullPointerException.class,
                    () -> store.delete(null));
        }
    }

    // ========================================================================
    // save バリデーション
    // ========================================================================

    @Nested
    @DisplayName("save バリデーション")
    class SaveValidationTest {

        @Test
        @DisplayName("nullの名前でNPEが発生する")
        void nullName_throwsNPE() {
            ArenaSession session = mockSession("test", null);
            assertThrows(NullPointerException.class,
                    () -> store.save(null, session, mockRegionManager()));
        }

        @Test
        @DisplayName("nullのセッションでNPEが発生する")
        void nullSession_throwsNPE() {
            assertThrows(NullPointerException.class,
                    () -> store.save("test", null, mockRegionManager()));
        }

        @Test
        @DisplayName("nullのRegionManagerでNPEが発生する")
        void nullRegionManager_throwsNPE() {
            ArenaSession session = mockSession("test", null);
            assertThrows(NullPointerException.class,
                    () -> store.save("test", session, null));
        }
    }
}
