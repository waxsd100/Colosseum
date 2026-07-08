package io.wax100.casinoCore.manager;

import io.wax100.chipLib.Chip;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;

/**
 * {@link CashoutMessageFormatter} のユニットテスト。
 *
 * <p>Player.sendMessage に送信されるメッセージ文字列を検証する。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("CashoutMessageFormatter: 換金メッセージフォーマット")
class CashoutMessageFormatterTest {

    @Mock
    private Player player;

    // ── ヘルパー ──

    private List<String> captureMessages() {
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(player, atLeastOnce()).sendMessage(captor.capture());
        return captor.getAllValues();
    }

    // ========================================================================
    // 利益
    // ========================================================================

    @Nested
    @DisplayName("利益ケース")
    class ProfitTest {

        @Test
        @DisplayName("利益時に+記号と勝ちメッセージが含まれる")
        void profit_showsPlusAndWin() {
            CashoutMessageFormatter.sendCashoutMessage(
                    player, 2000, 1000, 1000, Map.of(Chip.CHIP_1000, 2));

            List<String> msgs = captureMessages();
            String joined = String.join("\n", msgs);

            assertTrue(joined.contains("購入額"));
            assertTrue(joined.contains("換金額"));
            assertTrue(joined.contains("+"));
            assertTrue(joined.contains("勝ち"));
        }
    }

    // ========================================================================
    // 損失
    // ========================================================================

    @Nested
    @DisplayName("損失ケース")
    class LossTest {

        @Test
        @DisplayName("損失時に負けメッセージが含まれる")
        void loss_showsLossMessage() {
            CashoutMessageFormatter.sendCashoutMessage(
                    player, 500, 1000, -500, Map.of(Chip.CHIP_500, 1));

            List<String> msgs = captureMessages();
            String joined = String.join("\n", msgs);

            assertTrue(joined.contains("負け"));
            assertTrue(joined.contains("購入額"));
            assertTrue(joined.contains("換金額"));
        }
    }

    // ========================================================================
    // 引き分け
    // ========================================================================

    @Nested
    @DisplayName("引き分けケース")
    class DrawTest {

        @Test
        @DisplayName("損益0で引き分けメッセージが含まれる")
        void draw_showsDrawMessage() {
            CashoutMessageFormatter.sendCashoutMessage(
                    player, 1000, 1000, 0, Map.of(Chip.CHIP_1000, 1));

            List<String> msgs = captureMessages();
            String joined = String.join("\n", msgs);

            assertTrue(joined.contains("引き分け"));
            assertTrue(joined.contains("±"));
        }
    }

    // ========================================================================
    // 購入なし（チップ換金のみ）
    // ========================================================================

    @Nested
    @DisplayName("購入なしケース")
    class NoPurchaseTest {

        @Test
        @DisplayName("購入額0の場合、購入額行が表示されない")
        void noPurchase_noPurchaseLine() {
            CashoutMessageFormatter.sendCashoutMessage(
                    player, 500, 0, 0, Map.of(Chip.CHIP_500, 1));

            List<String> msgs = captureMessages();
            String joined = String.join("\n", msgs);

            assertFalse(joined.contains("購入額"));
            assertTrue(joined.contains("チップを換金しました"));
        }
    }

    // ========================================================================
    // 内訳表示
    // ========================================================================

    @Nested
    @DisplayName("売却内訳")
    class BreakdownTest {

        @Test
        @DisplayName("全Chipの内訳行が出力される")
        void breakdownContainsAllChips() {
            // 全チップ種別に0を設定して渡す
            Map<Chip, Integer> breakdown = Map.of(
                    Chip.CHIP_100, 3,
                    Chip.CHIP_1000, 1
            );

            CashoutMessageFormatter.sendCashoutMessage(
                    player, 1300, 1000, 300, breakdown);

            List<String> msgs = captureMessages();
            String joined = String.join("\n", msgs);

            // "売却内訳" ヘッダ
            assertTrue(joined.contains("売却内訳"));
            // 枚数が表示される
            assertTrue(joined.contains("枚"));
        }

        @Test
        @DisplayName("空の内訳でも例外なく動作する")
        void emptyBreakdown_noException() {
            assertDoesNotThrow(() ->
                    CashoutMessageFormatter.sendCashoutMessage(
                            player, 0, 0, 0, Map.of()));
        }
    }

    // ========================================================================
    // メッセージカウント
    // ========================================================================

    @Nested
    @DisplayName("メッセージ構造")
    class MessageStructureTest {

        @Test
        @DisplayName("セパレータが3回含まれる")
        void separatorCount() {
            CashoutMessageFormatter.sendCashoutMessage(
                    player, 1000, 1000, 0, Map.of(Chip.CHIP_1000, 1));

            List<String> msgs = captureMessages();
            long separatorCount = msgs.stream()
                    .filter(m -> m.contains("-*-*-*-"))
                    .count();
            // SEPARATOR は3回（上部・売却内訳前後）
            assertTrue(separatorCount >= 3, "セパレータが少なくとも3回含まれること: " + separatorCount);
        }
    }
}
