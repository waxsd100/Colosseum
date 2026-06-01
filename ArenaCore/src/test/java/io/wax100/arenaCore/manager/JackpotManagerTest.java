package io.wax100.arenaCore.manager;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("JackpotManager: ジャックポット管理")
class JackpotManagerTest {

    @TempDir
    File tempDir;

    private JackpotManager manager;
    private Logger logger;

    @BeforeEach
    void setUp() {
        logger = Logger.getLogger("JackpotManagerTest");
        manager = new JackpotManager(tempDir, logger);
    }

    // ========================================================================
    // 初期状態
    // ========================================================================

    @Nested
    @DisplayName("初期状態")
    class InitialStateTest {

        @Test
        @DisplayName("ファイルが存在しない場合、初期残高は0")
        void noFile_initialBalanceIsZero() {
            assertEquals(0L, manager.getBalance());
        }
    }

    // ========================================================================
    // deposit
    // ========================================================================

    @Nested
    @DisplayName("deposit - 積立")
    class DepositTest {

        @Test
        @DisplayName("正の金額を積立すると残高が増える")
        void positiveAmount_addsToBalance() {
            manager.deposit(500L);
            assertEquals(500L, manager.getBalance());
        }

        @Test
        @DisplayName("0を積立しても残高は変わらない")
        void zeroAmount_isIgnored() {
            manager.deposit(100L);
            manager.deposit(0L);
            assertEquals(100L, manager.getBalance());
        }

        @Test
        @DisplayName("負の金額を積立しても残高は変わらない")
        void negativeAmount_isIgnored() {
            manager.deposit(100L);
            manager.deposit(-50L);
            assertEquals(100L, manager.getBalance());
        }

        @Test
        @DisplayName("複数回積立すると累計になる")
        void multipleDeposits_accumulate() {
            manager.deposit(100L);
            manager.deposit(200L);
            manager.deposit(300L);
            assertEquals(600L, manager.getBalance());
        }
    }

    // ========================================================================
    // withdrawAll
    // ========================================================================

    @Nested
    @DisplayName("withdrawAll - 全額引き出し")
    class WithdrawAllTest {

        @Test
        @DisplayName("全額を引き出して残高が0になる")
        void withdrawAll_returnsFullBalanceAndResets() {
            manager.deposit(1000L);
            long withdrawn = manager.withdrawAll();

            assertEquals(1000L, withdrawn, "引き出し額");
            assertEquals(0L, manager.getBalance(), "引き出し後の残高");
        }

        @Test
        @DisplayName("残高0のとき引き出しても0を返す")
        void zeroBalance_returnsZero() {
            long withdrawn = manager.withdrawAll();
            assertEquals(0L, withdrawn);
            assertEquals(0L, manager.getBalance());
        }
    }

    // ========================================================================
    // shouldTrigger
    // ========================================================================

    @Nested
    @DisplayName("shouldTrigger - ジャックポット発動条件")
    class ShouldTriggerTest {

        @Test
        @DisplayName("totalBets=0のときfalseを返す")
        void totalBetsZero_returnsFalse() {
            manager.deposit(1000L);
            assertFalse(manager.shouldTrigger(0, 0, 0.10));
        }

        @Test
        @DisplayName("残高=0のときfalseを返す")
        void balanceZero_returnsFalse() {
            // 積立しない → balance = 0
            assertFalse(manager.shouldTrigger(10, 100, 0.10));
        }

        @Test
        @DisplayName("比率が閾値未満のときtrueを返す (9/100 < 0.10)")
        void ratioBelow_threshold_returnsTrue() {
            manager.deposit(1000L);
            // 9/100 = 0.09 < 0.10 → 発動
            assertTrue(manager.shouldTrigger(9, 100, 0.10));
        }

        @Test
        @DisplayName("比率が閾値ちょうどのときfalseを返す (10/100 == 0.10)")
        void ratioEquals_threshold_returnsFalse() {
            manager.deposit(1000L);
            // 10/100 = 0.10 == 0.10 → 発動しない（厳密に未満が条件）
            assertFalse(manager.shouldTrigger(10, 100, 0.10));
        }

        @Test
        @DisplayName("比率が閾値超のときfalseを返す (20/100 > 0.10)")
        void ratioAbove_threshold_returnsFalse() {
            manager.deposit(1000L);
            // 20/100 = 0.20 > 0.10 → 発動しない
            assertFalse(manager.shouldTrigger(20, 100, 0.10));
        }

        @Test
        @DisplayName("閾値0.0では常にfalseを返す")
        void thresholdZero_alwaysFalse() {
            manager.deposit(1000L);
            // 0/100 = 0.0 は 0.0 未満ではない
            assertFalse(manager.shouldTrigger(0, 100, 0.0));
        }

        @Test
        @DisplayName("閾値1.0で賭け比率が1.0未満ならtrueを返す")
        void thresholdOne_ratioBelow_returnsTrue() {
            manager.deposit(1000L);
            // 50/100 = 0.5 < 1.0 → 発動
            assertTrue(manager.shouldTrigger(50, 100, 1.0));
        }
    }

    // ========================================================================
    // 永続化
    // ========================================================================

    @Nested
    @DisplayName("永続化 - save/load")
    class PersistenceTest {

        @Test
        @DisplayName("積立後に新しいインスタンスを作成すると残高が復元される")
        void deposit_newInstance_balancePreserved() {
            manager.deposit(12345L);

            // 同じフォルダで新しいインスタンスを作成
            JackpotManager manager2 = new JackpotManager(tempDir, logger);
            assertEquals(12345L, manager2.getBalance(), "永続化された残高が復元される");
        }

        @Test
        @DisplayName("withdrawAll後に新しいインスタンスを作成すると残高0が復元される")
        void withdrawAll_newInstance_balanceZero() {
            manager.deposit(5000L);
            manager.withdrawAll();

            JackpotManager manager2 = new JackpotManager(tempDir, logger);
            assertEquals(0L, manager2.getBalance(), "引き出し後の残高0が永続化される");
        }

        @Test
        @DisplayName("複数回積立後の残高が永続化される")
        void multipleDeposits_persisted() {
            manager.deposit(100L);
            manager.deposit(200L);
            manager.deposit(300L);

            JackpotManager manager2 = new JackpotManager(tempDir, logger);
            assertEquals(600L, manager2.getBalance(), "累計600が永続化される");
        }
    }
}
