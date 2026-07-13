package io.wax100.arenaCore.model;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.Objects;

/**
 * 円柱フィールド設定レコード。
 *
 * <p>XZ 平面上の円形範囲と Y 軸方向の高さで定義される円柱エリア。
 * 判定には中心座標からの距離を使用し、サブブロック精度で判定を行う。
 *
 * @param worldName ワールド名
 * @param centerX   中心 X 座標
 * @param centerZ   中心 Z 座標
 * @param radius    半径（0 より大きい）
 * @param minY      最小 Y
 * @param maxY      最大 Y
 */
public record CylinderFieldConfig(
        String worldName,
        double centerX, double centerZ,
        double radius,
        int minY, int maxY
) implements ArenaFieldConfig {

    /**
     * 正規化済みの値でレコードを生成する（コンパクトコンストラクタ）。
     *
     * @throws IllegalArgumentException radius が 0 以下の場合
     */
    public CylinderFieldConfig {
        Objects.requireNonNull(worldName, "worldName must not be null");
        if (radius <= 0) {
            throw new IllegalArgumentException("radius must be > 0, got: " + radius);
        }
        if (minY > maxY) {
            int tmp = minY;
            minY = maxY;
            maxY = tmp;
        }
    }

    // ── AABB アクセサ（バウンディングボックス） ──

    /** @return AABB 最小 X（中心 − 半径を切り捨て） */
    @Override
    public int minX() {
        return (int) Math.floor(centerX - radius);
    }

    /** @return AABB 最大 X（中心 + 半径を切り捨て） */
    @Override
    public int maxX() {
        return (int) Math.floor(centerX + radius);
    }

    /** @return AABB 最小 Z（中心 − 半径を切り捨て） */
    @Override
    public int minZ() {
        return (int) Math.floor(centerZ - radius);
    }

    /** @return AABB 最大 Z（中心 + 半径を切り捨て） */
    @Override
    public int maxZ() {
        return (int) Math.floor(centerZ + radius);
    }

    // ── 形状固有メソッド ──

    /**
     * 指定座標がこの円柱フィールド内にあるかを判定する。
     *
     * <p>ワールド名の一致、Y 座標の範囲、XZ 平面上の距離を確認する。
     * サブブロック精度（{@code getX()}/{@code getZ()}）を使用する。
     *
     * @param loc 判定対象座標
     * @return フィールド内の場合 {@code true}
     */
    @Override
    public boolean contains(Location loc) {
        if (loc.getWorld() == null) return false;
        if (!loc.getWorld().getName().equals(worldName)) return false;
        int y = loc.getBlockY();
        if (y < minY || y > maxY) return false;
        double dx = loc.getX() - centerX;
        double dz = loc.getZ() - centerZ;
        return dx * dx + dz * dz <= radius * radius;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String shapeDisplayName() {
        return "円柱";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getBlockCount() {
        return (long) (Math.PI * radius * radius * (maxY - minY + 1));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Location getCenter(World world) {
        return new Location(world, centerX, (minY + maxY) / 2.0, centerZ);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public World getWorld() {
        return Bukkit.getWorld(worldName);
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
        yaml.set(prefix + "shape", "cylinder");
        yaml.set(prefix + "world", worldName);
        yaml.set(prefix + "centerX", centerX);
        yaml.set(prefix + "centerZ", centerZ);
        yaml.set(prefix + "radius", radius);
        yaml.set(prefix + "minY", minY);
        yaml.set(prefix + "maxY", maxY);
    }

    // ── ファクトリメソッド ──

    /**
     * 円柱フィールド設定を生成する。
     *
     * @param worldName ワールド名（null不可）
     * @param centerX   中心 X 座標
     * @param centerZ   中心 Z 座標
     * @param radius    半径（0 より大きい）
     * @param minY      最小 Y
     * @param maxY      最大 Y
     * @return CylinderFieldConfig
     */
    public static CylinderFieldConfig of(String worldName,
                                         double centerX, double centerZ,
                                         double radius,
                                         int minY, int maxY) {
        return new CylinderFieldConfig(worldName, centerX, centerZ, radius, minY, maxY);
    }

    /**
     * YAML セクションから {@link CylinderFieldConfig} を復元する。
     *
     * <p>セクションには {@code world}, {@code centerX}, {@code centerZ},
     * {@code radius}, {@code minY}, {@code maxY} キーが必要。
     *
     * @param section YAML セクション（null不可）
     * @return 復元した設定。データ不正の場合は {@code null}
     */
    public static CylinderFieldConfig fromYaml(ConfigurationSection section) {
        Objects.requireNonNull(section, "section must not be null");
        String worldName = section.getString("world");
        if (worldName == null) return null;
        if (!section.contains("centerX") || !section.contains("centerZ")
                || !section.contains("radius")
                || !section.contains("minY") || !section.contains("maxY")) {
            return null;
        }
        double centerX = section.getDouble("centerX");
        double centerZ = section.getDouble("centerZ");
        double radius = section.getDouble("radius");
        int minY = section.getInt("minY");
        int maxY = section.getInt("maxY");
        return CylinderFieldConfig.of(worldName, centerX, centerZ, radius, minY, maxY);
    }

    @Override
    public String toString() {
        return "CylinderFieldConfig{" +
                "world='" + worldName + '\'' +
                ", center=(" + centerX + "," + centerZ + ")" +
                ", radius=" + radius +
                ", y=[" + minY + "," + maxY + "]" +
                ", blocks≈" + getBlockCount() +
                '}';
    }
}
