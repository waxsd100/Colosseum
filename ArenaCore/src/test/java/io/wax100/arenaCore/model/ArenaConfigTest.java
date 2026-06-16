package io.wax100.arenaCore.model;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("ArenaConfig: 闘技場セッション固有の設定")
class ArenaConfigTest {

    // ========================================================================
    // コンストラクタ（FileConfiguration）
    // ========================================================================

    @Nested
    @DisplayName("コンストラクタ（FileConfiguration）")
    class FileConfigConstructorTest {

        @Test
        @DisplayName("グローバル設定からデフォルト値が読み込まれる")
        void loadsDefaultsFromGlobalConfig() {
            FileConfiguration config = mock(FileConfiguration.class);
            when(config.getLong("entry-fee", 0)).thenReturn(500L);
            when(config.getString("win-condition", "last-team-standing")).thenReturn("score");
            when(config.getInt("score-target", 0)).thenReturn(10);
            when(config.getLong("fighter-guarantee", 100)).thenReturn(200L);

            ArenaConfig ac = new ArenaConfig(config);
            assertEquals(500L, ac.getEntryFee());
            assertEquals("score", ac.getWinCondition());
            assertEquals(10, ac.getScoreTarget());
            assertEquals(200L, ac.getFighterGuarantee());
        }

        @Test
        @DisplayName("nullのFileConfigurationでNPEが発生する")
        void nullConfig_throwsNPE() {
            assertThrows(NullPointerException.class, () -> new ArenaConfig((FileConfiguration) null));
        }
    }

    // ========================================================================
    // コンストラクタ（全引数）
    // ========================================================================

    @Nested
    @DisplayName("コンストラクタ（全引数）")
    class AllArgsConstructorTest {

        @Test
        @DisplayName("すべての値が正しく設定される")
        void setsAllValues() {
            ArenaConfig ac = new ArenaConfig(1000L, "manual", 5, 300L);
            assertEquals(1000L, ac.getEntryFee());
            assertEquals("manual", ac.getWinCondition());
            assertEquals(5, ac.getScoreTarget());
            assertEquals(300L, ac.getFighterGuarantee());
        }

        @Test
        @DisplayName("nullのwinConditionはデフォルトに置換される")
        void nullWinCondition_usesDefault() {
            ArenaConfig ac = new ArenaConfig(0L, null, 0, 0L);
            assertEquals("last-team-standing", ac.getWinCondition());
        }
    }

    // ========================================================================
    // Setter
    // ========================================================================

    @Nested
    @DisplayName("Setter")
    class SetterTest {

        private ArenaConfig config;

        @BeforeEach
        void setUp() {
            config = new ArenaConfig(0L, "last-team-standing", 0, 100L);
        }

        @Test
        @DisplayName("参加費を設定できる")
        void setEntryFee() {
            config.setEntryFee(500L);
            assertEquals(500L, config.getEntryFee());
        }

        @Test
        @DisplayName("負の参加費は0にクランプされる")
        void negativeEntryFee_clampedToZero() {
            config.setEntryFee(-100L);
            assertEquals(0L, config.getEntryFee());
        }

        @Test
        @DisplayName("有効な勝利条件を設定するとtrueを返す")
        void validWinCondition_returnsTrue() {
            assertTrue(config.setWinCondition("last-team-standing"));
            assertEquals("last-team-standing", config.getWinCondition());
            assertTrue(config.setWinCondition("manual"));
            assertEquals("manual", config.getWinCondition());
            assertTrue(config.setWinCondition("score"));
            assertEquals("score", config.getWinCondition());
        }

        @Test
        @DisplayName("大文字の勝利条件も受け入れる")
        void upperCaseWinCondition_accepted() {
            assertTrue(config.setWinCondition("MANUAL"));
            assertEquals("manual", config.getWinCondition());
        }

        @Test
        @DisplayName("無効な勝利条件でfalseを返す")
        void invalidWinCondition_returnsFalse() {
            assertFalse(config.setWinCondition("invalid"));
            assertEquals("last-team-standing", config.getWinCondition());
        }

        @Test
        @DisplayName("nullの勝利条件でfalseを返す")
        void nullWinCondition_returnsFalse() {
            assertFalse(config.setWinCondition(null));
        }

        @Test
        @DisplayName("スコア目標を設定できる")
        void setScoreTarget() {
            config.setScoreTarget(15);
            assertEquals(15, config.getScoreTarget());
        }

        @Test
        @DisplayName("負のスコア目標は0にクランプされる")
        void negativeScoreTarget_clampedToZero() {
            config.setScoreTarget(-5);
            assertEquals(0, config.getScoreTarget());
        }

        @Test
        @DisplayName("闘技者保証金を設定できる")
        void setFighterGuarantee() {
            config.setFighterGuarantee(250L);
            assertEquals(250L, config.getFighterGuarantee());
        }

        @Test
        @DisplayName("負の闘技者保証金は0にクランプされる")
        void negativeFighterGuarantee_clampedToZero() {
            config.setFighterGuarantee(-50L);
            assertEquals(0L, config.getFighterGuarantee());
        }
    }

    // ========================================================================
    // 表示名
    // ========================================================================

    @Nested
    @DisplayName("getWinConditionDisplayName")
    class DisplayNameTest {

        @Test
        @DisplayName("全滅方式の表示名")
        void lastTeamStanding_displayName() {
            ArenaConfig ac = new ArenaConfig(0L, "last-team-standing", 0, 0L);
            assertEquals("全滅方式", ac.getWinConditionDisplayName());
        }

        @Test
        @DisplayName("手動宣言の表示名")
        void manual_displayName() {
            ArenaConfig ac = new ArenaConfig(0L, "manual", 0, 0L);
            assertEquals("手動宣言", ac.getWinConditionDisplayName());
        }

        @Test
        @DisplayName("スコア制の表示名にスコア目標が含まれる")
        void score_displayNameIncludesTarget() {
            ArenaConfig ac = new ArenaConfig(0L, "score", 10, 0L);
            assertTrue(ac.getWinConditionDisplayName().contains("スコア制"));
            assertTrue(ac.getWinConditionDisplayName().contains("10"));
        }
    }

    // ========================================================================
    // YAML ラウンドトリップ
    // ========================================================================

    @Nested
    @DisplayName("YAML シリアライズ")
    class YamlTest {

        @Test
        @DisplayName("toYaml → fromYaml で値が保持される")
        void roundTrip_preservesValues() {
            ArenaConfig original = new ArenaConfig(500L, "score", 10, 200L);

            YamlConfiguration yaml = new YamlConfiguration();
            original.toYaml(yaml, "config");

            ArenaConfig restored = ArenaConfig.fromYaml(
                    yaml.getConfigurationSection("config"));

            assertEquals(original.getEntryFee(), restored.getEntryFee());
            assertEquals(original.getWinCondition(), restored.getWinCondition());
            assertEquals(original.getScoreTarget(), restored.getScoreTarget());
            assertEquals(original.getFighterGuarantee(), restored.getFighterGuarantee());
        }

        @Test
        @DisplayName("空のbasePath でも動作する")
        void emptyBasePath_works() {
            ArenaConfig original = new ArenaConfig(100L, "manual", 0, 50L);

            YamlConfiguration yaml = new YamlConfiguration();
            original.toYaml(yaml, "");

            ArenaConfig restored = ArenaConfig.fromYaml(yaml);
            assertEquals(100L, restored.getEntryFee());
            assertEquals("manual", restored.getWinCondition());
        }

        @Test
        @DisplayName("fromYaml でキーが存在しない場合はデフォルト値が使用される")
        void missingKeys_usesDefaults() {
            YamlConfiguration yaml = new YamlConfiguration();
            // 空のセクションを作成
            yaml.createSection("empty");

            ArenaConfig restored = ArenaConfig.fromYaml(
                    yaml.getConfigurationSection("empty"));

            assertEquals(0L, restored.getEntryFee());
            assertEquals("last-team-standing", restored.getWinCondition());
            assertEquals(0, restored.getScoreTarget());
            assertEquals(100L, restored.getFighterGuarantee());
        }

        @Test
        @DisplayName("fromYaml でnullセクションはNPEをスローする")
        void nullSection_throwsNPE() {
            assertThrows(NullPointerException.class, () -> ArenaConfig.fromYaml(null));
        }
    }
}
