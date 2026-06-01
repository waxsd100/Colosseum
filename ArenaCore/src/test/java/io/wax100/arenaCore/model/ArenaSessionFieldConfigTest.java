package io.wax100.arenaCore.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ArenaSession: fieldConfig (戦闘エリア設定) 管理")
class ArenaSessionFieldConfigTest {

    private static final String SESSION_NAME = "TestArena";
    private static final List<String> TWO_TEAMS = List.of("Red", "Blue");

    private ArenaSession session;

    @BeforeEach
    void setUp() {
        session = new ArenaSession(SESSION_NAME, TWO_TEAMS);
    }

    // ========================================================================
    // 初期状態
    // ========================================================================

    @Nested
    @DisplayName("初期状態")
    class InitialStateTest {

        @Test
        @DisplayName("fieldConfigがnullで初期化される")
        void initialFieldConfig_isNull() {
            assertNull(session.getFieldConfig());
        }
    }

    // ========================================================================
    // setter / getter
    // ========================================================================

    @Nested
    @DisplayName("setFieldConfig / getFieldConfig")
    class SetGetTest {

        @Test
        @DisplayName("設定した値が取得できる")
        void setAndGet() {
            ArenaFieldConfig field = ArenaFieldConfig.of("world", 0, 0, 0, 100, 64, 100);
            session.setFieldConfig(field);
            assertSame(field, session.getFieldConfig());
        }

        @Test
        @DisplayName("異なる設定で上書きできる")
        void overwrite() {
            ArenaFieldConfig first = ArenaFieldConfig.of("world", 0, 0, 0, 10, 10, 10);
            ArenaFieldConfig second = ArenaFieldConfig.of("world", -50, 0, -50, 50, 100, 50);
            session.setFieldConfig(first);
            session.setFieldConfig(second);
            assertSame(second, session.getFieldConfig());
        }

        @Test
        @DisplayName("nullを設定してリセットできる")
        void setNull_resetsToNull() {
            session.setFieldConfig(ArenaFieldConfig.of("world", 0, 0, 0, 10, 10, 10));
            session.setFieldConfig(null);
            assertNull(session.getFieldConfig());
        }
    }

    // ========================================================================
    // clearAllData
    // ========================================================================

    @Nested
    @DisplayName("clearAllData")
    class ClearAllDataTest {

        @Test
        @DisplayName("clearAllData後にfieldConfigがnullになる")
        void clearAllData_resetsFieldConfig() {
            session.setFieldConfig(ArenaFieldConfig.of("world", 0, 0, 0, 100, 64, 100));

            // clearAllDataを呼ぶにはFINISHED状態が必要
            session.setState(ArenaState.RECRUITING);
            session.setState(ArenaState.BETTING);
            session.setState(ArenaState.CLOSED);
            session.setState(ArenaState.ACTIVE);
            session.setState(ArenaState.FINISHED);
            session.clearAllData();

            assertNull(session.getFieldConfig());
        }
    }

    // ========================================================================
    // 値の独立性
    // ========================================================================

    @Nested
    @DisplayName("値の独立性")
    class IndependenceTest {

        @Test
        @DisplayName("fieldConfigは他のセッションデータと独立している")
        void fieldConfig_independentOfOtherData() {
            ArenaFieldConfig field = ArenaFieldConfig.of("world", 0, 0, 0, 50, 50, 50);
            session.setFieldConfig(field);

            // チーム操作がfieldConfigに影響しない
            session.addTeamMember("Red", java.util.UUID.randomUUID());
            session.addScore("Red", 10);

            assertSame(field, session.getFieldConfig());
        }

        @Test
        @DisplayName("同じArenaFieldConfigを複数セッションに設定できる")
        void sameConfig_onMultipleSessions() {
            ArenaFieldConfig field = ArenaFieldConfig.of("world", 0, 0, 0, 100, 64, 100);

            ArenaSession session2 = new ArenaSession("Arena2", TWO_TEAMS);
            session.setFieldConfig(field);
            session2.setFieldConfig(field);

            assertSame(field, session.getFieldConfig());
            assertSame(field, session2.getFieldConfig());
        }
    }
}
