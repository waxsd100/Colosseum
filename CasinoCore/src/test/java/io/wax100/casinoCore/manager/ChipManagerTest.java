package io.wax100.casinoCore.manager;

import io.wax100.chipLib.Chip;
import io.wax100.chipLib.ChipManager;
import org.bukkit.plugin.java.JavaPlugin;
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
 * ChipManager の単体テスト（Mockito でプラグインをモック）
 */
@ExtendWith(MockitoExtension.class)
class ChipManagerTest {

    @Mock
    private JavaPlugin plugin;

    private ChipManager chipManager;

    @BeforeEach
    void setUp() {
        when(plugin.getName()).thenReturn("CasinoCore");
        chipManager = new ChipManager(plugin);
    }

    // ── formatAmount ──

    @Nested
    @DisplayName("formatAmount")
    class FormatAmountTest {
        @Test
        @DisplayName("基本的なフォーマット")
        void basicFormat() {
            assertEquals("1,000", ChipManager.formatAmount(1000));
            assertEquals("100,000", ChipManager.formatAmount(100000));
            assertEquals("1,000,000", ChipManager.formatAmount(1000000));
        }

        @Test
        @DisplayName("小さい値はカンマなし")
        void smallValuesHaveNoComma() {
            assertEquals("1", ChipManager.formatAmount(1));
            assertEquals("100", ChipManager.formatAmount(100));
            assertEquals("999", ChipManager.formatAmount(999));
        }

        @Test
        @DisplayName("ゼロ")
        void zero() {
            assertEquals("0", ChipManager.formatAmount(0));
        }

        @Test
        @DisplayName("負の値")
        void negativeValues() {
            assertEquals("-500", ChipManager.formatAmount(-500));
            assertEquals("-1,000", ChipManager.formatAmount(-1000));
        }
    }

    // ── Chip enum ──

    @Nested
    @DisplayName("Chip enum")
    class ChipEnumTest {
        @Test
        @DisplayName("チップは13種類")
        void thirteenChipTypes() {
            assertEquals(13, Chip.values().length);
        }

        @Test
        @DisplayName("最低額は1E_最高額は1000000E")
        void lowestIs1EAndHighestIs1000000E() {
            assertEquals(1, Chip.values()[0].getValue());
            Chip[] chips = Chip.values();
            assertEquals(1000000, chips[chips.length - 1].getValue());
        }

        @Test
        @DisplayName("全チップにプロパティがある")
        void allChipsHaveProperties() {
            for (Chip chip : Chip.values()) {
                assertNotNull(chip.getMaterial(), chip.name());
                assertNotNull(chip.getColorName(), chip.name());
                assertNotNull(chip.getChatColor(), chip.name());
                assertTrue(chip.getValue() > 0, chip.name());
            }
        }

        @Test
        @DisplayName("額面は昇順")
        void denominationsAreInAscendingOrder() {
            Chip[] chips = Chip.values();
            for (int i = 1; i < chips.length; i++) {
                assertTrue(chips[i].getValue() > chips[i - 1].getValue());
            }
        }

        @Test
        @DisplayName("マテリアルは重複しない")
        void materialsAreUnique() {
            long distinct = java.util.Arrays.stream(Chip.values())
                    .map(Chip::getMaterial).distinct().count();
            assertEquals(Chip.values().length, distinct);
        }

        @Test
        @DisplayName("チャットカラーは重複しない")
        void chatColorsAreUnique() {
            long distinct = java.util.Arrays.stream(Chip.values())
                    .map(Chip::getChatColor).distinct().count();
            assertEquals(Chip.values().length, distinct,
                    "ChatColor が重複しているチップがあります");
        }
    }

    // ── breakdownAmount ──

    @Nested
    @DisplayName("breakdownAmount")
    class BreakdownTest {
        @Test
        @DisplayName("ぴったりの額面は1枚")
        void exactDenominationProducesSingleChip() {
            Map<Chip, Integer> result = chipManager.breakdownAmount(100000);
            assertEquals(1, result.get(Chip.CHIP_100000));
            assertEquals(1, result.size());
        }

        @Test
        @DisplayName("大きい額面から割り当てる")
        void allocatesFromLargestDenomination() {
            Map<Chip, Integer> result = chipManager.breakdownAmount(15000);
            assertEquals(1, result.get(Chip.CHIP_10000));
            assertEquals(1, result.get(Chip.CHIP_5000));
        }

        @Test
        @DisplayName("複雑な分割")
        void complexBreakdown() {
            Map<Chip, Integer> result = chipManager.breakdownAmount(12345);
            assertEquals(1, result.get(Chip.CHIP_10000));
            assertEquals(2, result.get(Chip.CHIP_1000));
            assertEquals(3, result.get(Chip.CHIP_100));
            assertEquals(4, result.get(Chip.CHIP_10));
            assertEquals(1, result.get(Chip.CHIP_5));
        }

        @Test
        @DisplayName("最小額面のみ")
        void minimumDenominationOnly() {
            Map<Chip, Integer> result = chipManager.breakdownAmount(3);
            assertEquals(3, result.get(Chip.CHIP_1));
            assertEquals(1, result.size());
        }

        @Test
        @DisplayName("合計が元の金額と一致する")
        void totalMatchesOriginalAmount() {
            long amount = 999999;
            Map<Chip, Integer> result = chipManager.breakdownAmount(amount);
            long total = result.entrySet().stream()
                    .mapToLong(e -> e.getKey().getValue() * e.getValue())
                    .sum();
            assertEquals(amount, total);
        }

        @Test
        @DisplayName("最高額チップの分割")
        void highestDenominationChipBreakdown() {
            Map<Chip, Integer> result = chipManager.breakdownAmount(3000000);
            assertEquals(3, result.get(Chip.CHIP_1000000));
            assertEquals(1, result.size());
        }
    }

    // ── fromValue ──

    @Nested
    @DisplayName("fromValue")
    class FromValueTest {
        @Test
        @DisplayName("有効な額面")
        void validDenomination() {
            assertEquals(Chip.CHIP_100, Chip.fromValue(100).orElse(null));
            assertEquals(Chip.CHIP_1000000, Chip.fromValue(1000000).orElse(null));
        }

        @Test
        @DisplayName("無効な額面はempty")
        void invalidDenominationReturnsEmpty() {
            assertTrue(Chip.fromValue(999).isEmpty());
            assertTrue(Chip.fromValue(0).isEmpty());
            assertTrue(Chip.fromValue(-1).isEmpty());
        }
    }

    // ── calculateSlotsNeeded ──

    @Nested
    @DisplayName("calculateSlotsNeeded")
    class SlotsNeededTest {
        @Test
        @DisplayName("スタック内は1スロット")
        void withinStackUsesOneSlot() {
            assertEquals(1, chipManager.calculateSlotsNeeded(Map.of(Chip.CHIP_100, 64)));
        }

        @Test
        @DisplayName("スタック超過は2スロット")
        void exceedingStackUsesTwoSlots() {
            assertEquals(2, chipManager.calculateSlotsNeeded(Map.of(Chip.CHIP_100, 65)));
        }

        @Test
        @DisplayName("複数種類")
        void multipleTypes() {
            assertEquals(2, chipManager.calculateSlotsNeeded(
                    Map.of(Chip.CHIP_100, 64, Chip.CHIP_1000, 1)));
        }
    }

    // ── isChipMaterial ──

    @Nested
    @DisplayName("isChipMaterial")
    class IsChipMaterialTest {
        @Test
        @DisplayName("チップ素材はtrue")
        void chipMaterialReturnsTrue() {
            for (Chip chip : Chip.values()) {
                assertTrue(ChipManager.isChipMaterial(chip.getMaterial()),
                        chip.name() + " の素材が判定されない");
            }
        }

        @Test
        @DisplayName("チップ以外の素材はfalse")
        void nonChipMaterialReturnsFalse() {
            assertFalse(ChipManager.isChipMaterial(org.bukkit.Material.STONE));
            assertFalse(ChipManager.isChipMaterial(org.bukkit.Material.DIAMOND));
            assertFalse(ChipManager.isChipMaterial(org.bukkit.Material.SHEARS));
        }
    }
}
