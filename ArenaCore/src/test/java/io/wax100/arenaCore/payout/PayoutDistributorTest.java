package io.wax100.arenaCore.payout;

import io.wax100.arenaCore.payout.PayoutDistributor.DistributionResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("PayoutDistributor: 天引き分配計算")
class PayoutDistributorTest {

    private PayoutDistributor distributor;

    @BeforeEach
    void setUp() {
        distributor = new PayoutDistributor();
    }

    // ========================================================================
    // 正常系
    // ========================================================================

    @Nested
    @DisplayName("正常分配")
    class NormalDistributionTest {

        @Test
        @DisplayName("デフォルト率(1%/10%/5%)でプール10000を正しく分配する")
        void defaultRates_pool10000() {
            DistributionResult result = PayoutDistributor.calculate(10_000L, 0.01, 0.10, 0.05);

            assertEquals(100L, result.loserFighterTotal(), "敗者闘技者還元");
            assertEquals(1000L, result.winnerFighterTotal(), "勝者闘技者還元");
            assertEquals(500L, result.houseFee(), "運営手数料");
            assertEquals(8400L, result.bettorPayoutPool(), "観客配当プール");
        }

        @Test
        @DisplayName("分配合計がtotalPoolに等しい")
        void sumEquals_totalPool() {
            long totalPool = 10_000L;
            DistributionResult result = PayoutDistributor.calculate(totalPool, 0.01, 0.10, 0.05);

            long sum = result.loserFighterTotal()
                    + result.winnerFighterTotal()
                    + result.houseFee()
                    + result.bettorPayoutPool();
            assertEquals(totalPool, sum, "分配合計 == totalPool");
        }
    }

    // ========================================================================
    // ゼロガード
    // ========================================================================

    @Nested
    @DisplayName("ゼロガード")
    class ZeroGuardTest {

        @Test
        @DisplayName("totalPool=0のとき全て0を返す")
        void zeroPool_allZeros() {
            DistributionResult result = PayoutDistributor.calculate(0L, 0.01, 0.10, 0.05);

            assertEquals(0L, result.loserFighterTotal());
            assertEquals(0L, result.winnerFighterTotal());
            assertEquals(0L, result.houseFee());
            assertEquals(0L, result.bettorPayoutPool());
        }

        @Test
        @DisplayName("totalPoolが負のとき全て0を返す")
        void negativePool_allZeros() {
            DistributionResult result = PayoutDistributor.calculate(-100L, 0.01, 0.10, 0.05);

            assertEquals(0L, result.loserFighterTotal());
            assertEquals(0L, result.winnerFighterTotal());
            assertEquals(0L, result.houseFee());
            assertEquals(0L, result.bettorPayoutPool());
        }
    }

    // ========================================================================
    // レート境界
    // ========================================================================

    @Nested
    @DisplayName("レート境界テスト")
    class RateBoundaryTest {

        @Test
        @DisplayName("全レート0のとき観客配当プールがtotalPoolと等しい")
        void allRatesZero_bettorGetsAll() {
            long totalPool = 5000L;
            DistributionResult result = PayoutDistributor.calculate(totalPool, 0.0, 0.0, 0.0);

            assertEquals(0L, result.loserFighterTotal());
            assertEquals(0L, result.winnerFighterTotal());
            assertEquals(0L, result.houseFee());
            assertEquals(totalPool, result.bettorPayoutPool());
        }

        @Test
        @DisplayName("レート合計が1.0のとき観客配当プールが0になる")
        void ratesSumToOne_bettorZero() {
            // 0.30 + 0.50 + 0.20 = 1.00
            long totalPool = 10_000L;
            DistributionResult result = PayoutDistributor.calculate(totalPool, 0.30, 0.50, 0.20);

            assertEquals(3000L, result.loserFighterTotal());
            assertEquals(5000L, result.winnerFighterTotal());
            assertEquals(2000L, result.houseFee());
            assertEquals(0L, result.bettorPayoutPool(), "レート合計=1.0 → 残余キャプチャ0");
        }

        @Test
        @DisplayName("レート合計が1.0超のとき観客配当プールが0にクランプされる")
        void ratesExceedOne_bettorClampedToZero() {
            // 0.40 + 0.40 + 0.30 = 1.10
            long totalPool = 10_000L;
            DistributionResult result = PayoutDistributor.calculate(totalPool, 0.40, 0.40, 0.30);

            assertEquals(0L, result.bettorPayoutPool(), "Math.maxにより0にクランプ");
            // 各天引きは個別に正しく計算される
            assertEquals(4000L, result.loserFighterTotal());
            assertEquals(4000L, result.winnerFighterTotal());
            assertEquals(3000L, result.houseFee());
        }
    }

    // ========================================================================
    // 大きなプール
    // ========================================================================

    @Nested
    @DisplayName("大きなプール")
    class LargePoolTest {

        @Test
        @DisplayName("1_000_000_000Lでfloor丸めが正しく動作する")
        void largePool_floorRounding() {
            long totalPool = 1_000_000_000L;
            DistributionResult result = PayoutDistributor.calculate(totalPool, 0.01, 0.10, 0.05);

            long expectedLoser = (long) Math.floor(totalPool * 0.01);
            long expectedWinner = (long) Math.floor(totalPool * 0.10);
            long expectedHouse = (long) Math.floor(totalPool * 0.05);
            long expectedBettor = totalPool - expectedLoser - expectedWinner - expectedHouse;

            assertEquals(expectedLoser, result.loserFighterTotal());
            assertEquals(expectedWinner, result.winnerFighterTotal());
            assertEquals(expectedHouse, result.houseFee());
            assertEquals(expectedBettor, result.bettorPayoutPool());

            // 合計不変量
            long sum = result.loserFighterTotal()
                    + result.winnerFighterTotal()
                    + result.houseFee()
                    + result.bettorPayoutPool();
            assertEquals(totalPool, sum, "大きなプールでも合計 == totalPool");
        }
    }

    // ========================================================================
    // 小さなプールと丸め
    // ========================================================================

    @Nested
    @DisplayName("小さなプールと丸め")
    class SmallPoolTest {

        @Test
        @DisplayName("totalPool=7でfloor丸めにより天引きが全て0になる")
        void smallPool_allFloorToZero() {
            // 7 * 0.01 = 0.07 → floor = 0
            // 7 * 0.10 = 0.70 → floor = 0
            // 7 * 0.05 = 0.35 → floor = 0
            DistributionResult result = PayoutDistributor.calculate(7L, 0.01, 0.10, 0.05);

            assertEquals(0L, result.loserFighterTotal(), "floor(7*0.01)=0");
            assertEquals(0L, result.winnerFighterTotal(), "floor(7*0.10)=0");
            assertEquals(0L, result.houseFee(), "floor(7*0.05)=0");
            assertEquals(7L, result.bettorPayoutPool(), "端数は全て観客配当プールへ");
        }

        @Test
        @DisplayName("totalPool=1でも合計不変量を満たす")
        void pool1_sumInvariant() {
            DistributionResult result = PayoutDistributor.calculate(1L, 0.01, 0.10, 0.05);

            long sum = result.loserFighterTotal()
                    + result.winnerFighterTotal()
                    + result.houseFee()
                    + result.bettorPayoutPool();
            assertEquals(1L, sum);
        }
    }

    // ========================================================================
    // 合計不変量
    // ========================================================================

    @Nested
    @DisplayName("合計不変量")
    class SumInvariantTest {

        @Test
        @DisplayName("様々なプールサイズで loser+winner+house+bettor == totalPool")
        void variousPools_sumEqualsTotal() {
            long[] pools = {1L, 2L, 3L, 10L, 99L, 100L, 1_234L, 10_000L, 999_999L};
            for (long pool : pools) {
                DistributionResult result = PayoutDistributor.calculate(pool, 0.01, 0.10, 0.05);
                long sum = result.loserFighterTotal()
                        + result.winnerFighterTotal()
                        + result.houseFee()
                        + result.bettorPayoutPool();
                assertEquals(pool, sum, "totalPool=" + pool + " で合計不変量を満たす");
            }
        }

        @Test
        @DisplayName("様々なレートで合計不変量を満たす")
        void variousRates_sumEqualsTotal() {
            long totalPool = 10_000L;
            double[][] ratesCombinations = {
                    {0.00, 0.00, 0.00},
                    {0.01, 0.10, 0.05},
                    {0.10, 0.20, 0.30},
                    {0.33, 0.33, 0.33},
                    {0.50, 0.25, 0.25},
            };
            for (double[] rates : ratesCombinations) {
                DistributionResult result = PayoutDistributor.calculate(totalPool, rates[0], rates[1], rates[2]);
                long sum = result.loserFighterTotal()
                        + result.winnerFighterTotal()
                        + result.houseFee()
                        + result.bettorPayoutPool();
                assertEquals(totalPool, sum,
                        "rates=[%.2f, %.2f, %.2f] で合計不変量を満たす"
                                .formatted(rates[0], rates[1], rates[2]));
            }
        }
    }

    // ========================================================================
    // 観客配当プール非負保証
    // ========================================================================

    @Nested
    @DisplayName("観客配当プール非負保証")
    class BettorNonNegativeTest {

        @Test
        @DisplayName("レート合計>1.0でも観客配当プールが負にならない")
        void ratesOver100Percent_bettorNonNegative() {
            // 0.50 + 0.50 + 0.50 = 1.50
            DistributionResult result = PayoutDistributor.calculate(10_000L, 0.50, 0.50, 0.50);

            assertTrue(result.bettorPayoutPool() >= 0,
                    "Math.max(0, ...) により非負が保証される");
            assertEquals(0L, result.bettorPayoutPool());
        }

        @Test
        @DisplayName("極端なレート(各1.0)でも観客配当プールが0")
        void extremeRates_bettorZero() {
            DistributionResult result = PayoutDistributor.calculate(1000L, 1.0, 1.0, 1.0);

            assertEquals(0L, result.bettorPayoutPool());
        }
    }
}
