package io.wax100.arenaCore.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Bet: 賭け情報モデル")
class BetTest {

    private static final String TEAM_RED = "Red";

    private UUID playerId;
    private Bet bet;

    @BeforeEach
    void setUp() {
        playerId = UUID.randomUUID();
        bet = new Bet(playerId, TEAM_RED, 100L);
    }

    // ========================================================================
    // コンストラクタ
    // ========================================================================

    @Nested
    @DisplayName("コンストラクタ")
    class ConstructorTest {

        @Test
        @DisplayName("プレイヤーIDが正しく設定される")
        void playerId_isSet() {
            assertEquals(playerId, bet.playerId());
        }

        @Test
        @DisplayName("チーム名が正しく設定される")
        void teamName_isSet() {
            assertEquals(TEAM_RED, bet.teamName());
        }

        @Test
        @DisplayName("金額が正しく設定される")
        void amount_isSet() {
            assertEquals(100L, bet.amount());
        }

        @Test
        @DisplayName("固定オッズの初期値は0.0である")
        void lockedOdds_initiallyZero() {
            assertEquals(0.0, bet.lockedOdds(), 0.0001);
        }

        @Test
        @DisplayName("金額0でBetを生成できる")
        void zeroAmount_isValid() {
            Bet zeroBet = new Bet(UUID.randomUUID(), TEAM_RED, 0L);
            assertEquals(0L, zeroBet.amount());
        }

        @Test
        @DisplayName("負の金額でIllegalArgumentExceptionが発生する")
        void negativeAmount_throwsIAE() {
            assertThrows(IllegalArgumentException.class,
                    () -> new Bet(UUID.randomUUID(), TEAM_RED, -1L));
        }

        @Test
        @DisplayName("大きな金額でBetを生成できる")
        void largeAmount_isPreserved() {
            long largeAmount = 1_000_000_000L;
            Bet largeBet = new Bet(UUID.randomUUID(), TEAM_RED, largeAmount);
            assertEquals(largeAmount, largeBet.amount());
        }
    }

    // ========================================================================
    // addAmount
    // ========================================================================

    @Nested
    @DisplayName("addAmount - 金額加算")
    class AddAmountTest {

        @Test
        @DisplayName("正の金額を加算できる")
        void positiveAmount_adds() {
            bet.addAmount(50L);
            assertEquals(150L, bet.amount());
        }

        @Test
        @DisplayName("複数回加算すると累計になる")
        void multipleAdds_accumulate() {
            bet.addAmount(20L);
            bet.addAmount(30L);
            bet.addAmount(50L);
            assertEquals(200L, bet.amount());
        }

        @Test
        @DisplayName("0を加算しても金額は変わらない")
        void zeroAdd_noChange() {
            bet.addAmount(0L);
            assertEquals(100L, bet.amount());
        }

        @Test
        @DisplayName("負の金額を加算すると減算される")
        void negativeAmount_subtracts() {
            bet.addAmount(-30L);
            assertEquals(70L, bet.amount());
        }

        @Test
        @DisplayName("結果が負になる減算はIllegalArgumentExceptionが発生する")
        void negativeResult_throwsIAE() {
            // 100 - 200 = -100 → IAE
            assertThrows(IllegalArgumentException.class,
                    () -> bet.addAmount(-200L));
            // 元の金額が変わっていないことも確認
            assertEquals(100L, bet.amount());
        }

        @Test
        @DisplayName("大きな金額を加算してもオーバーフローしない範囲で正しく動作する")
        void largeAdd_worksCorrectly() {
            Bet largeBet = new Bet(UUID.randomUUID(), TEAM_RED, 0L);
            largeBet.addAmount(Long.MAX_VALUE / 2);
            largeBet.addAmount(Long.MAX_VALUE / 2);
            assertEquals(Long.MAX_VALUE - 1, largeBet.amount());
        }
    }

    // ========================================================================
    // lockedOdds
    // ========================================================================

    @Nested
    @DisplayName("lockedOdds - 固定オッズ")
    class LockedOddsTest {

        @Test
        @DisplayName("オッズを設定して取得できる")
        void setAndGet() {
            bet.setLockedOdds(2.5);
            assertEquals(2.5, bet.lockedOdds(), 0.0001);
        }

        @Test
        @DisplayName("オッズを上書きできる")
        void canOverwrite() {
            bet.setLockedOdds(1.5);
            bet.setLockedOdds(3.0);
            assertEquals(3.0, bet.lockedOdds(), 0.0001);
        }

        @Test
        @DisplayName("オッズに1.0を設定できる")
        void evenOdds() {
            bet.setLockedOdds(1.0);
            assertEquals(1.0, bet.lockedOdds(), 0.0001);
        }

        @Test
        @DisplayName("オッズに0.0を設定して初期状態に戻せる")
        void resetToZero() {
            bet.setLockedOdds(5.0);
            bet.setLockedOdds(0.0);
            assertEquals(0.0, bet.lockedOdds(), 0.0001);
        }

        @Test
        @DisplayName("非常に小さいオッズ値を正しく保持する")
        void verySmallOdds() {
            bet.setLockedOdds(0.001);
            assertEquals(0.001, bet.lockedOdds(), 0.00001);
        }

        @Test
        @DisplayName("非常に大きいオッズ値を正しく保持する")
        void veryLargeOdds() {
            bet.setLockedOdds(9999.99);
            assertEquals(9999.99, bet.lockedOdds(), 0.001);
        }
    }

    // ========================================================================
    // 不変性
    // ========================================================================

    @Nested
    @DisplayName("フィールド不変性")
    class ImmutabilityTest {

        @Test
        @DisplayName("playerIdはコンストラクタ後に変更できない (getterのみ)")
        void playerId_isImmutable() {
            UUID original = bet.playerId();
            // playerIdにはsetterがないため、取得値が常に同一であることを確認
            assertEquals(original, bet.playerId());
        }

        @Test
        @DisplayName("teamNameはコンストラクタ後に変更できない (getterのみ)")
        void teamName_isImmutable() {
            String original = bet.teamName();
            assertEquals(original, bet.teamName());
        }

        @Test
        @DisplayName("addAmountで金額を変更してもplayerIdとteamNameは変わらない")
        void addAmount_doesNotAffectOtherFields() {
            UUID originalId = bet.playerId();
            String originalTeam = bet.teamName();

            bet.addAmount(999L);

            assertEquals(originalId, bet.playerId());
            assertEquals(originalTeam, bet.teamName());
        }

        @Test
        @DisplayName("setLockedOddsで金額やチーム名は変わらない")
        void setOdds_doesNotAffectOtherFields() {
            long originalAmount = bet.amount();
            String originalTeam = bet.teamName();

            bet.setLockedOdds(5.0);

            assertEquals(originalAmount, bet.amount());
            assertEquals(originalTeam, bet.teamName());
        }
    }
}
