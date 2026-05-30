package io.wax100.arenaCore.wincondition;

import io.wax100.arenaCore.model.ArenaSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("勝利条件テスト")
class WinConditionTest {

    // ────────────────────────────────────────────
    //  共通ヘルパー用 UUID
    // ────────────────────────────────────────────
    private static final UUID PLAYER_A1 = UUID.randomUUID();
    private static final UUID PLAYER_A2 = UUID.randomUUID();
    private static final UUID PLAYER_B1 = UUID.randomUUID();
    private static final UUID PLAYER_B2 = UUID.randomUUID();
    private static final UUID PLAYER_C1 = UUID.randomUUID();
    private static final UUID NON_FIGHTER = UUID.randomUUID();

    // ================================================================
    //  LastTeamStandingCondition
    // ================================================================
    @Nested
    @DisplayName("LastTeamStandingCondition - 全滅方式")
    class LastTeamStandingConditionTest {

        private LastTeamStandingCondition condition;

        @BeforeEach
        void setUp() {
            condition = new LastTeamStandingCondition();
        }

        // ── 2チーム × 各1名 ──

        @Nested
        @DisplayName("2チーム（各1名）")
        class TwoTeamsOneEach {

            private ArenaSession session;

            @BeforeEach
            void setUp() {
                session = new ArenaSession("test", Arrays.asList("TeamA", "TeamB"));
                session.addTeamMember("TeamA", PLAYER_A1);
                session.addTeamMember("TeamB", PLAYER_B1);
            }

            @Test
            @DisplayName("片方が脱落すれば残りチームが勝利する")
            void singleElimination_returnsWinner() {
                Set<UUID> eliminated = new HashSet<>();
                eliminated.add(PLAYER_B1);

                String winner = condition.checkWinOnDeath(session, PLAYER_B1, eliminated);

                assertEquals("TeamA", winner);
            }

            @Test
            @DisplayName("誰も脱落していなければ null を返す")
            void noElimination_returnsNull() {
                Set<UUID> eliminated = new HashSet<>();

                String winner = condition.checkWinOnDeath(session, PLAYER_A1, eliminated);

                assertNull(winner);
            }

            @Test
            @DisplayName("TeamA が脱落すれば TeamB が勝利する")
            void oppositeElimination_returnsOtherWinner() {
                Set<UUID> eliminated = new HashSet<>();
                eliminated.add(PLAYER_A1);

                String winner = condition.checkWinOnDeath(session, PLAYER_A1, eliminated);

                assertEquals("TeamB", winner);
            }
        }

        // ── 2チーム × 各2名 ──

        @Nested
        @DisplayName("2チーム（各2名）")
        class TwoTeamsTwoEach {

            private ArenaSession session;

            @BeforeEach
            void setUp() {
                session = new ArenaSession("test", Arrays.asList("TeamA", "TeamB"));
                session.addTeamMember("TeamA", PLAYER_A1);
                session.addTeamMember("TeamA", PLAYER_A2);
                session.addTeamMember("TeamB", PLAYER_B1);
                session.addTeamMember("TeamB", PLAYER_B2);
            }

            @Test
            @DisplayName("1人脱落ではまだ勝敗未定（null）")
            void oneEliminated_notYetDecided() {
                Set<UUID> eliminated = new HashSet<>();
                eliminated.add(PLAYER_A1);

                String winner = condition.checkWinOnDeath(session, PLAYER_A1, eliminated);

                assertNull(winner);
            }

            @Test
            @DisplayName("同チーム2人とも脱落で相手チームが勝利する")
            void fullTeamEliminated_returnsWinner() {
                Set<UUID> eliminated = new HashSet<>();
                eliminated.add(PLAYER_A1);
                eliminated.add(PLAYER_A2);

                String winner = condition.checkWinOnDeath(session, PLAYER_A2, eliminated);

                assertEquals("TeamB", winner);
            }

            @Test
            @DisplayName("各チーム1名ずつ脱落ではまだ勝敗未定")
            void oneFromEachTeam_notYetDecided() {
                Set<UUID> eliminated = new HashSet<>();
                eliminated.add(PLAYER_A1);
                eliminated.add(PLAYER_B1);

                String winner = condition.checkWinOnDeath(session, PLAYER_B1, eliminated);

                assertNull(winner);
            }
        }

        // ── 3チーム ──

        @Nested
        @DisplayName("3チーム")
        class ThreeTeams {

            private ArenaSession session;

            @BeforeEach
            void setUp() {
                session = new ArenaSession("test", Arrays.asList("TeamA", "TeamB", "TeamC"));
                session.addTeamMember("TeamA", PLAYER_A1);
                session.addTeamMember("TeamB", PLAYER_B1);
                session.addTeamMember("TeamC", PLAYER_C1);
            }

            @Test
            @DisplayName("1チーム脱落ではまだ2チーム残っており勝敗未定")
            void oneTeamEliminated_stillTwoRemaining() {
                Set<UUID> eliminated = new HashSet<>();
                eliminated.add(PLAYER_C1);

                String winner = condition.checkWinOnDeath(session, PLAYER_C1, eliminated);

                assertNull(winner);
            }

            @Test
            @DisplayName("2チーム脱落で残り1チームが勝利する")
            void twoTeamsEliminated_returnsWinner() {
                Set<UUID> eliminated = new HashSet<>();
                eliminated.add(PLAYER_B1);
                eliminated.add(PLAYER_C1);

                String winner = condition.checkWinOnDeath(session, PLAYER_C1, eliminated);

                assertEquals("TeamA", winner);
            }

            @Test
            @DisplayName("2チーム脱落 — TeamB が最後の生存者として勝利する")
            void twoTeamsEliminated_teamBWins() {
                Set<UUID> eliminated = new HashSet<>();
                eliminated.add(PLAYER_A1);
                eliminated.add(PLAYER_C1);

                String winner = condition.checkWinOnDeath(session, PLAYER_C1, eliminated);

                assertEquals("TeamB", winner);
            }
        }

        // ── エッジケース ──

        @Nested
        @DisplayName("エッジケース")
        class EdgeCases {

            @Test
            @DisplayName("全員が脱落している場合は null を返す")
            void allEliminated_returnsNull() {
                ArenaSession session = new ArenaSession("test", Arrays.asList("TeamA", "TeamB"));
                session.addTeamMember("TeamA", PLAYER_A1);
                session.addTeamMember("TeamB", PLAYER_B1);

                Set<UUID> eliminated = new HashSet<>();
                eliminated.add(PLAYER_A1);
                eliminated.add(PLAYER_B1);

                String winner = condition.checkWinOnDeath(session, PLAYER_B1, eliminated);

                assertNull(winner);
            }

            @Test
            @DisplayName("戦闘員でない UUID が eliminated に含まれていても影響しない")
            void nonFighterInEliminated_ignoredGracefully() {
                ArenaSession session = new ArenaSession("test", Arrays.asList("TeamA", "TeamB"));
                session.addTeamMember("TeamA", PLAYER_A1);
                session.addTeamMember("TeamB", PLAYER_B1);

                Set<UUID> eliminated = new HashSet<>();
                eliminated.add(NON_FIGHTER);
                eliminated.add(PLAYER_B1);

                String winner = condition.checkWinOnDeath(session, PLAYER_B1, eliminated);

                assertEquals("TeamA", winner);
            }
        }

        // ── インターフェースデフォルト ──

        @Test
        @DisplayName("allowsManualWin はデフォルトで true を返す")
        void allowsManualWin_defaultTrue() {
            assertTrue(condition.allowsManualWin());
        }
    }

    // ================================================================
    //  ScoreCondition
    // ================================================================
    @Nested
    @DisplayName("ScoreCondition - スコア制")
    class ScoreConditionTest {

        // ── targetScore > 0 ──

        @Nested
        @DisplayName("目標スコアあり（targetScore=3）")
        class WithTargetScore {

            private ScoreCondition condition;
            private ArenaSession session;

            @BeforeEach
            void setUp() {
                condition = new ScoreCondition(3);
                session = new ArenaSession("test", Arrays.asList("TeamA", "TeamB"));
                session.addTeamMember("TeamA", PLAYER_A1);
                session.addTeamMember("TeamB", PLAYER_B1);
            }

            @Test
            @DisplayName("キル1回目ではまだ目標未達で null を返す")
            void firstKill_doesNotReachTarget() {
                Set<UUID> eliminated = new HashSet<>();

                String winner = condition.checkWinOnDeath(session, PLAYER_A1, eliminated);

                assertNull(winner);
                assertEquals(1, session.getScore("TeamB"),
                        "TeamA の死亡で TeamB にスコア+1");
                assertEquals(0, session.getScore("TeamA"),
                        "死亡チームのスコアは変わらない");
            }

            @Test
            @DisplayName("キル3回で目標到達しチームが勝利する")
            void threeKills_reachTarget_returnsWinner() {
                Set<UUID> eliminated = new HashSet<>();

                // Kill 1 & 2: TeamA のメンバー死亡 → TeamB にスコア加算
                assertNull(condition.checkWinOnDeath(session, PLAYER_A1, eliminated));
                assertNull(condition.checkWinOnDeath(session, PLAYER_A1, eliminated));

                // Kill 3: 目標到達
                String winner = condition.checkWinOnDeath(session, PLAYER_A1, eliminated);

                assertEquals("TeamB", winner);
                assertEquals(3, session.getScore("TeamB"));
            }

            @Test
            @DisplayName("交互にキルが発生する場合は先に3点のチームが勝利")
            void alternatingKills_firstToReachTargetWins() {
                Set<UUID> eliminated = new HashSet<>();

                // A dies → B gets 1
                assertNull(condition.checkWinOnDeath(session, PLAYER_A1, eliminated));
                // B dies → A gets 1
                assertNull(condition.checkWinOnDeath(session, PLAYER_B1, eliminated));
                // A dies → B gets 2
                assertNull(condition.checkWinOnDeath(session, PLAYER_A1, eliminated));
                // B dies → A gets 2
                assertNull(condition.checkWinOnDeath(session, PLAYER_B1, eliminated));
                // A dies → B gets 3 → B wins
                String winner = condition.checkWinOnDeath(session, PLAYER_A1, eliminated);

                assertEquals("TeamB", winner);
                assertEquals(3, session.getScore("TeamB"));
                assertEquals(2, session.getScore("TeamA"));
            }
        }

        // ── targetScore = 0 ──

        @Nested
        @DisplayName("目標スコアなし（targetScore=0）")
        class NoTargetScore {

            private ScoreCondition condition;
            private ArenaSession session;

            @BeforeEach
            void setUp() {
                condition = new ScoreCondition(0);
                session = new ArenaSession("test", Arrays.asList("TeamA", "TeamB"));
                session.addTeamMember("TeamA", PLAYER_A1);
                session.addTeamMember("TeamB", PLAYER_B1);
            }

            @Test
            @DisplayName("何回キルしても自動勝利は発生しない")
            void manyKills_neverReturnsWinner() {
                Set<UUID> eliminated = new HashSet<>();

                for (int i = 0; i < 100; i++) {
                    assertNull(condition.checkWinOnDeath(session, PLAYER_A1, eliminated));
                }

                assertEquals(100, session.getScore("TeamB"),
                        "スコアは正しく加算される");
            }

            @Test
            @DisplayName("スコアは加算されるが勝者は返さない")
            void scoresAccumulate_butNoWinner() {
                Set<UUID> eliminated = new HashSet<>();

                condition.checkWinOnDeath(session, PLAYER_A1, eliminated);
                condition.checkWinOnDeath(session, PLAYER_B1, eliminated);
                condition.checkWinOnDeath(session, PLAYER_A1, eliminated);

                assertAll(
                        () -> assertEquals(2, session.getScore("TeamB")),
                        () -> assertEquals(1, session.getScore("TeamA"))
                );
            }
        }

        // ── 戦闘員でないプレイヤーの死亡 ──

        @Nested
        @DisplayName("非戦闘員の死亡")
        class NonFighterDeath {

            @Test
            @DisplayName("戦闘員でないプレイヤーが死亡しても null を返しスコアは変わらない")
            void nonFighterDeath_returnsNull_noScoreChange() {
                ScoreCondition condition = new ScoreCondition(1);
                ArenaSession session = new ArenaSession("test", Arrays.asList("TeamA", "TeamB"));
                session.addTeamMember("TeamA", PLAYER_A1);
                session.addTeamMember("TeamB", PLAYER_B1);

                Set<UUID> eliminated = new HashSet<>();

                String winner = condition.checkWinOnDeath(session, NON_FIGHTER, eliminated);

                assertNull(winner);
                assertEquals(0, session.getScore("TeamA"));
                assertEquals(0, session.getScore("TeamB"));
            }
        }

        // ── 3チームでのスコア制 ──

        @Nested
        @DisplayName("3チームでのスコア制")
        class ThreeTeamsScoring {

            private ScoreCondition condition;
            private ArenaSession session;

            @BeforeEach
            void setUp() {
                condition = new ScoreCondition(2);
                session = new ArenaSession("test", Arrays.asList("TeamA", "TeamB", "TeamC"));
                session.addTeamMember("TeamA", PLAYER_A1);
                session.addTeamMember("TeamB", PLAYER_B1);
                session.addTeamMember("TeamC", PLAYER_C1);
            }

            @Test
            @DisplayName("1チーム死亡で他の2チームにスコアが加算される")
            void deathAddsScoreToAllOtherTeams() {
                Set<UUID> eliminated = new HashSet<>();

                condition.checkWinOnDeath(session, PLAYER_A1, eliminated);

                assertEquals(0, session.getScore("TeamA"));
                assertEquals(1, session.getScore("TeamB"));
                assertEquals(1, session.getScore("TeamC"));
            }

            @Test
            @DisplayName("目標到達した最初のチームが勝利する")
            void firstTeamToReachTarget_wins() {
                Set<UUID> eliminated = new HashSet<>();

                // A dies → B=1, C=1
                assertNull(condition.checkWinOnDeath(session, PLAYER_A1, eliminated));
                // A dies → B=2, C=2 → first checked team wins
                String winner = condition.checkWinOnDeath(session, PLAYER_A1, eliminated);

                // TeamB or TeamC should win (whichever is iterated first)
                assertNotNull(winner);
                assertTrue(winner.equals("TeamB") || winner.equals("TeamC"),
                        "TeamB または TeamC のいずれかが勝利するはず");
            }
        }

        // ── targetScore=1 の即時勝利 ──

        @Test
        @DisplayName("targetScore=1 なら最初のキルで即座に勝利が確定する")
        void targetScoreOne_immediateWin() {
            ScoreCondition condition = new ScoreCondition(1);
            ArenaSession session = new ArenaSession("test", Arrays.asList("TeamA", "TeamB"));
            session.addTeamMember("TeamA", PLAYER_A1);
            session.addTeamMember("TeamB", PLAYER_B1);

            Set<UUID> eliminated = new HashSet<>();

            String winner = condition.checkWinOnDeath(session, PLAYER_A1, eliminated);

            assertEquals("TeamB", winner);
            assertEquals(1, session.getScore("TeamB"));
        }

        // ── インターフェースデフォルト ──

        @Test
        @DisplayName("allowsManualWin はデフォルトで true を返す")
        void allowsManualWin_defaultTrue() {
            ScoreCondition condition = new ScoreCondition(5);
            assertTrue(condition.allowsManualWin());
        }
    }

    // ================================================================
    //  ManualDeclarationCondition
    // ================================================================
    @Nested
    @DisplayName("ManualDeclarationCondition - 手動宣言方式")
    class ManualDeclarationConditionTest {

        private ManualDeclarationCondition condition;

        @BeforeEach
        void setUp() {
            condition = new ManualDeclarationCondition();
        }

        @Test
        @DisplayName("checkWinOnDeath は常に null を返す")
        void checkWinOnDeath_alwaysReturnsNull() {
            ArenaSession session = new ArenaSession("test", Arrays.asList("TeamA", "TeamB"));
            session.addTeamMember("TeamA", PLAYER_A1);
            session.addTeamMember("TeamB", PLAYER_B1);

            Set<UUID> eliminated = new HashSet<>();

            assertNull(condition.checkWinOnDeath(session, PLAYER_A1, eliminated));
        }

        @Test
        @DisplayName("全員脱落しても null を返す — 自動判定は行わない")
        void allEliminated_stillReturnsNull() {
            ArenaSession session = new ArenaSession("test", Arrays.asList("TeamA", "TeamB"));
            session.addTeamMember("TeamA", PLAYER_A1);
            session.addTeamMember("TeamB", PLAYER_B1);

            Set<UUID> eliminated = new HashSet<>();
            eliminated.add(PLAYER_A1);
            eliminated.add(PLAYER_B1);

            assertNull(condition.checkWinOnDeath(session, PLAYER_B1, eliminated));
        }

        @Test
        @DisplayName("何度呼んでも常に null を返す")
        void multipleInvocations_alwaysNull() {
            ArenaSession session = new ArenaSession("test", Arrays.asList("TeamA", "TeamB"));
            session.addTeamMember("TeamA", PLAYER_A1);
            session.addTeamMember("TeamB", PLAYER_B1);

            Set<UUID> eliminated = new HashSet<>();

            for (int i = 0; i < 10; i++) {
                eliminated.add(UUID.randomUUID());
                assertNull(condition.checkWinOnDeath(session, PLAYER_A1, eliminated),
                        "呼び出し " + (i + 1) + " 回目でも null");
            }
        }

        @Test
        @DisplayName("非戦闘員の死亡でも null を返す")
        void nonFighterDeath_returnsNull() {
            ArenaSession session = new ArenaSession("test", Arrays.asList("TeamA", "TeamB"));
            session.addTeamMember("TeamA", PLAYER_A1);
            session.addTeamMember("TeamB", PLAYER_B1);

            Set<UUID> eliminated = new HashSet<>();

            assertNull(condition.checkWinOnDeath(session, NON_FIGHTER, eliminated));
        }

        @Test
        @DisplayName("allowsManualWin は true を返す")
        void allowsManualWin_returnsTrue() {
            assertTrue(condition.allowsManualWin());
        }

        @Test
        @DisplayName("WinCondition インターフェースを実装している")
        void implementsWinConditionInterface() {
            assertInstanceOf(WinCondition.class, condition);
        }
    }
}
