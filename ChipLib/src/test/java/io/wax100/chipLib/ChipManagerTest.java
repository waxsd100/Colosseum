package io.wax100.chipLib;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * {@link ChipManager} のユニットテスト。
 *
 * <p>Bukkit/ItemStack に依存しない純粋ロジック
 * ({@code formatAmount}, {@code breakdownAmount}, {@code calculateSlotsNeeded})
 * を網羅的に検証する。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ChipManager: チップ管理ロジック")
class ChipManagerTest {

    @Mock
    private org.bukkit.plugin.java.JavaPlugin plugin;

    private ChipManager chipManager;

    @BeforeEach
    void setUp() {
        when(plugin.getName()).thenReturn("ChipLibTest");
        chipManager = new ChipManager(plugin);
    }

    // ========================================================================
    // formatAmount (static)
    // ========================================================================

    @Nested
    @DisplayName("formatAmount - 金額フォーマット")
    class FormatAmountTest {

        @Test
        @DisplayName("カンマ区切りで正しくフォーマットされる")
        void commaSeparated() {
            assertEquals("1,000", ChipManager.formatAmount(1000));
            assertEquals("1,000,000", ChipManager.formatAmount(1_000_000));
        }

        @Test
        @DisplayName("1000未満はカンマなし")
        void lessThan1000_noComma() {
            assertEquals("0", ChipManager.formatAmount(0));
            assertEquals("1", ChipManager.formatAmount(1));
            assertEquals("999", ChipManager.formatAmount(999));
        }

        @Test
        @DisplayName("負の値もフォーマットされる")
        void negativeValues() {
            assertEquals("-1", ChipManager.formatAmount(-1));
            assertEquals("-1,000", ChipManager.formatAmount(-1000));
            assertEquals("-999,999", ChipManager.formatAmount(-999_999));
        }

        @Test
        @DisplayName("大きな値がオーバーフローしない")
        void largeValue_noOverflow() {
            String result = ChipManager.formatAmount(Long.MAX_VALUE);
            assertNotNull(result);
            assertFalse(result.isEmpty());
        }
    }

    // ========================================================================
    // breakdownAmount
    // ========================================================================

    @Nested
    @DisplayName("breakdownAmount - 金額チップ分割")
    class BreakdownAmountTest {

        @Test
        @DisplayName("ぴったりの額面は1枚")
        void exactDenomination() {
            Map<Chip, Integer> result = chipManager.breakdownAmount(100);
            assertEquals(1, result.get(Chip.CHIP_100));
            assertEquals(1, result.size());
        }

        @Test
        @DisplayName("複数額面に分割される")
        void multiDenomination() {
            // 1,500 = 1,000 + 500
            Map<Chip, Integer> result = chipManager.breakdownAmount(1500);
            assertEquals(1, result.get(Chip.CHIP_1000));
            assertEquals(1, result.get(Chip.CHIP_500));
            assertEquals(2, result.size());
        }

        @Test
        @DisplayName("複雑な分割: 12,345")
        void complexBreakdown() {
            Map<Chip, Integer> result = chipManager.breakdownAmount(12345);
            assertEquals(1, result.get(Chip.CHIP_10000));
            assertEquals(2, result.get(Chip.CHIP_1000));
            assertEquals(3, result.get(Chip.CHIP_100));
            assertEquals(4, result.get(Chip.CHIP_10));
            assertEquals(1, result.get(Chip.CHIP_5));
        }

        @Test
        @DisplayName("0は空マップを返す")
        void zero_returnsEmptyMap() {
            assertTrue(chipManager.breakdownAmount(0).isEmpty());
        }

        @Test
        @DisplayName("負の値は空マップを返す")
        void negative_returnsEmptyMap() {
            assertTrue(chipManager.breakdownAmount(-100).isEmpty());
        }

        @Test
        @DisplayName("最大額チップ複数枚")
        void maxChipMultiple() {
            Map<Chip, Integer> result = chipManager.breakdownAmount(5_000_000);
            assertEquals(5, result.get(Chip.CHIP_1000000));
            assertEquals(1, result.size());
        }

        @Test
        @DisplayName("合計額が元の金額と一致する")
        void totalMatchesOriginal() {
            long amount = 777_777;
            Map<Chip, Integer> result = chipManager.breakdownAmount(amount);
            long total = result.entrySet().stream()
                    .mapToLong(e -> e.getKey().getValue() * e.getValue())
                    .sum();
            assertEquals(amount, total);
        }

        @Test
        @DisplayName("返却マップは変更不可")
        void returnedMap_isUnmodifiable() {
            Map<Chip, Integer> result = chipManager.breakdownAmount(100);
            assertThrows(UnsupportedOperationException.class,
                    () -> result.put(Chip.CHIP_1, 99));
        }

        @Test
        @DisplayName("最小額面のみ")
        void onlyMinimumDenomination() {
            Map<Chip, Integer> result = chipManager.breakdownAmount(4);
            assertEquals(4, result.get(Chip.CHIP_1));
            assertEquals(1, result.size());
        }
    }

    // ========================================================================
    // calculateSlotsNeeded
    // ========================================================================

    @Nested
    @DisplayName("calculateSlotsNeeded - 必要スロット数")
    class CalculateSlotsNeededTest {

        @Test
        @DisplayName("64枚以内は1スロット")
        void withinStack_oneSlot() {
            assertEquals(1, chipManager.calculateSlotsNeeded(Map.of(Chip.CHIP_100, 1)));
            assertEquals(1, chipManager.calculateSlotsNeeded(Map.of(Chip.CHIP_100, 64)));
        }

        @Test
        @DisplayName("65枚は2スロット")
        void exceedingStack_twoSlots() {
            assertEquals(2, chipManager.calculateSlotsNeeded(Map.of(Chip.CHIP_100, 65)));
        }

        @Test
        @DisplayName("128枚は2スロット")
        void doubleStack() {
            assertEquals(2, chipManager.calculateSlotsNeeded(Map.of(Chip.CHIP_100, 128)));
        }

        @Test
        @DisplayName("129枚は3スロット")
        void overDoubleStack() {
            assertEquals(3, chipManager.calculateSlotsNeeded(Map.of(Chip.CHIP_100, 129)));
        }

        @Test
        @DisplayName("複数種類のチップ")
        void multipleTypes() {
            // 64 + 1 = 2スロット
            assertEquals(2, chipManager.calculateSlotsNeeded(
                    Map.of(Chip.CHIP_100, 64, Chip.CHIP_1000, 1)));
        }

        @Test
        @DisplayName("空マップは0スロット")
        void emptyMap_zeroSlots() {
            assertEquals(0, chipManager.calculateSlotsNeeded(Map.of()));
        }

        @Test
        @DisplayName("nullでNPEが発生する")
        void null_throwsNPE() {
            assertThrows(NullPointerException.class,
                    () -> chipManager.calculateSlotsNeeded(null));
        }
    }

    // ========================================================================
    // getChipByValue (deprecated)
    // ========================================================================

    @Nested
    @DisplayName("fromValue - 額面からChip取得")
    class FromValueTest {

        @Test
        @DisplayName("有効な額面でChipを返す")
        void validValue() {
            assertEquals(Chip.CHIP_1, Chip.fromValue(1).orElse(null));
            assertEquals(Chip.CHIP_100, Chip.fromValue(100).orElse(null));
            assertEquals(Chip.CHIP_1000000, Chip.fromValue(1_000_000).orElse(null));
        }

        @Test
        @DisplayName("無効な額面でemptyを返す")
        void invalidValue_returnsEmpty() {
            assertTrue(Chip.fromValue(0).isEmpty());
            assertTrue(Chip.fromValue(-1).isEmpty());
            assertTrue(Chip.fromValue(999).isEmpty());
        }

        @Test
        @DisplayName("全Chipが正引きできる")
        void allChips_reverseLookup() {
            for (Chip chip : Chip.values()) {
                assertEquals(chip, Chip.fromValue(chip.getValue()).orElse(null));
            }
        }
    }

    // ========================================================================
    // isChipMaterial (static)
    // ========================================================================

    @Nested
    @DisplayName("isChipMaterial - 素材判定")
    class IsChipMaterialTest {

        @Test
        @DisplayName("チップ素材はtrue")
        void chipMaterial_true() {
            for (Chip chip : Chip.values()) {
                assertTrue(ChipManager.isChipMaterial(chip.getMaterial()));
            }
        }

        @Test
        @DisplayName("非チップ素材はfalse")
        void nonChip_false() {
            assertFalse(ChipManager.isChipMaterial(org.bukkit.Material.STONE));
            assertFalse(ChipManager.isChipMaterial(org.bukkit.Material.DIAMOND));
        }
    }
}
