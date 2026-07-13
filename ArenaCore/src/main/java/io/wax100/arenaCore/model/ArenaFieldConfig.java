package io.wax100.arenaCore.model;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.Objects;

/**
 * 戦闘エリアの形状設定を定義するシールドインターフェース。
 *
 * <p>直方体（{@link CuboidFieldConfig}）と円柱（{@link CylinderFieldConfig}）の
 * 2種類のフィールド形状をサポートする。すべての実装は軸平行境界ボックス（AABB）の
 * アクセサを提供し、WorldEdit の {@link CuboidRegion} への変換もサポートする。
 *
 * <p>インスタンスは {@link #of(String, int, int, int, int, int, int)} または
 * 各実装クラスのファクトリメソッドで生成する。
 */
public sealed interface ArenaFieldConfig
        extends CuboidArea
        permits CuboidFieldConfig, CylinderFieldConfig {

    // ── AABB アクセサ（CuboidArea から継承） ──

    /** @return ワールド名 */
    @Override
    String worldName();

    /** @return AABB 最小 X */
    @Override
    int minX();

    /** @return AABB 最小 Y */
    @Override
    int minY();

    /** @return AABB 最小 Z */
    @Override
    int minZ();

    /** @return AABB 最大 X */
    @Override
    int maxX();

    /** @return AABB 最大 Y */
    @Override
    int maxY();

    /** @return AABB 最大 Z */
    @Override
    int maxZ();

    // ── 形状固有メソッド ──

    /**
     * 指定座標がこのフィールド内にあるかを判定する。
     *
     * <p>直方体の場合は {@link CuboidArea} のデフォルト実装を利用し、
     * 円柱の場合は XZ 平面上の距離判定を行う。
     *
     * @param loc 判定対象座標
     * @return フィールド内の場合 {@code true}
     */
    @Override
    boolean contains(Location loc);

    /**
     * WorldEdit の {@link CuboidRegion} に変換する（ワールド付き）。
     *
     * <p>すべての形状で AABB バウンディングボックスに基づく領域を返す。
     *
     * @return CuboidRegion
     * @throws NullPointerException ワールドが未ロードの場合
     */
    default CuboidRegion toRegion() {
        com.sk89q.worldedit.world.World weWorld =
                BukkitAdapter.adapt(Objects.requireNonNull(getWorld(),
                        "World not loaded: " + worldName()));
        return new CuboidRegion(weWorld,
                BlockVector3.at(minX(), minY(), minZ()),
                BlockVector3.at(maxX(), maxY(), maxZ()));
    }

    /**
     * エリア内のブロック数を返す（形状固有）。
     *
     * @return ブロック数
     */
    long getBlockCount();

    /**
     * フィールド中心座標を返す（形状固有）。
     *
     * @param world 対象ワールド
     * @return フィールド中心の Location
     */
    Location getCenter(World world);

    /**
     * この設定を YAML に書き出す。
     *
     * @param yaml     書き出し先の YamlConfiguration（null不可）
     * @param basePath キーの接頭辞（例: {@code "field"}）
     */
    void toYaml(YamlConfiguration yaml, String basePath);

    /**
     * 形状の表示名を返す。
     *
     * @return 直方体の場合 {@code "直方体"}、円柱の場合 {@code "円柱"}
     */
    String shapeDisplayName();

    /**
     * このフィールドのワールドを取得する。
     *
     * @return Bukkit ワールド。未ロードの場合は {@code null}
     */
    default World getWorld() {
        return Bukkit.getWorld(worldName());
    }

    // ── ファクトリメソッド ──

    /**
     * 2点の座標から最小・最大を自動計算して直方体フィールドを生成する。
     *
     * @param worldName ワールド名（null不可）
     * @param x1        第1点 X
     * @param y1        第1点 Y
     * @param z1        第1点 Z
     * @param x2        第2点 X
     * @param y2        第2点 Y
     * @param z2        第2点 Z
     * @return 正規化済みの {@link CuboidFieldConfig}
     */
    static ArenaFieldConfig of(String worldName,
                               int x1, int y1, int z1,
                               int x2, int y2, int z2) {
        return CuboidFieldConfig.of(worldName, x1, y1, z1, x2, y2, z2);
    }

    /**
     * YAML セクションから {@link ArenaFieldConfig} を復元する。
     *
     * <p>{@code shape} キーが {@code "cylinder"} の場合は {@link CylinderFieldConfig}、
     * それ以外（{@code "cuboid"} または未指定）の場合は {@link CuboidFieldConfig} を返す。
     * 未指定の場合に直方体として扱うことで、旧プリセットとの後方互換性を維持する。
     *
     * @param section YAML セクション（null不可）
     * @return 復元した設定。データ不正の場合は {@code null}
     */
    static ArenaFieldConfig fromYaml(ConfigurationSection section) {
        Objects.requireNonNull(section, "section must not be null");
        String shape = section.getString("shape", "cuboid");
        return switch (shape) {
            case "cylinder" -> CylinderFieldConfig.fromYaml(section);
            default -> CuboidFieldConfig.fromYaml(section);
        };
    }
}
