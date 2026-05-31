package io.wax100.chipLib;

import org.bukkit.Material;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link Chip} enum のユニットテスト。
 *
 * <p>Bukkit API に依存しない純粋な enum ロジックをテストする。
 */
@DisplayName("Chip enum")
class ChipTest {

    // ========================================================================
    // fromValue
    // ========================================================================

    @Nested
    @DisplayName("fromValue - 額面からChipを取得")
    class FromValueTest {

        @Test
        @DisplayName("存在する額面でChipを返す")
        void existingValue_returnsChip() {
            assertEquals(Chip.CHIP_1, Chip.fromValue(1).orElse(null));
            assertEquals(Chip.CHIP_100, Chip.fromValue(100).orElse(null));
            assertEquals(Chip.CHIP_1000000, Chip.fromValue(1000000).orElse(null));
        }

        @Test
        @DisplayName("存在しない額面で空Optionalを返す")
        void nonExistingValue_returnsEmpty() {
            assertTrue(Chip.fromValue(0).isEmpty());
            assertTrue(Chip.fromValue(-1).isEmpty());
            assertTrue(Chip.fromValue(2).isEmpty());
            assertTrue(Chip.fromValue(999999).isEmpty());
        }

        @Test
        @DisplayName("全額面が正しくマッピングされている")
        void allValues_areMapped() {
            for (Chip chip : Chip.values()) {
                Optional<Chip> found = Chip.fromValue(chip.getValue());
                assertTrue(found.isPresent(), "Missing mapping for value: " + chip.getValue());
                assertSame(chip, found.get());
            }
        }
    }

    // ========================================================================
    // fromMaterial
    // ========================================================================

    @Nested
    @DisplayName("fromMaterial - マテリアルからChipを取得")
    class FromMaterialTest {

        @Test
        @DisplayName("チップ用マテリアルでChipを返す")
        void chipMaterial_returnsChip() {
            assertEquals(Chip.CHIP_1, Chip.fromMaterial(Material.BROWN_CARPET).orElse(null));
            assertEquals(Chip.CHIP_1000000, Chip.fromMaterial(Material.BLACK_CARPET).orElse(null));
        }

        @Test
        @DisplayName("チップ用でないマテリアルで空Optionalを返す")
        void nonChipMaterial_returnsEmpty() {
            assertTrue(Chip.fromMaterial(Material.STONE).isEmpty());
            assertTrue(Chip.fromMaterial(Material.DIAMOND).isEmpty());
        }

        @Test
        @DisplayName("nullで空Optionalを返す（NPEしない）")
        void nullMaterial_returnsEmpty() {
            // EnumMap.get(null) は NPE を投げるため、fromMaterial 側で null ガードが必要
            Optional<Chip> result = assertDoesNotThrow(() -> Chip.fromMaterial(null),
                    "fromMaterial(null) が NPE を投げてはならない");
            assertTrue(result.isEmpty(), "null マテリアルに対しては空 Optional を返すべき");
        }

        @Test
        @DisplayName("全マテリアルが正しくマッピングされている")
        void allMaterials_areMapped() {
            for (Chip chip : Chip.values()) {
                Optional<Chip> found = Chip.fromMaterial(chip.getMaterial());
                assertTrue(found.isPresent(), "Missing mapping for material: " + chip.getMaterial());
                assertSame(chip, found.get());
            }
        }
    }

    // ========================================================================
    // isChipMaterial
    // ========================================================================

    @Nested
    @DisplayName("isChipMaterial - チップ用マテリアル判定")
    class IsChipMaterialTest {

        @Test
        @DisplayName("チップ用マテリアルでtrueを返す")
        void chipMaterial_returnsTrue() {
            assertTrue(Chip.isChipMaterial(Material.BROWN_CARPET));
            assertTrue(Chip.isChipMaterial(Material.BLACK_CARPET));
            assertTrue(Chip.isChipMaterial(Material.RED_CARPET));
        }

        @Test
        @DisplayName("チップ用でないマテリアルでfalseを返す")
        void nonChipMaterial_returnsFalse() {
            assertFalse(Chip.isChipMaterial(Material.STONE));
            assertFalse(Chip.isChipMaterial(Material.DIAMOND));
            assertFalse(Chip.isChipMaterial(Material.GRAY_CARPET)); // チップ未使用カーペット
        }

        @Test
        @DisplayName("全Chip定義のマテリアルがtrueになる")
        void allChipMaterials_returnTrue() {
            for (Chip chip : Chip.values()) {
                assertTrue(Chip.isChipMaterial(chip.getMaterial()),
                        chip.name() + " の material がチップとして認識されない");
            }
        }
    }

    // ========================================================================
    // denominationsDescending
    // ========================================================================

    @Nested
    @DisplayName("denominationsDescending - 降順リスト")
    class DenominationsDescendingTest {

        @Test
        @DisplayName("リストが降順に並んでいる")
        void array_isInDescendingOrder() {
            List<Chip> desc = Chip.denominationsDescending();
            for (int i = 0; i < desc.size() - 1; i++) {
                assertTrue(desc.get(i).getValue() > desc.get(i + 1).getValue(),
                        desc.get(i).name() + " (" + desc.get(i).getValue() + ") should be > "
                                + desc.get(i + 1).name() + " (" + desc.get(i + 1).getValue() + ")");
            }
        }

        @Test
        @DisplayName("全Chip定義が含まれている")
        void array_containsAllChips() {
            assertEquals(Chip.values().length, Chip.denominationsDescending().size());
        }

        @Test
        @DisplayName("先頭が最大額面、末尾が最小額面")
        void firstIsMax_lastIsMin() {
            List<Chip> desc = Chip.denominationsDescending();
            assertEquals(Chip.CHIP_1000000, desc.get(0));
            assertEquals(Chip.CHIP_1, desc.get(desc.size() - 1));
        }
    }

    // ========================================================================
    // プロパティアクセス
    // ========================================================================

    @Nested
    @DisplayName("プロパティ - 基本アクセサ")
    class PropertyTest {

        @Test
        @DisplayName("全Chipが正の額面を持つ")
        void allChips_havePositiveValue() {
            for (Chip chip : Chip.values()) {
                assertTrue(chip.getValue() > 0, chip.name() + " has non-positive value");
            }
        }

        @Test
        @DisplayName("全Chipが非nullのプロパティを持つ")
        void allChips_haveNonNullProperties() {
            for (Chip chip : Chip.values()) {
                assertNotNull(chip.getMaterial(), chip.name() + " material is null");
                assertNotNull(chip.getColorName(), chip.name() + " colorName is null");
                assertNotNull(chip.getChatColor(), chip.name() + " chatColor is null");
            }
        }

        @Test
        @DisplayName("getDisplayNameがフォーマットされた文字列を返す")
        void getDisplayName_returnsFormattedString() {
            String display = Chip.CHIP_100.getDisplayName("100");
            assertTrue(display.contains("100"));
            assertTrue(display.contains("E チップ"));
        }

        @Test
        @DisplayName("額面の一意性 — 重複する額面がない")
        void values_areUnique() {
            long[] values = new long[Chip.values().length];
            for (int i = 0; i < Chip.values().length; i++) {
                values[i] = Chip.values()[i].getValue();
            }
            assertEquals(values.length,
                    java.util.Arrays.stream(values).distinct().count(),
                    "額面に重複がある");
        }

        @Test
        @DisplayName("マテリアルの一意性 — 重複するマテリアルがない")
        void materials_areUnique() {
            Material[] mats = new Material[Chip.values().length];
            for (int i = 0; i < Chip.values().length; i++) {
                mats[i] = Chip.values()[i].getMaterial();
            }
            assertEquals(mats.length,
                    java.util.Arrays.stream(mats).distinct().count(),
                    "マテリアルに重複がある");
        }
    }
}
