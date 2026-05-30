package io.wax100.arenaCore.payout;

import io.wax100.arenaCore.model.ArenaSession;
import io.wax100.arenaCore.model.Bet;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("配当戦略テスト")
class PayoutStrategyTest {

    // ── 共通定数 ──

    private static final UUID PLAYER_A1 = UUID.randomUUID();
    private static final UUID PLAYER_A2 = UUID.randomUUID();
    private static final UUID PLAYER_B1 = UUID.randomUUID();
    private static final UUID PLAYER_B2 = UUID.randomUUID();
    private static final UUID PLAYER_C1 = UUID.randomUUID();

    private static final double DELTA = 0.01;

    // =========================================================================
    // 1. PariMutuelPayout
    // =========================================================================

    @Nested
    @DisplayName("パリミュチュエル方式")
    class PariMutuelPayoutTest {

        private PariMutuelPayout strategy;

        @BeforeEach
        void setUp() {
            strategy = new PariMutuelPayout();
        }

        // ── calculatePayouts ──

        @Nested
        @DisplayName("calculatePayouts")
        class CalculatePayouts {

            @Test
            @DisplayName("基本: 2チーム・houseEdge=0.1 で勝利チームに按分される")
            void basicTwoTeam_houseEdge10Percent() {
                // totalPool=15000, payoutPool=13500, winningPool=10000
                // odds=13500/10000=1.35, A1 payout=10000*1.35=13500
                ArenaSession session = new ArenaSession("test", Arrays.asList("TeamA", "TeamB"));
                session.addOrUpdateBet(PLAYER_A1, "TeamA", 10000);
                session.addOrUpdateBet(PLAYER_B1, "TeamB", 5000);

                Map<UUID, Long> payouts = strategy.calculatePayouts(session, "TeamA", 0.1);

                assertEquals(1, payouts.size());
                assertEquals(13500L, payouts.get(PLAYER_A1));
                assertNull(payouts.get(PLAYER_B1));
            }

            @Test
            @DisplayName("複数の勝者が賭け額に応じて按分される")
            void multipleWinners_proportionalPayout() {
                // totalPool=20000, payoutPool=18000, winningPool=15000
                // odds=18000/15000=1.2
                // A1: 10000*1.2=12000, A2: 5000*1.2=6000
                ArenaSession session = new ArenaSession("test", Arrays.asList("TeamA", "TeamB"));
                session.addOrUpdateBet(PLAYER_A1, "TeamA", 10000);
                session.addOrUpdateBet(PLAYER_A2, "TeamA", 5000);
                session.addOrUpdateBet(PLAYER_B1, "TeamB", 5000);

                Map<UUID, Long> payouts = strategy.calculatePayouts(session, "TeamA", 0.1);

                assertEquals(2, payouts.size());
                assertEquals(12000L, payouts.get(PLAYER_A1));
                assertEquals(6000L, payouts.get(PLAYER_A2));
            }

            @Test
            @DisplayName("全員が勝利チームに賭けた場合、手数料分だけ減る")
            void allBetsOnWinner_payoutReduced() {
                // totalPool=15000, payoutPool=13500, winningPool=15000
                // odds=13500/15000=0.9
                // A1: 10000*0.9=9000, A2: 5000*0.9=4500
                ArenaSession session = new ArenaSession("test", Arrays.asList("TeamA", "TeamB"));
                session.addOrUpdateBet(PLAYER_A1, "TeamA", 10000);
                session.addOrUpdateBet(PLAYER_A2, "TeamA", 5000);

                Map<UUID, Long> payouts = strategy.calculatePayouts(session, "TeamA", 0.1);

                assertEquals(2, payouts.size());
                assertEquals(9000L, payouts.get(PLAYER_A1));
                assertEquals(4500L, payouts.get(PLAYER_A2));
            }

            @Test
            @DisplayName("賭けが存在しない場合、空マップを返す")
            void noBets_returnsEmpty() {
                ArenaSession session = new ArenaSession("test", Arrays.asList("TeamA", "TeamB"));

                Map<UUID, Long> payouts = strategy.calculatePayouts(session, "TeamA", 0.1);

                assertTrue(payouts.isEmpty());
            }

            @Test
            @DisplayName("勝利チームへの賭けが0の場合、空マップを返す")
            void emptyWinningPool_returnsEmpty() {
                ArenaSession session = new ArenaSession("test", Arrays.asList("TeamA", "TeamB"));
                session.addOrUpdateBet(PLAYER_B1, "TeamB", 5000);

                Map<UUID, Long> payouts = strategy.calculatePayouts(session, "TeamA", 0.1);

                assertTrue(payouts.isEmpty());
            }

            @Test
            @DisplayName("houseEdge=0.0 の場合、プール全額が配当される")
            void houseEdgeZero_fullPoolPaid() {
                // totalPool=15000, payoutPool=15000, winningPool=10000
                // odds=1.5, payout=10000*1.5=15000
                ArenaSession session = new ArenaSession("test", Arrays.asList("TeamA", "TeamB"));
                session.addOrUpdateBet(PLAYER_A1, "TeamA", 10000);
                session.addOrUpdateBet(PLAYER_B1, "TeamB", 5000);

                Map<UUID, Long> payouts = strategy.calculatePayouts(session, "TeamA", 0.0);

                assertEquals(15000L, payouts.get(PLAYER_A1));
            }

            @Test
            @DisplayName("houseEdge=1.0 の場合、配当は0になる")
            void houseEdgeOne_zeroPayout() {
                // payoutPool=0
                ArenaSession session = new ArenaSession("test", Arrays.asList("TeamA", "TeamB"));
                session.addOrUpdateBet(PLAYER_A1, "TeamA", 10000);
                session.addOrUpdateBet(PLAYER_B1, "TeamB", 5000);

                Map<UUID, Long> payouts = strategy.calculatePayouts(session, "TeamA", 1.0);

                assertEquals(0L, payouts.get(PLAYER_A1));
            }

            @Test
            @DisplayName("3チーム構成でも正しく計算される")
            void threeTeams_correctCalculation() {
                // totalPool=30000, payoutPool=27000, winningPool=10000
                // odds=2.7, A1 payout=10000*2.7=27000
                ArenaSession session = new ArenaSession("test", Arrays.asList("TeamA", "TeamB", "TeamC"));
                session.addOrUpdateBet(PLAYER_A1, "TeamA", 10000);
                session.addOrUpdateBet(PLAYER_B1, "TeamB", 8000);
                session.addOrUpdateBet(PLAYER_C1, "TeamC", 12000);

                Map<UUID, Long> payouts = strategy.calculatePayouts(session, "TeamA", 0.1);

                assertEquals(1, payouts.size());
                assertEquals(27000L, payouts.get(PLAYER_A1));
            }
        }

        // ── calculateOdds ──

        @Nested
        @DisplayName("calculateOdds")
        class CalculateOdds {

            @Test
            @DisplayName("基本: payoutPool/teamPool でオッズを返す")
            void basicOdds() {
                // totalPool=15000, payoutPool=13500, teamPool=10000
                // odds=13500/10000=1.35
                ArenaSession session = new ArenaSession("test", Arrays.asList("TeamA", "TeamB"));
                session.addOrUpdateBet(PLAYER_A1, "TeamA", 10000);
                session.addOrUpdateBet(PLAYER_B1, "TeamB", 5000);

                double odds = strategy.calculateOdds(session, "TeamA", 0.1);

                assertEquals(1.35, odds, DELTA);
            }

            @Test
            @DisplayName("少額チームほどオッズが高くなる")
            void smallerTeam_higherOdds() {
                ArenaSession session = new ArenaSession("test", Arrays.asList("TeamA", "TeamB"));
                session.addOrUpdateBet(PLAYER_A1, "TeamA", 10000);
                session.addOrUpdateBet(PLAYER_B1, "TeamB", 5000);

                double oddsA = strategy.calculateOdds(session, "TeamA", 0.1);
                double oddsB = strategy.calculateOdds(session, "TeamB", 0.1);

                // TeamB is smaller pool => higher odds
                assertTrue(oddsB > oddsA);
                assertEquals(1.35, oddsA, DELTA);
                assertEquals(2.70, oddsB, DELTA);
            }

            @Test
            @DisplayName("賭けなしの場合、0.0 を返す")
            void noBets_returnsZero() {
                ArenaSession session = new ArenaSession("test", Arrays.asList("TeamA", "TeamB"));

                assertEquals(0.0, strategy.calculateOdds(session, "TeamA", 0.1), DELTA);
            }

            @Test
            @DisplayName("該当チームに賭けがない場合、0.0 を返す")
            void noTeamBets_returnsZero() {
                ArenaSession session = new ArenaSession("test", Arrays.asList("TeamA", "TeamB"));
                session.addOrUpdateBet(PLAYER_B1, "TeamB", 5000);

                assertEquals(0.0, strategy.calculateOdds(session, "TeamA", 0.1), DELTA);
            }

            @Test
            @DisplayName("houseEdge=0.0 なら totalPool/teamPool")
            void houseEdgeZero() {
                ArenaSession session = new ArenaSession("test", Arrays.asList("TeamA", "TeamB"));
                session.addOrUpdateBet(PLAYER_A1, "TeamA", 10000);
                session.addOrUpdateBet(PLAYER_B1, "TeamB", 5000);

                // 15000/10000=1.5
                assertEquals(1.5, strategy.calculateOdds(session, "TeamA", 0.0), DELTA);
            }
        }
    }

    // =========================================================================
    // 2. FixedOddsPayout
    // =========================================================================

    @Nested
    @DisplayName("固定オッズ方式")
    class FixedOddsPayoutTest {

        private FixedOddsPayout strategy;

        @BeforeEach
        void setUp() {
            strategy = new FixedOddsPayout();
        }

        // ── calculatePayouts ──

        @Nested
        @DisplayName("calculatePayouts")
        class CalculatePayouts {

            @Test
            @DisplayName("ロック済みオッズがある場合、そのオッズで計算される")
            void lockedOdds_usedForPayout() {
                ArenaSession session = new ArenaSession("test", Arrays.asList("TeamA", "TeamB"));
                session.addOrUpdateBet(PLAYER_A1, "TeamA", 10000);
                session.addOrUpdateBet(PLAYER_B1, "TeamB", 5000);

                // Lock odds at 2.5 for PLAYER_A1
                session.getBet(PLAYER_A1).setLockedOdds(2.5);

                Map<UUID, Long> payouts = strategy.calculatePayouts(session, "TeamA", 0.1);

                // 10000 * 2.5 = 25000
                assertEquals(25000L, payouts.get(PLAYER_A1));
            }

            @Test
            @DisplayName("ロック済みオッズが0の場合、パリミュチュエル式にフォールバックする")
            void noLockedOdds_fallsBackToPariMutuel() {
                // lockedOdds defaults to 0.0 => fallback
                // totalPool=15000, payoutPool=13500, winningPool=10000
                // odds=1.35, payout=10000*1.35=13500
                ArenaSession session = new ArenaSession("test", Arrays.asList("TeamA", "TeamB"));
                session.addOrUpdateBet(PLAYER_A1, "TeamA", 10000);
                session.addOrUpdateBet(PLAYER_B1, "TeamB", 5000);

                Map<UUID, Long> payouts = strategy.calculatePayouts(session, "TeamA", 0.1);

                assertEquals(13500L, payouts.get(PLAYER_A1));
            }

            @Test
            @DisplayName("ロック済みと未ロックが混在する場合、各自の方式で計算される")
            void mixedLockedAndUnlocked() {
                // A1: locked at 3.0 => 10000*3.0=30000
                // A2: not locked => fallback to pariMutuel
                // totalPool=20000, payoutPool=18000, winningPool=15000
                // odds=1.2, A2 payout=5000*1.2=6000
                ArenaSession session = new ArenaSession("test", Arrays.asList("TeamA", "TeamB"));
                session.addOrUpdateBet(PLAYER_A1, "TeamA", 10000);
                session.addOrUpdateBet(PLAYER_A2, "TeamA", 5000);
                session.addOrUpdateBet(PLAYER_B1, "TeamB", 5000);

                session.getBet(PLAYER_A1).setLockedOdds(3.0);
                // A2 remains unlocked (0.0)

                Map<UUID, Long> payouts = strategy.calculatePayouts(session, "TeamA", 0.1);

                assertEquals(2, payouts.size());
                assertEquals(30000L, payouts.get(PLAYER_A1));
                assertEquals(6000L, payouts.get(PLAYER_A2));
            }

            @Test
            @DisplayName("全員ロック済みオッズの場合、houseEdge に影響されない")
            void allLocked_houseEdgeIgnored() {
                ArenaSession session = new ArenaSession("test", Arrays.asList("TeamA", "TeamB"));
                session.addOrUpdateBet(PLAYER_A1, "TeamA", 10000);
                session.addOrUpdateBet(PLAYER_B1, "TeamB", 5000);

                session.getBet(PLAYER_A1).setLockedOdds(2.0);

                Map<UUID, Long> payoutsLowEdge = strategy.calculatePayouts(session, "TeamA", 0.0);
                Map<UUID, Long> payoutsHighEdge = strategy.calculatePayouts(session, "TeamA", 0.5);

                // Locked odds ignore houseEdge, both should produce same result
                assertEquals(payoutsLowEdge.get(PLAYER_A1), payoutsHighEdge.get(PLAYER_A1));
                assertEquals(20000L, payoutsLowEdge.get(PLAYER_A1));
            }

            @Test
            @DisplayName("賭けが存在しない場合、空マップを返す")
            void noBets_returnsEmpty() {
                ArenaSession session = new ArenaSession("test", Arrays.asList("TeamA", "TeamB"));

                Map<UUID, Long> payouts = strategy.calculatePayouts(session, "TeamA", 0.1);

                assertTrue(payouts.isEmpty());
            }

            @Test
            @DisplayName("勝利チームへの賭けがない場合、空マップを返す")
            void noWinnerBets_returnsEmpty() {
                ArenaSession session = new ArenaSession("test", Arrays.asList("TeamA", "TeamB"));
                session.addOrUpdateBet(PLAYER_B1, "TeamB", 5000);

                Map<UUID, Long> payouts = strategy.calculatePayouts(session, "TeamA", 0.1);

                assertTrue(payouts.isEmpty());
            }

            @Test
            @DisplayName("負のロックオッズは無効として扱いフォールバックする")
            void negativeLockedOdds_fallsBack() {
                ArenaSession session = new ArenaSession("test", Arrays.asList("TeamA", "TeamB"));
                session.addOrUpdateBet(PLAYER_A1, "TeamA", 10000);
                session.addOrUpdateBet(PLAYER_B1, "TeamB", 5000);

                session.getBet(PLAYER_A1).setLockedOdds(-1.0);

                Map<UUID, Long> payouts = strategy.calculatePayouts(session, "TeamA", 0.1);

                // Falls back to PariMutuel: 13500
                assertEquals(13500L, payouts.get(PLAYER_A1));
            }
        }

        // ── calculateOdds ──

        @Nested
        @DisplayName("calculateOdds")
        class CalculateOdds {

            @Test
            @DisplayName("パリミュチュエルと同じ計算式でオッズを返す")
            void sameAsPariMutuel() {
                ArenaSession session = new ArenaSession("test", Arrays.asList("TeamA", "TeamB"));
                session.addOrUpdateBet(PLAYER_A1, "TeamA", 10000);
                session.addOrUpdateBet(PLAYER_B1, "TeamB", 5000);

                PariMutuelPayout pariMutuel = new PariMutuelPayout();

                double fixedOdds = strategy.calculateOdds(session, "TeamA", 0.1);
                double pariOdds = pariMutuel.calculateOdds(session, "TeamA", 0.1);

                assertEquals(pariOdds, fixedOdds, DELTA);
                assertEquals(1.35, fixedOdds, DELTA);
            }

            @Test
            @DisplayName("賭けなしの場合、0.0 を返す")
            void noBets_returnsZero() {
                ArenaSession session = new ArenaSession("test", Arrays.asList("TeamA", "TeamB"));

                assertEquals(0.0, strategy.calculateOdds(session, "TeamA", 0.1), DELTA);
            }

            @Test
            @DisplayName("該当チームに賭けがない場合、0.0 を返す")
            void noTeamBets_returnsZero() {
                ArenaSession session = new ArenaSession("test", Arrays.asList("TeamA", "TeamB"));
                session.addOrUpdateBet(PLAYER_B1, "TeamB", 5000);

                assertEquals(0.0, strategy.calculateOdds(session, "TeamA", 0.1), DELTA);
            }
        }
    }

    // =========================================================================
    // 3. SimpleRedistributionPayout
    // =========================================================================

    @Nested
    @DisplayName("単純再分配方式")
    class SimpleRedistributionPayoutTest {

        private SimpleRedistributionPayout strategy;

        @BeforeEach
        void setUp() {
            strategy = new SimpleRedistributionPayout();
        }

        // ── calculatePayouts ──

        @Nested
        @DisplayName("calculatePayouts")
        class CalculatePayouts {

            @Test
            @DisplayName("基本: 元金＋敗者プールの再分配を受け取る")
            void basicRedistribution() {
                // totalPool=15000, winningPool=10000, losingPool=5000
                // redistributable=5000*0.9=4500
                // A1: 10000 + 4500*(10000/10000) = 10000 + 4500 = 14500
                ArenaSession session = new ArenaSession("test", Arrays.asList("TeamA", "TeamB"));
                session.addOrUpdateBet(PLAYER_A1, "TeamA", 10000);
                session.addOrUpdateBet(PLAYER_B1, "TeamB", 5000);

                Map<UUID, Long> payouts = strategy.calculatePayouts(session, "TeamA", 0.1);

                assertEquals(1, payouts.size());
                assertEquals(14500L, payouts.get(PLAYER_A1));
                assertNull(payouts.get(PLAYER_B1));
            }

            @Test
            @DisplayName("複数の勝者が賭け額に応じて再分配を按分される")
            void multipleWinners_proportionalRedistribution() {
                // totalPool=20000, winningPool=15000, losingPool=5000
                // redistributable=5000*0.9=4500
                // A1 share=10000/15000=2/3, A2 share=5000/15000=1/3
                // A1: 10000 + 4500*(2/3) = 10000 + 3000 = 13000
                // A2: 5000 + 4500*(1/3) = 5000 + 1500 = 6500
                ArenaSession session = new ArenaSession("test", Arrays.asList("TeamA", "TeamB"));
                session.addOrUpdateBet(PLAYER_A1, "TeamA", 10000);
                session.addOrUpdateBet(PLAYER_A2, "TeamA", 5000);
                session.addOrUpdateBet(PLAYER_B1, "TeamB", 5000);

                Map<UUID, Long> payouts = strategy.calculatePayouts(session, "TeamA", 0.1);

                assertEquals(2, payouts.size());
                assertEquals(13000L, payouts.get(PLAYER_A1));
                assertEquals(6500L, payouts.get(PLAYER_A2));
            }

            @Test
            @DisplayName("敗者がいない場合（全員が勝利チーム）、元金がそのまま返る")
            void noLosers_originalBetReturned() {
                // losingPool=0, redistributable=0
                // A1: 10000 + 0 = 10000, A2: 5000 + 0 = 5000
                ArenaSession session = new ArenaSession("test", Arrays.asList("TeamA", "TeamB"));
                session.addOrUpdateBet(PLAYER_A1, "TeamA", 10000);
                session.addOrUpdateBet(PLAYER_A2, "TeamA", 5000);

                Map<UUID, Long> payouts = strategy.calculatePayouts(session, "TeamA", 0.1);

                assertEquals(2, payouts.size());
                assertEquals(10000L, payouts.get(PLAYER_A1));
                assertEquals(5000L, payouts.get(PLAYER_A2));
            }

            @Test
            @DisplayName("houseEdge=0.0 の場合、敗者プール全額が再分配される")
            void houseEdgeZero_fullRedistribution() {
                // losingPool=5000, redistributable=5000*1.0=5000
                // A1: 10000 + 5000 = 15000
                ArenaSession session = new ArenaSession("test", Arrays.asList("TeamA", "TeamB"));
                session.addOrUpdateBet(PLAYER_A1, "TeamA", 10000);
                session.addOrUpdateBet(PLAYER_B1, "TeamB", 5000);

                Map<UUID, Long> payouts = strategy.calculatePayouts(session, "TeamA", 0.0);

                assertEquals(15000L, payouts.get(PLAYER_A1));
            }

            @Test
            @DisplayName("houseEdge=1.0 の場合、再分配は0で元金のみ返る")
            void houseEdgeOne_onlyOriginalReturned() {
                // redistributable=5000*0.0=0
                // A1: 10000 + 0 = 10000
                ArenaSession session = new ArenaSession("test", Arrays.asList("TeamA", "TeamB"));
                session.addOrUpdateBet(PLAYER_A1, "TeamA", 10000);
                session.addOrUpdateBet(PLAYER_B1, "TeamB", 5000);

                Map<UUID, Long> payouts = strategy.calculatePayouts(session, "TeamA", 1.0);

                assertEquals(10000L, payouts.get(PLAYER_A1));
            }

            @Test
            @DisplayName("賭けが存在しない場合、空マップを返す")
            void noBets_returnsEmpty() {
                ArenaSession session = new ArenaSession("test", Arrays.asList("TeamA", "TeamB"));

                Map<UUID, Long> payouts = strategy.calculatePayouts(session, "TeamA", 0.1);

                assertTrue(payouts.isEmpty());
            }

            @Test
            @DisplayName("勝利チームへの賭けが0の場合、空マップを返す")
            void emptyWinningPool_returnsEmpty() {
                ArenaSession session = new ArenaSession("test", Arrays.asList("TeamA", "TeamB"));
                session.addOrUpdateBet(PLAYER_B1, "TeamB", 5000);

                Map<UUID, Long> payouts = strategy.calculatePayouts(session, "TeamA", 0.1);

                assertTrue(payouts.isEmpty());
            }

            @Test
            @DisplayName("3チーム構成で敗者2チーム分が再分配される")
            void threeTeams_twoLosersRedistributed() {
                // totalPool=30000, winningPool=10000, losingPool=20000
                // redistributable=20000*0.9=18000
                // A1: 10000 + 18000*(10000/10000) = 10000 + 18000 = 28000
                ArenaSession session = new ArenaSession("test", Arrays.asList("TeamA", "TeamB", "TeamC"));
                session.addOrUpdateBet(PLAYER_A1, "TeamA", 10000);
                session.addOrUpdateBet(PLAYER_B1, "TeamB", 8000);
                session.addOrUpdateBet(PLAYER_C1, "TeamC", 12000);

                Map<UUID, Long> payouts = strategy.calculatePayouts(session, "TeamA", 0.1);

                assertEquals(1, payouts.size());
                assertEquals(28000L, payouts.get(PLAYER_A1));
            }
        }

        // ── calculateOdds ──

        @Nested
        @DisplayName("calculateOdds")
        class CalculateOdds {

            @Test
            @DisplayName("基本: 1.0 + redistributable/teamPool でオッズを返す")
            void basicOdds() {
                // totalPool=15000, teamPool=10000, losingPool=5000
                // redistributable=5000*0.9=4500
                // odds=1.0 + 4500/10000 = 1.45
                ArenaSession session = new ArenaSession("test", Arrays.asList("TeamA", "TeamB"));
                session.addOrUpdateBet(PLAYER_A1, "TeamA", 10000);
                session.addOrUpdateBet(PLAYER_B1, "TeamB", 5000);

                double odds = strategy.calculateOdds(session, "TeamA", 0.1);

                assertEquals(1.45, odds, DELTA);
            }

            @Test
            @DisplayName("少額チームほどオッズが高くなる")
            void smallerTeam_higherOdds() {
                ArenaSession session = new ArenaSession("test", Arrays.asList("TeamA", "TeamB"));
                session.addOrUpdateBet(PLAYER_A1, "TeamA", 10000);
                session.addOrUpdateBet(PLAYER_B1, "TeamB", 5000);

                double oddsA = strategy.calculateOdds(session, "TeamA", 0.1);
                double oddsB = strategy.calculateOdds(session, "TeamB", 0.1);

                assertTrue(oddsB > oddsA);
                assertEquals(1.45, oddsA, DELTA);
                // losingPool for B = 10000, redistributable=9000
                // odds=1.0 + 9000/5000 = 2.8
                assertEquals(2.8, oddsB, DELTA);
            }

            @Test
            @DisplayName("全員が同チームに賭けた場合、オッズは1.0")
            void allSameTeam_oddsOne() {
                // losingPool=0, redistributable=0, odds=1.0+0=1.0
                ArenaSession session = new ArenaSession("test", Arrays.asList("TeamA", "TeamB"));
                session.addOrUpdateBet(PLAYER_A1, "TeamA", 10000);
                session.addOrUpdateBet(PLAYER_A2, "TeamA", 5000);

                assertEquals(1.0, strategy.calculateOdds(session, "TeamA", 0.1), DELTA);
            }

            @Test
            @DisplayName("賭けなしの場合、0.0 を返す")
            void noBets_returnsZero() {
                ArenaSession session = new ArenaSession("test", Arrays.asList("TeamA", "TeamB"));

                assertEquals(0.0, strategy.calculateOdds(session, "TeamA", 0.1), DELTA);
            }

            @Test
            @DisplayName("該当チームに賭けがない場合、0.0 を返す")
            void noTeamBets_returnsZero() {
                ArenaSession session = new ArenaSession("test", Arrays.asList("TeamA", "TeamB"));
                session.addOrUpdateBet(PLAYER_B1, "TeamB", 5000);

                assertEquals(0.0, strategy.calculateOdds(session, "TeamA", 0.1), DELTA);
            }

            @Test
            @DisplayName("houseEdge=0.0 なら敗者プール全額が反映される")
            void houseEdgeZero() {
                // losingPool=5000, redistributable=5000
                // odds=1.0 + 5000/10000 = 1.5
                ArenaSession session = new ArenaSession("test", Arrays.asList("TeamA", "TeamB"));
                session.addOrUpdateBet(PLAYER_A1, "TeamA", 10000);
                session.addOrUpdateBet(PLAYER_B1, "TeamB", 5000);

                assertEquals(1.5, strategy.calculateOdds(session, "TeamA", 0.0), DELTA);
            }
        }
    }

    // =========================================================================
    // 共通エッジケース（全戦略に適用）
    // =========================================================================

    @Nested
    @DisplayName("全戦略共通のエッジケース")
    class CommonEdgeCases {

        private final PayoutStrategy[] allStrategies = {
                new PariMutuelPayout(),
                new FixedOddsPayout(),
                new SimpleRedistributionPayout()
        };

        @Test
        @DisplayName("全戦略: 賭けゼロのセッションで空マップを返す")
        void allStrategies_emptySession_returnsEmptyPayouts() {
            ArenaSession session = new ArenaSession("test", Arrays.asList("TeamA", "TeamB"));

            for (PayoutStrategy strategy : allStrategies) {
                Map<UUID, Long> payouts = strategy.calculatePayouts(session, "TeamA", 0.1);
                assertTrue(payouts.isEmpty(),
                        strategy.getClass().getSimpleName() + " should return empty for no bets");
            }
        }

        @Test
        @DisplayName("全戦略: 賭けゼロのセッションでオッズ0.0を返す")
        void allStrategies_emptySession_returnsZeroOdds() {
            ArenaSession session = new ArenaSession("test", Arrays.asList("TeamA", "TeamB"));

            for (PayoutStrategy strategy : allStrategies) {
                double odds = strategy.calculateOdds(session, "TeamA", 0.1);
                assertEquals(0.0, odds, DELTA,
                        strategy.getClass().getSimpleName() + " should return 0.0 odds for no bets");
            }
        }

        @Test
        @DisplayName("全戦略: 勝利チームにのみ配当が支払われる")
        void allStrategies_onlyWinnersGetPaid() {
            ArenaSession session = new ArenaSession("test", Arrays.asList("TeamA", "TeamB"));
            session.addOrUpdateBet(PLAYER_A1, "TeamA", 10000);
            session.addOrUpdateBet(PLAYER_B1, "TeamB", 5000);

            for (PayoutStrategy strategy : allStrategies) {
                Map<UUID, Long> payouts = strategy.calculatePayouts(session, "TeamA", 0.1);
                assertNull(payouts.get(PLAYER_B1),
                        strategy.getClass().getSimpleName() + " should not pay losers");
                assertNotNull(payouts.get(PLAYER_A1),
                        strategy.getClass().getSimpleName() + " should pay winners");
            }
        }

        @Test
        @DisplayName("全戦略: houseEdge=0.0 で勝利チームに賭けが集中していても計算可能")
        void allStrategies_houseEdgeZero_noError() {
            ArenaSession session = new ArenaSession("test", Arrays.asList("TeamA", "TeamB"));
            session.addOrUpdateBet(PLAYER_A1, "TeamA", 10000);

            for (PayoutStrategy strategy : allStrategies) {
                assertDoesNotThrow(() -> {
                    strategy.calculatePayouts(session, "TeamA", 0.0);
                    strategy.calculateOdds(session, "TeamA", 0.0);
                }, strategy.getClass().getSimpleName() + " should not throw with houseEdge=0.0");
            }
        }

        @Test
        @DisplayName("全戦略: houseEdge=1.0 でも例外を投げない")
        void allStrategies_houseEdgeOne_noError() {
            ArenaSession session = new ArenaSession("test", Arrays.asList("TeamA", "TeamB"));
            session.addOrUpdateBet(PLAYER_A1, "TeamA", 10000);
            session.addOrUpdateBet(PLAYER_B1, "TeamB", 5000);

            for (PayoutStrategy strategy : allStrategies) {
                assertDoesNotThrow(() -> {
                    strategy.calculatePayouts(session, "TeamA", 1.0);
                    strategy.calculateOdds(session, "TeamA", 1.0);
                }, strategy.getClass().getSimpleName() + " should not throw with houseEdge=1.0");
            }
        }

        @Test
        @DisplayName("全戦略: 存在しないチーム名でも空マップ/0.0を返す")
        void allStrategies_nonExistentTeam_graceful() {
            ArenaSession session = new ArenaSession("test", Arrays.asList("TeamA", "TeamB"));
            session.addOrUpdateBet(PLAYER_A1, "TeamA", 10000);
            session.addOrUpdateBet(PLAYER_B1, "TeamB", 5000);

            for (PayoutStrategy strategy : allStrategies) {
                Map<UUID, Long> payouts = strategy.calculatePayouts(session, "NonExistent", 0.1);
                assertTrue(payouts.isEmpty(),
                        strategy.getClass().getSimpleName() + " should return empty for unknown team");
            }
        }
    }
}
