package io.wax100.casinoCore.manager;

import io.wax100.casinoCore.CasinoCore;
import io.wax100.casinoCore.manager.ChipManager.Chip;
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
    private CasinoCore plugin;

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
        void 基本的なフォーマット() {
            assertEquals("1,000", ChipManager.formatAmount(1000));
            assertEquals("100,000", ChipManager.formatAmount(100000));
            assertEquals("1,000,000", ChipManager.formatAmount(1000000));
        }

        @Test
        void 小さい値はカンマなし() {
            assertEquals("1", ChipManager.formatAmount(1));
            assertEquals("100", ChipManager.formatAmount(100));
            assertEquals("999", ChipManager.formatAmount(999));
        }

        @Test
        void ゼロ() {
            assertEquals("0", ChipManager.formatAmount(0));
        }

        @Test
        void 負の値() {
            assertEquals("-500", ChipManager.formatAmount(-500));
            assertEquals("-1,000", ChipManager.formatAmount(-1000));
        }
    }

    // ── Chip enum ──

    @Nested
    @DisplayName("Chip enum")
    class ChipEnumTest {
        @Test
        void チップは13種類() {
            assertEquals(13, Chip.values().length);
        }

        @Test
        void 最低額は1E_最高額は1000000E() {
            assertEquals(1, Chip.values()[0].getValue());
            Chip[] chips = Chip.values();
            assertEquals(1000000, chips[chips.length - 1].getValue());
        }

        @Test
        void 全チップにプロパティがある() {
            for (Chip chip : Chip.values()) {
                assertNotNull(chip.getMaterial(), chip.name());
                assertNotNull(chip.getColorName(), chip.name());
                assertNotNull(chip.getChatColor(), chip.name());
                assertTrue(chip.getValue() > 0, chip.name());
            }
        }

        @Test
        void 額面は昇順() {
            Chip[] chips = Chip.values();
            for (int i = 1; i < chips.length; i++) {
                assertTrue(chips[i].getValue() > chips[i - 1].getValue());
            }
        }

        @Test
        void マテリアルは重複しない() {
            long distinct = java.util.Arrays.stream(Chip.values())
                    .map(Chip::getMaterial).distinct().count();
            assertEquals(Chip.values().length, distinct);
        }
    }

    // ── breakdownAmount ──

    @Nested
    @DisplayName("breakdownAmount")
    class BreakdownTest {
        @Test
        void ぴったりの額面は1枚() {
            Map<Chip, Integer> result = chipManager.breakdownAmount(100000);
            assertEquals(1, result.get(Chip.CHIP_100000));
            assertEquals(1, result.size());
        }

        @Test
        void 大きい額面から割り当てる() {
            Map<Chip, Integer> result = chipManager.breakdownAmount(15000);
            assertEquals(1, result.get(Chip.CHIP_10000));
            assertEquals(1, result.get(Chip.CHIP_5000));
        }

        @Test
        void 複雑な分割() {
            Map<Chip, Integer> result = chipManager.breakdownAmount(12345);
            assertEquals(1, result.get(Chip.CHIP_10000));
            assertEquals(2, result.get(Chip.CHIP_1000));
            assertEquals(3, result.get(Chip.CHIP_100));
            assertEquals(4, result.get(Chip.CHIP_10));
            assertEquals(1, result.get(Chip.CHIP_5));
        }

        @Test
        void 最小額面のみ() {
            Map<Chip, Integer> result = chipManager.breakdownAmount(3);
            assertEquals(3, result.get(Chip.CHIP_1));
            assertEquals(1, result.size());
        }

        @Test
        void 合計が元の金額と一致する() {
            long amount = 999999;
            Map<Chip, Integer> result = chipManager.breakdownAmount(amount);
            long total = result.entrySet().stream()
                    .mapToLong(e -> e.getKey().getValue() * e.getValue())
                    .sum();
            assertEquals(amount, total);
        }

        @Test
        void 最高額チップの分割() {
            Map<Chip, Integer> result = chipManager.breakdownAmount(3000000);
            assertEquals(3, result.get(Chip.CHIP_1000000));
            assertEquals(1, result.size());
        }
    }

    // ── getChipByValue ──

    @Nested
    @DisplayName("getChipByValue")
    class GetChipByValueTest {
        @Test
        void 有効な額面() {
            assertEquals(Chip.CHIP_100, chipManager.getChipByValue(100));
            assertEquals(Chip.CHIP_1000000, chipManager.getChipByValue(1000000));
        }

        @Test
        void 無効な額面はnull() {
            assertNull(chipManager.getChipByValue(999));
            assertNull(chipManager.getChipByValue(0));
            assertNull(chipManager.getChipByValue(-1));
        }
    }

    // ── calculateSlotsNeeded ──

    @Nested
    @DisplayName("calculateSlotsNeeded")
    class SlotsNeededTest {
        @Test
        void スタック内は1スロット() {
            assertEquals(1, chipManager.calculateSlotsNeeded(Map.of(Chip.CHIP_100, 64)));
        }

        @Test
        void スタック超過は2スロット() {
            assertEquals(2, chipManager.calculateSlotsNeeded(Map.of(Chip.CHIP_100, 65)));
        }

        @Test
        void 複数種類() {
            assertEquals(2, chipManager.calculateSlotsNeeded(
                    Map.of(Chip.CHIP_100, 64, Chip.CHIP_1000, 1)));
        }
    }

    // ── isChipMaterial ──

    @Nested
    @DisplayName("isChipMaterial")
    class IsChipMaterialTest {
        @Test
        void チップ素材はtrue() {
            for (Chip chip : Chip.values()) {
                assertTrue(ChipManager.isChipMaterial(chip.getMaterial()),
                        chip.name() + " の素材が判定されない");
            }
        }

        @Test
        void チップ以外の素材はfalse() {
            assertFalse(ChipManager.isChipMaterial(org.bukkit.Material.STONE));
            assertFalse(ChipManager.isChipMaterial(org.bukkit.Material.DIAMOND));
            assertFalse(ChipManager.isChipMaterial(org.bukkit.Material.SHEARS));
        }
    }
}
