package io.wax100.casinoCore.manager;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * PlayerStats の切断・オフライン払い出しシナリオに特化した単体テスト。
 *
 * <p>
 * handlePlayerDisconnect → recordCashout(handChipsValue, purchased) と
 * OfflinePayoutManager → recordCashout(wonAmount, 0) の組み合わせを
 * 網羅的にテストする。PlayerStats は純粋な POJO なのでモック不要。
 */
class PlayerStatsDisconnectTest {

    private PlayerStats stats;

    @BeforeEach
    void setUp() {
        stats = new PlayerStats();
        stats.recordSessionJoin("TestPlayer");
    }

    // ================================================================
    // 1. 切断フロー: handlePlayerDisconnect が recordCashout(handChips, purchased) を呼ぶ
    // ================================================================
    @Nested
    @DisplayName("切断フロー — recordCashout(handChips, purchased)")
    class DisconnectFlowTest {

        @Test
        @DisplayName("購入10000, 手持ち5000 → 損失5000が記録される")
        void 購入より手持ちが少ない場合は損失() {
            stats.addPurchase(10000);
            stats.recordCashout(5000, 10000);

            assertEquals(10000, stats.getTotalPurchases());
            assertEquals(5000, stats.getTotalCashouts());
            assertEquals(-5000, stats.getNetProfit());
            assertEquals(0, stats.getWins());
            assertEquals(1, stats.getLosses());
            assertEquals(0, stats.getDraws());
            assertEquals(-5000, stats.getBiggestLoss());
        }

        @Test
        @DisplayName("購入10000, 手持ち10000 → 引き分け (netProfit=0)")
        void 購入と手持ちが同じ場合は引き分け() {
            stats.addPurchase(10000);
            stats.recordCashout(10000, 10000);

            assertEquals(10000, stats.getTotalPurchases());
            assertEquals(10000, stats.getTotalCashouts());
            assertEquals(0, stats.getNetProfit());
            assertEquals(0, stats.getWins());
            assertEquals(0, stats.getLosses());
            assertEquals(1, stats.getDraws());
        }

        @Test
        @DisplayName("購入5000, 手持ち8000 (勝利分あり) → 利益3000")
        void 手持ちが購入より多い場合は勝利() {
            stats.addPurchase(5000);
            stats.recordCashout(8000, 5000);

            assertEquals(5000, stats.getTotalPurchases());
            assertEquals(8000, stats.getTotalCashouts());
            assertEquals(3000, stats.getNetProfit());
            assertEquals(1, stats.getWins());
            assertEquals(0, stats.getLosses());
            assertEquals(0, stats.getDraws());
            assertEquals(3000, stats.getBiggestWin());
        }

        @Test
        @DisplayName("購入0, 手持ち0 → recordCashout 未呼出相当, 統計変化なし")
        void 購入ゼロ手持ちゼロの場合は統計変化なし() {
            // handlePlayerDisconnect の条件分岐で recordCashout が呼ばれない場合を想定
            // 呼ばないことをシミュレート → 何も呼ばず統計が初期値のまま
            assertEquals(0, stats.getTotalPurchases());
            assertEquals(0, stats.getTotalCashouts());
            assertEquals(0, stats.getNetProfit());
            assertEquals(0, stats.getWins());
            assertEquals(0, stats.getLosses());
            assertEquals(0, stats.getDraws());
        }
    }

    // ================================================================
    // 2. オフライン払い出しフロー: OfflinePayoutManager が recordCashout(wonAmount, 0) を呼ぶ
    // ================================================================
    @Nested
    @DisplayName("オフライン払い出しフロー — recordCashout(wonAmount, 0)")
    class OfflinePayoutFlowTest {

        @Test
        @DisplayName("切断で損失記録後、オフライン賭けで15000勝利 → cashouts+=15000, profit+=15000")
        void 切断後のオフライン勝利で利益追加() {
            // 切断時: 購入10000, 手持ち5000
            stats.addPurchase(10000);
            stats.recordCashout(5000, 10000);

            // オフライン払い出し: 15000勝利
            stats.recordCashout(15000, 0);

            assertEquals(10000, stats.getTotalPurchases());
            assertEquals(20000, stats.getTotalCashouts());   // 5000 + 15000
            assertEquals(10000, stats.getNetProfit());        // -5000 + 15000
        }

        @Test
        @DisplayName("切断後オフライン賭けが負け → recordCashout 未呼出, 統計は切断時のまま")
        void オフライン賭け負けではrecordCashout呼ばれない() {
            // 切断時: 購入10000, 手持ち5000
            stats.addPurchase(10000);
            stats.recordCashout(5000, 10000);

            // wonAmount=0 のため OfflinePayoutManager が recordCashout を呼ばない想定
            // 統計は切断時のまま
            assertEquals(5000, stats.getTotalCashouts());
            assertEquals(-5000, stats.getNetProfit());
            assertEquals(1, stats.getLosses());
        }

        @Test
        @DisplayName("purchased=0 のオフライン払い出しでは勝敗カウント変化なし")
        void purchased0では勝敗カウントが変わらない() {
            stats.recordCashout(15000, 0);

            assertEquals(15000, stats.getTotalCashouts());
            assertEquals(15000, stats.getNetProfit());
            // purchased=0 → wins/losses/draws 変化なし
            assertEquals(0, stats.getWins());
            assertEquals(0, stats.getLosses());
            assertEquals(0, stats.getDraws());
        }
    }

    // ================================================================
    // 3. 切断 + オフライン払い出し 複合ライフサイクル
    // ================================================================
    @Nested
    @DisplayName("切断 + オフライン払い出し 複合ライフサイクル")
    class CombinedLifecycleTest {

        @Test
        @DisplayName("購入10000→手持ち5000→切断→オフライン15000勝利 → 最終profit=+10000, wins=1(切断時の損失)")
        void 切断損失後にオフライン勝利() {
            stats.addPurchase(10000);

            // 切断: recordCashout(5000, 10000) → sessionNet = -5000 → loss
            stats.recordCashout(5000, 10000);

            assertEquals(-5000, stats.getNetProfit());
            assertEquals(1, stats.getLosses());
            assertEquals(0, stats.getWins());

            // オフライン勝利: recordCashout(15000, 0) → sessionNet = +15000, purchased=0 なので勝敗変化なし
            stats.recordCashout(15000, 0);

            assertEquals(10000, stats.getTotalPurchases());
            assertEquals(20000, stats.getTotalCashouts());  // 5000 + 15000
            assertEquals(10000, stats.getNetProfit());       // -5000 + 15000
            assertEquals(1, stats.getLosses());               // 切断時の損失のまま
            assertEquals(0, stats.getWins());                 // オフライン分は勝敗にカウントされない
            assertEquals(0, stats.getDraws());
        }

        @Test
        @DisplayName("購入10000→手持ち5000→切断→オフライン負け → 最終profit=-5000, losses=1")
        void 切断損失後にオフライン負け() {
            stats.addPurchase(10000);

            // 切断: recordCashout(5000, 10000)
            stats.recordCashout(5000, 10000);

            // オフライン負け: recordCashout が呼ばれない想定
            // 統計は切断時のまま
            assertEquals(10000, stats.getTotalPurchases());
            assertEquals(5000, stats.getTotalCashouts());
            assertEquals(-5000, stats.getNetProfit());
            assertEquals(0, stats.getWins());
            assertEquals(1, stats.getLosses());
            assertEquals(0, stats.getDraws());
        }

        @Test
        @DisplayName("購入10000→手持ち10000→切断(引き分け)→オフライン20000勝利 → profit=+20000, draws=1")
        void 切断引き分け後にオフライン勝利() {
            stats.addPurchase(10000);

            // 切断: recordCashout(10000, 10000) → sessionNet=0 → draw
            stats.recordCashout(10000, 10000);

            assertEquals(0, stats.getNetProfit());
            assertEquals(1, stats.getDraws());

            // オフライン勝利: recordCashout(20000, 0) → sessionNet=+20000, purchased=0 → 勝敗変化なし
            stats.recordCashout(20000, 0);

            assertEquals(10000, stats.getTotalPurchases());
            assertEquals(30000, stats.getTotalCashouts());  // 10000 + 20000
            assertEquals(20000, stats.getNetProfit());       // 0 + 20000
            assertEquals(0, stats.getWins());                 // オフラインは勝敗なし
            assertEquals(0, stats.getLosses());
            assertEquals(1, stats.getDraws());                // 切断時の引き分けのまま
        }
    }

    // ================================================================
    // 4. 複数セッションの累積
    // ================================================================
    @Nested
    @DisplayName("複数セッションの切断 + オフライン払い出し累積")
    class MultiSessionAccumulationTest {

        @Test
        @DisplayName("2セッション: 切断+オフライン → 統計が正しく累積される")
        void 複数セッションの統計累積() {
            // --- セッション1 ---
            stats.addPurchase(10000);
            // 切断: 手持ち3000
            stats.recordCashout(3000, 10000);  // sessionNet = -7000, loss
            // オフライン勝利: 12000
            stats.recordCashout(12000, 0);     // sessionNet = +12000, 勝敗変化なし

            // セッション1後の中間検証
            assertEquals(10000, stats.getTotalPurchases());
            assertEquals(15000, stats.getTotalCashouts());  // 3000 + 12000
            assertEquals(5000, stats.getNetProfit());        // -7000 + 12000
            assertEquals(1, stats.getLosses());
            assertEquals(0, stats.getWins());

            // --- セッション2 ---
            stats.recordSessionJoin("TestPlayer");
            stats.addPurchase(8000);
            // 切断: 手持ち6000
            stats.recordCashout(6000, 8000);   // sessionNet = -2000, loss
            // オフライン勝利: 5000
            stats.recordCashout(5000, 0);      // sessionNet = +5000, 勝敗変化なし

            // 最終検証
            assertEquals(2, stats.getTotalSessions());
            assertEquals(18000, stats.getTotalPurchases()); // 10000 + 8000
            assertEquals(26000, stats.getTotalCashouts());  // 15000 + 6000 + 5000
            assertEquals(8000, stats.getNetProfit());        // 5000 + (-2000) + 5000
            assertEquals(2, stats.getLosses());               // セッション1,2 の切断損失
            assertEquals(0, stats.getWins());
            assertEquals(0, stats.getDraws());
        }

        @Test
        @DisplayName("セッション1で損失、セッション2で勝利 → wins=1, losses=1")
        void セッション間の勝敗が独立して記録される() {
            // セッション1: 損失
            stats.addPurchase(10000);
            stats.recordCashout(5000, 10000); // loss

            // セッション2: 勝利
            stats.recordSessionJoin("TestPlayer");
            stats.addPurchase(5000);
            stats.recordCashout(12000, 5000); // win

            assertEquals(1, stats.getWins());
            assertEquals(1, stats.getLosses());
            assertEquals(0, stats.getDraws());
            assertEquals(15000, stats.getTotalPurchases()); // 10000 + 5000
            assertEquals(17000, stats.getTotalCashouts());  // 5000 + 12000
            assertEquals(2000, stats.getNetProfit());        // -5000 + 7000
        }

        @Test
        @DisplayName("3セッション: 損失→引き分け→勝利 + 各オフライン払い出し → 全統計正確")
        void 三セッション全パターン混合() {
            // セッション1: 損失 + オフライン勝利
            stats.addPurchase(10000);
            stats.recordCashout(4000, 10000);  // loss, -6000
            stats.recordCashout(8000, 0);       // offline, +8000

            // セッション2: 引き分け + オフライン勝利
            stats.recordSessionJoin("TestPlayer");
            stats.addPurchase(5000);
            stats.recordCashout(5000, 5000);   // draw, 0
            stats.recordCashout(3000, 0);       // offline, +3000

            // セッション3: 勝利 + オフライン勝利
            stats.recordSessionJoin("TestPlayer");
            stats.addPurchase(7000);
            stats.recordCashout(11000, 7000);  // win, +4000
            stats.recordCashout(2000, 0);       // offline, +2000

            assertEquals(3, stats.getTotalSessions());
            assertEquals(22000, stats.getTotalPurchases()); // 10000 + 5000 + 7000
            // cashouts: 4000 + 8000 + 5000 + 3000 + 11000 + 2000 = 33000
            assertEquals(33000, stats.getTotalCashouts());
            // netProfit: -6000 + 8000 + 0 + 3000 + 4000 + 2000 = 11000
            assertEquals(11000, stats.getNetProfit());
            assertEquals(1, stats.getWins());
            assertEquals(1, stats.getLosses());
            assertEquals(1, stats.getDraws());

            // biggestWin: max(+8000, +3000, +4000, +2000) だが purchased=0 は勝敗カウント外
            // purchased>0 での最大勝利: +4000 (セッション3)
            assertEquals(4000, stats.getBiggestWin());
            // biggestLoss: -6000 (セッション1)
            assertEquals(-6000, stats.getBiggestLoss());
        }
    }

    // ================================================================
    // 5. オーバーフロー保護
    // ================================================================
    @Nested
    @DisplayName("オーバーフロー保護")
    class OverflowProtectionTest {

        @Test
        @DisplayName("totalPurchases が Long.MAX_VALUE にクランプされる")
        void 購入額オーバーフロー時にMAX_VALUEにクランプ() {
            stats.addPurchase(Long.MAX_VALUE);
            stats.addPurchase(1);

            assertEquals(Long.MAX_VALUE, stats.getTotalPurchases());
        }

        @Test
        @DisplayName("totalCashouts が Long.MAX_VALUE にクランプされる")
        void 換金額オーバーフロー時にMAX_VALUEにクランプ() {
            stats.recordCashout(Long.MAX_VALUE, 0);
            stats.recordCashout(1, 0);

            assertEquals(Long.MAX_VALUE, stats.getTotalCashouts());
        }

        @Test
        @DisplayName("netProfit の正方向オーバーフローが Long.MAX_VALUE にクランプされる")
        void 利益正方向オーバーフロー() {
            stats.recordCashout(Long.MAX_VALUE, 0);
            stats.recordCashout(1, 0);

            assertEquals(Long.MAX_VALUE, stats.getNetProfit());
        }

        @Test
        @DisplayName("netProfit の負方向オーバーフローが Long.MIN_VALUE にクランプされる")
        void 利益負方向オーバーフロー() {
            // sessionNet = 0 - Long.MAX_VALUE = -Long.MAX_VALUE → netProfit = -MAX_VALUE
            stats.recordCashout(0, Long.MAX_VALUE);
            // sessionNet = 0 - Long.MAX_VALUE = -Long.MAX_VALUE → netProfit would underflow
            stats.recordCashout(0, Long.MAX_VALUE);

            assertEquals(Long.MIN_VALUE, stats.getNetProfit());
        }

        @Test
        @DisplayName("大きな値でも切断+オフライン払い出しフローが破綻しない")
        void 大量チップの切断オフラインフロー() {
            long largePurchase = Long.MAX_VALUE / 2;
            long smallHand = Long.MAX_VALUE / 4;
            long offlineWin = Long.MAX_VALUE / 3;

            stats.addPurchase(largePurchase);
            stats.recordCashout(smallHand, largePurchase);
            stats.recordCashout(offlineWin, 0);

            // 各値が数学的に正しいかオーバーフローなく計算されることを検証
            long expectedCashouts = smallHand + offlineWin;
            assertEquals(expectedCashouts, stats.getTotalCashouts());

            long sessionNet1 = smallHand - largePurchase; // 負の値
            long sessionNet2 = offlineWin;                 // 正の値
            long expectedProfit = sessionNet1 + sessionNet2;
            assertEquals(expectedProfit, stats.getNetProfit());
        }

        @Test
        @DisplayName("sessionNet 計算自体のオーバーフロー (cashout >> purchased)")
        void sessionNet自体のオーバーフロー() {
            // cashoutAmount=MAX_VALUE, purchased が負の影響を与えるほど極端な差はないが
            // subtractExact で cashoutAmount - purchased がオーバーフローするケース:
            // cashoutAmount = Long.MAX_VALUE, purchased = -1 は型的に不可能だが
            // cashoutAmount = Long.MAX_VALUE, purchased = 0 → sessionNet = MAX_VALUE (正常)
            stats.recordCashout(Long.MAX_VALUE, 0);
            assertEquals(Long.MAX_VALUE, stats.getNetProfit());
            assertEquals(Long.MAX_VALUE, stats.getTotalCashouts());
        }
    }

    // ================================================================
    // 6. 勝率への影響
    // ================================================================
    @Nested
    @DisplayName("切断・オフラインフローの勝率への影響")
    class WinRateWithDisconnectTest {

        @Test
        @DisplayName("切断損失1回 + オフライン勝利(purchased=0) → 勝率0% (勝敗1回のみ)")
        void オフライン勝利は勝率に影響しない() {
            stats.addPurchase(10000);
            stats.recordCashout(5000, 10000); // loss
            stats.recordCashout(20000, 0);     // offline win, 勝敗カウントなし

            assertEquals(0, stats.getWins());
            assertEquals(1, stats.getLosses());
            assertEquals(0.0, stats.getWinRate(), 0.001);
        }

        @Test
        @DisplayName("切断勝利1回 + 切断損失1回 → 勝率50%")
        void 切断時の勝敗で勝率計算() {
            stats.addPurchase(5000);
            stats.recordCashout(10000, 5000); // win

            stats.addPurchase(10000);
            stats.recordCashout(3000, 10000); // loss

            assertEquals(1, stats.getWins());
            assertEquals(1, stats.getLosses());
            assertEquals(0.5, stats.getWinRate(), 0.001);
        }

        @Test
        @DisplayName("オフライン払い出しのみ (purchased=0) → 勝率0% (対戦数0)")
        void オフライン払い出しのみでは対戦数ゼロ() {
            stats.recordCashout(10000, 0);
            stats.recordCashout(5000, 0);

            assertEquals(0, stats.getWins() + stats.getLosses() + stats.getDraws());
            assertEquals(0.0, stats.getWinRate(), 0.001);
        }
    }

    // ================================================================
    // 7. biggestWin / biggestLoss とオフラインフローの関係
    // ================================================================
    @Nested
    @DisplayName("最大勝ち/負け額とオフラインフロー")
    class BiggestWinLossWithOfflineTest {

        @Test
        @DisplayName("purchased=0 のオフライン勝利は biggestWin を更新しない")
        void オフライン勝利はbiggestWin更新しない() {
            // 通常の勝利: +3000
            stats.addPurchase(5000);
            stats.recordCashout(8000, 5000);
            assertEquals(3000, stats.getBiggestWin());

            // オフライン勝利: sessionNet = +50000 だが purchased=0 なので更新なし
            stats.recordCashout(50000, 0);
            assertEquals(3000, stats.getBiggestWin()); // 変わらない
        }

        @Test
        @DisplayName("purchased>0 の切断損失は biggestLoss を更新する")
        void 切断損失はbiggestLossを更新する() {
            stats.addPurchase(10000);
            stats.recordCashout(2000, 10000); // loss = -8000
            assertEquals(-8000, stats.getBiggestLoss());

            stats.addPurchase(10000);
            stats.recordCashout(7000, 10000); // loss = -3000 (less severe)
            assertEquals(-8000, stats.getBiggestLoss()); // 変わらない

            stats.addPurchase(20000);
            stats.recordCashout(1000, 20000); // loss = -19000 (more severe)
            assertEquals(-19000, stats.getBiggestLoss()); // 更新
        }
    }
}
