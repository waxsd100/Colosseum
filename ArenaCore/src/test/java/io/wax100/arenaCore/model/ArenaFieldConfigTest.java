package io.wax100.arenaCore.model;

import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("ArenaFieldConfig: 戦闘エリアAABBの設定")
class ArenaFieldConfigTest {

    private static final String WORLD_NAME = "world";
    private ArenaFieldConfig config;

    @BeforeEach
    void setUp() {
        // (10, 20, 30) → (50, 80, 70) の範囲
        config = ArenaFieldConfig.of(WORLD_NAME, 10, 20, 30, 50, 80, 70);
    }

    // ========================================================================
    // コンストラクタ
    // ========================================================================

    @Nested
    @DisplayName("コンストラクタ")
    class ConstructorTest {

        @Test
        @DisplayName("正常な2点で生成できる")
        void normalConstruction() {
            assertNotNull(config);
        }

        @Test
        @DisplayName("座標が逆順でもmin/maxが自動計算される")
        void autoSwapMinMax() {
            // 大→小の順で渡す
            ArenaFieldConfig reversed = ArenaFieldConfig.of(WORLD_NAME, 50, 80, 70, 10, 20, 30);
            assertEquals(10, reversed.minX());
            assertEquals(20, reversed.minY());
            assertEquals(30, reversed.minZ());
            assertEquals(50, reversed.maxX());
            assertEquals(80, reversed.maxY());
            assertEquals(70, reversed.maxZ());
        }

        @Test
        @DisplayName("同一座標で1x1x1が作られる")
        void samePoint_creates1x1x1() {
            ArenaFieldConfig single = ArenaFieldConfig.of(WORLD_NAME, 5, 5, 5, 5, 5, 5);
            assertEquals(5, single.minX());
            assertEquals(5, single.maxX());
            assertEquals(1L, single.getBlockCount());
        }

        @Test
        @DisplayName("nullのワールド名でNPEが発生する")
        void nullWorldName_throwsNPE() {
            assertThrows(NullPointerException.class,
                    () -> ArenaFieldConfig.of(null, 0, 0, 0, 1, 1, 1));
        }
    }

    // ========================================================================
    // ゲッター
    // ========================================================================

    @Nested
    @DisplayName("ゲッター")
    class GetterTest {

        @Test
        @DisplayName("ワールド名が正しく返される")
        void worldName() {
            assertEquals(WORLD_NAME, config.worldName());
        }

        @Test
        @DisplayName("min座標が正しく返される")
        void minCoords() {
            assertEquals(10, config.minX());
            assertEquals(20, config.minY());
            assertEquals(30, config.minZ());
        }

        @Test
        @DisplayName("max座標が正しく返される")
        void maxCoords() {
            assertEquals(50, config.maxX());
            assertEquals(80, config.maxY());
            assertEquals(70, config.maxZ());
        }
    }

    // ========================================================================
    // contains
    // ========================================================================

    @Nested
    @DisplayName("contains - 範囲内判定")
    class ContainsTest {

        private Location makeLocation(String worldName, double x, double y, double z) {
            World mockWorld = mock(World.class);
            when(mockWorld.getName()).thenReturn(worldName);
            return new Location(mockWorld, x, y, z);
        }

        @Test
        @DisplayName("範囲内の座標でtrueを返す")
        void insidePoint_returnsTrue() {
            assertTrue(config.contains(makeLocation(WORLD_NAME, 30, 50, 50)));
        }

        @Test
        @DisplayName("最小境界上でtrueを返す")
        void minEdge_returnsTrue() {
            assertTrue(config.contains(makeLocation(WORLD_NAME, 10, 20, 30)));
        }

        @Test
        @DisplayName("最大境界上でtrueを返す")
        void maxEdge_returnsTrue() {
            assertTrue(config.contains(makeLocation(WORLD_NAME, 50, 80, 70)));
        }

        @Test
        @DisplayName("範囲外の座標でfalseを返す（X超過）")
        void outsideX_returnsFalse() {
            assertFalse(config.contains(makeLocation(WORLD_NAME, 51, 50, 50)));
        }

        @Test
        @DisplayName("範囲外の座標でfalseを返す（Y不足）")
        void outsideY_returnsFalse() {
            assertFalse(config.contains(makeLocation(WORLD_NAME, 30, 19, 50)));
        }

        @Test
        @DisplayName("範囲外の座標でfalseを返す（Z超過）")
        void outsideZ_returnsFalse() {
            assertFalse(config.contains(makeLocation(WORLD_NAME, 30, 50, 71)));
        }

        @Test
        @DisplayName("異なるワールドでfalseを返す")
        void differentWorld_returnsFalse() {
            assertFalse(config.contains(makeLocation("nether", 30, 50, 50)));
        }

        @Test
        @DisplayName("Locationのワールドがnullでfalseを返す")
        void nullWorld_returnsFalse() {
            Location loc = new Location(null, 30, 50, 50);
            assertFalse(config.contains(loc));
        }
    }

    // ========================================================================
    // getBlockCount
    // ========================================================================

    @Nested
    @DisplayName("getBlockCount - ブロック数計算")
    class GetBlockCountTest {

        @Test
        @DisplayName("正しいブロック数を返す")
        void normalBlockCount() {
            // (50-10+1) * (80-20+1) * (70-30+1) = 41 * 61 * 41 = 102,541
            assertEquals(102_541L, config.getBlockCount());
        }

        @Test
        @DisplayName("1x1x1の場合は1を返す")
        void singleBlock_returnsOne() {
            ArenaFieldConfig single = ArenaFieldConfig.of(WORLD_NAME, 0, 0, 0, 0, 0, 0);
            assertEquals(1L, single.getBlockCount());
        }

        @Test
        @DisplayName("大きなエリアでもオーバーフローしない（long）")
        void largeArea_noOverflow() {
            // 2001 * 321 * 2001 = 1,285,282,321 (int範囲内だがlongで正確)
            ArenaFieldConfig large = ArenaFieldConfig.of(WORLD_NAME,
                    -1000, 0, -1000, 1000, 320, 1000);
            long expected = 2001L * 321L * 2001L;
            assertEquals(expected, large.getBlockCount());
        }
    }

    // ========================================================================
    // equals / hashCode
    // ========================================================================

    @Nested
    @DisplayName("equals / hashCode")
    class EqualsHashCodeTest {

        @Test
        @DisplayName("同一パラメータで等しい")
        void sameParams_areEqual() {
            ArenaFieldConfig other = ArenaFieldConfig.of(WORLD_NAME, 10, 20, 30, 50, 80, 70);
            assertEquals(config, other);
            assertEquals(config.hashCode(), other.hashCode());
        }

        @Test
        @DisplayName("座標が逆順でも結果的に等しい")
        void reversedParams_areEqual() {
            ArenaFieldConfig reversed = ArenaFieldConfig.of(WORLD_NAME, 50, 80, 70, 10, 20, 30);
            assertEquals(config, reversed);
        }

        @Test
        @DisplayName("座標が異なると等しくない")
        void differentCoords_notEqual() {
            ArenaFieldConfig other = ArenaFieldConfig.of(WORLD_NAME, 10, 20, 30, 50, 80, 71);
            assertNotEquals(config, other);
        }

        @Test
        @DisplayName("ワールドが異なると等しくない")
        void differentWorld_notEqual() {
            ArenaFieldConfig other = ArenaFieldConfig.of("nether", 10, 20, 30, 50, 80, 70);
            assertNotEquals(config, other);
        }

        @Test
        @DisplayName("自身と等しい")
        void reflexive() {
            assertEquals(config, config);
        }

        @Test
        @DisplayName("nullとは等しくない")
        void notEqualToNull() {
            assertNotEquals(null, config);
        }

        @Test
        @DisplayName("異なる型とは等しくない")
        void notEqualToDifferentType() {
            assertNotEquals("string", config);
        }
    }

    // ========================================================================
    // toString
    // ========================================================================

    @Nested
    @DisplayName("toString")
    class ToStringTest {

        @Test
        @DisplayName("ワールド名が含まれる")
        void containsWorldName() {
            assertTrue(config.toString().contains(WORLD_NAME));
        }

        @Test
        @DisplayName("座標が含まれる")
        void containsCoords() {
            String str = config.toString();
            assertTrue(str.contains("10"));
            assertTrue(str.contains("50"));
        }

        @Test
        @DisplayName("ブロック数が含まれる")
        void containsBlockCount() {
            assertTrue(config.toString().contains(String.valueOf(config.getBlockCount())));
        }
    }
}
