package io.wax100.casinoCore.manager;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PlayerStats の単体テスト
 */
class PlayerStatsTest {

    private PlayerStats stats;

    @BeforeEach
    void setUp() {
        stats = new PlayerStats();
    }

    @Nested
    @DisplayName("セッション参加")
    class SessionJoinTest {

        @Test
        void 初回参加でセッション数が1になる() {
            stats.recordSessionJoin("TestPlayer");
            assertEquals(1, stats.getTotalSessions());
            assertEquals("TestPlayer", stats.getName());
        }

        @Test
        void 複数回参加でセッション数が増える() {
            stats.recordSessionJoin("TestPlayer");
            stats.recordSessionJoin("TestPlayer");
            stats.recordSessionJoin("TestPlayer");
            assertEquals(3, stats.getTotalSessions());
        }

        @Test
        void 初回参加日時が設定される() {
            stats.recordSessionJoin("TestPlayer");
            assertNotNull(stats.getFirstPlayed());
            assertNotNull(stats.getLastPlayed());
        }

        @Test
        void 再参加で初回日時は変わらない() {
            stats.recordSessionJoin("TestPlayer");
            var first = stats.getFirstPlayed();
            stats.recordSessionJoin("TestPlayer");
            assertEquals(first, stats.getFirstPlayed());
        }

        @Test
        void プレイヤー名が更新される() {
            stats.recordSessionJoin("OldName");
            stats.recordSessionJoin("NewName");
            assertEquals("NewName", stats.getName());
        }
    }

    @Nested
    @DisplayName("購入記録")
    class PurchaseTest {

        @Test
        void 購入額が累積する() {
            stats.addPurchase(5000);
            stats.addPurchase(3000);
            assertEquals(8000, stats.getTotalPurchases());
        }

        @Test
        void 初期値は0() {
            assertEquals(0, stats.getTotalPurchases());
        }
    }

    @Nested
    @DisplayName("換金結果")
    class CashoutTest {

        @Test
        void 勝ちで勝利数が増える() {
            stats.addPurchase(10000);
            stats.recordCashout(15000, 10000);
            assertEquals(1, stats.getWins());
            assertEquals(0, stats.getLosses());
            assertEquals(0, stats.getDraws());
        }

        @Test
        void 負けで敗北数が増える() {
            stats.addPurchase(10000);
            stats.recordCashout(5000, 10000);
            assertEquals(0, stats.getWins());
            assertEquals(1, stats.getLosses());
        }

        @Test
        void 引き分け() {
            stats.addPurchase(10000);
            stats.recordCashout(10000, 10000);
            assertEquals(0, stats.getWins());
            assertEquals(0, stats.getLosses());
            assertEquals(1, stats.getDraws());
        }

        @Test
        void 累計換金額が加算される() {
            stats.recordCashout(15000, 10000);
            stats.recordCashout(5000, 10000);
            assertEquals(20000, stats.getTotalCashouts());
        }

        @Test
        void 累計損益が正しく計算される() {
            stats.addPurchase(10000);
            stats.recordCashout(15000, 10000); // +5000
            stats.addPurchase(10000);
            stats.recordCashout(3000, 10000);  // -7000
            assertEquals(-2000, stats.getNetProfit());
        }

        @Test
        void 最大勝ち額が更新される() {
            stats.recordCashout(15000, 10000); // +5000
            stats.recordCashout(30000, 10000); // +20000
            stats.recordCashout(12000, 10000); // +2000
            assertEquals(20000, stats.getBiggestWin());
        }

        @Test
        void 最大負け額が更新される() {
            stats.recordCashout(5000, 10000);  // -5000
            stats.recordCashout(1000, 10000);  // -9000
            stats.recordCashout(8000, 10000);  // -2000
            assertEquals(-9000, stats.getBiggestLoss());
        }

        @Test
        void 購入0の場合は勝敗にカウントしない() {
            stats.recordCashout(5000, 0);
            assertEquals(0, stats.getWins());
            assertEquals(0, stats.getLosses());
            assertEquals(0, stats.getDraws());
        }
    }

    @Nested
    @DisplayName("勝率")
    class WinRateTest {

        @Test
        void 勝率100パーセント() {
            stats.recordCashout(15000, 10000);
            stats.recordCashout(20000, 10000);
            assertEquals(1.0, stats.getWinRate(), 0.001);
        }

        @Test
        void 勝率0パーセント() {
            stats.recordCashout(5000, 10000);
            stats.recordCashout(3000, 10000);
            assertEquals(0.0, stats.getWinRate(), 0.001);
        }

        @Test
        void 勝率50パーセント() {
            stats.recordCashout(15000, 10000);
            stats.recordCashout(5000, 10000);
            assertEquals(0.5, stats.getWinRate(), 0.001);
        }

        @Test
        void 対戦なしは0() {
            assertEquals(0.0, stats.getWinRate(), 0.001);
        }
    }
}
