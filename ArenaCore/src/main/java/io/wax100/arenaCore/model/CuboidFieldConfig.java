package io.wax100.arenaCore.model;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.List;
import java.util.Objects;

/**
 * 直方体（AABB）フィールド設定レコード。
 *
 * <p>旧 {@code ArenaFieldConfig} レコードのロジックをそのまま引き継ぐ。
 * フィールド範囲内のブロック変更を地形復元の対象とするために使用する。
 *
 * @param worldName ワールド名
 * @param minX      最小 X
 * @param minY      最小 Y
 * @param minZ      最小 Z
 * @param maxX      最大 X
 * @param maxY      最大 Y
 * @param maxZ      最大 Z
 */
public record CuboidFieldConfig(
        String worldName,
        int minX, int minY, int minZ,
        int maxX, int maxY, int maxZ
) implements ArenaFieldConfig, CuboidArea {

    /**
     * 正規化済みの値でレコードを生成する（コンパクトコンストラクタ）。
     */
    public CuboidFieldConfig {
        Objects.requireNonNull(worldName, "worldName must not be null");
    }

    // ── contains は CuboidArea のデフォルト実装を利用 ──

    /**
     * {@inheritDoc}
     *
     * <p>直方体の AABB 判定を行う。{@link CuboidArea} のデフォルト実装と同等。
     */
    @Override
    public boolean contains(Location loc) {
        if (loc.getWorld() == null) return false;
        if (!loc.getWorld().getName().equals(worldName)) return false;
        int x = loc.getBlockX();
        int y = loc.getBlockY();
        int z = loc.getBlockZ();
        return x >= minX && x <= maxX
                && y >= minY && y <= maxY
                && z >= minZ && z <= maxZ;
    }

    // ── ファクトリメソッド ──

    /**
     * 2点の座標から最小・最大を自動計算してインスタンスを生成する。
     *
     * @param worldName ワールド名（null不可）
     * @param x1        第1点 X
     * @param y1        第1点 Y
     * @param z1        第1点 Z
     * @param x2        第2点 X
     * @param y2        第2点 Y
     * @param z2        第2点 Z
     * @return 正規化済みの CuboidFieldConfig
     */
    public static CuboidFieldConfig of(String worldName,
                                       int x1, int y1, int z1,
                                       int x2, int y2, int z2) {
        return new CuboidFieldConfig(worldName,
                Math.min(x1, x2), Math.min(y1, y2), Math.min(z1, z2),
                Math.max(x1, x2), Math.max(y1, y2), Math.max(z1, z2));
    }

    /**
     * YAML セクションから {@link CuboidFieldConfig} を復元する。
     *
     * <p>セクションには {@code world}, {@code min}, {@code max} キーが必要。
     *
     * @param section YAML セクション（null不可）
     * @return 復元した設定。データ不正の場合は {@code null}
     */
    public static CuboidFieldConfig fromYaml(ConfigurationSection section) {
        Objects.requireNonNull(section, "section must not be null");
        String worldName = section.getString("world");
        List<Integer> min = section.getIntegerList("min");
        List<Integer> max = section.getIntegerList("max");
        if (worldName == null || min.size() != 3 || max.size() != 3) return null;
        return CuboidFieldConfig.of(worldName,
                min.get(0), min.get(1), min.get(2),
                max.get(0), max.get(1), max.get(2));
    }

    // ── YAML シリアライズ ──

    /**
     * {@inheritDoc}
     */
    @Override
    public void toYaml(YamlConfiguration yaml, String basePath) {
        Objects.requireNonNull(yaml, "yaml must not be null");
        Objects.requireNonNull(basePath, "basePath must not be null");
        String prefix = basePath.isEmpty() ? "" : basePath + ".";
        yaml.set(prefix + "shape", "cuboid");
        yaml.set(prefix + "world", worldName);
        yaml.set(prefix + "min", List.of(minX, minY, minZ));
        yaml.set(prefix + "max", List.of(maxX, maxY, maxZ));
    }

    // ── 形状固有メソッド ──

    /**
     * {@inheritDoc}
     */
    @Override
    public String shapeDisplayName() {
        return "直方体";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getBlockCount() {
        return (long) (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Location getCenter(World world) {
        double cx = (minX + maxX) / 2.0;
        double cy = (minY + maxY) / 2.0;
        double cz = (minZ + maxZ) / 2.0;
        return new Location(world, cx, cy, cz);
    }

    @Override
    public String toString() {
        return "CuboidFieldConfig{" +
                "world='" + worldName + '\'' +
                ", min=(" + minX + "," + minY + "," + minZ + ")" +
                ", max=(" + maxX + "," + maxY + "," + maxZ + ")" +
                ", blocks=" + getBlockCount() +
                '}';
    }
}
