package io.wax100.arenaCore.model;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.List;
import java.util.Objects;

/**
 * 戦闘エリアの軸平行境界ボックス（AABB）を保持する設定レコード。
 *
 * <p>フィールド範囲内のブロック変更を地形復元の対象とするために使用する。
 * WorldEdit の {@link CuboidRegion} への変換もサポートする。
 *
 * <p>インスタンスは {@link #of(String, int, int, int, int, int, int)} で生成する。
 *
 * @param worldName ワールド名
 * @param minX      最小 X
 * @param minY      最小 Y
 * @param minZ      最小 Z
 * @param maxX      最大 X
 * @param maxY      最大 Y
 * @param maxZ      最大 Z
 */
public record ArenaFieldConfig(
        String worldName,
        int minX, int minY, int minZ,
        int maxX, int maxY, int maxZ
) implements CuboidArea {

    /**
     * 正規化済みの値でレコードを生成する（コンパクトコンストラクタ）。
     */
    public ArenaFieldConfig {
        Objects.requireNonNull(worldName, "worldName must not be null");
    }

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
     * @return 正規化済みの ArenaFieldConfig
     */
    public static ArenaFieldConfig of(String worldName,
                                               int x1, int y1, int z1,
                                               int x2, int y2, int z2) {
        return new ArenaFieldConfig(worldName,
                Math.min(x1, x2), Math.min(y1, y2), Math.min(z1, z2),
                Math.max(x1, x2), Math.max(y1, y2), Math.max(z1, z2));
    }

    /**
     * YAML セクションから {@link ArenaFieldConfig} を復元する。
     *
     * <p>セクションには {@code world}, {@code min}, {@code max} キーが必要。
     *
     * @param section YAML セクション（null不可）
     * @return 復元した設定。データ不正の場合は {@code null}
     */
    public static ArenaFieldConfig fromYaml(ConfigurationSection section) {
        Objects.requireNonNull(section, "section must not be null");
        String worldName = section.getString("world");
        List<Integer> min = section.getIntegerList("min");
        List<Integer> max = section.getIntegerList("max");
        if (worldName == null || min.size() != 3 || max.size() != 3) return null;
        return ArenaFieldConfig.of(worldName,
                min.get(0), min.get(1), min.get(2),
                max.get(0), max.get(1), max.get(2));
    }

    /**
     * この設定を YAML に書き出す。
     *
     * @param yaml     書き出し先の YamlConfiguration（null不可）
     * @param basePath キーの接頭辞（例: {@code "field"}）
     */
    public void toYaml(YamlConfiguration yaml, String basePath) {
        Objects.requireNonNull(yaml, "yaml must not be null");
        Objects.requireNonNull(basePath, "basePath must not be null");
        String prefix = basePath.isEmpty() ? "" : basePath + ".";
        yaml.set(prefix + "world", worldName);
        yaml.set(prefix + "min", List.of(minX, minY, minZ));
        yaml.set(prefix + "max", List.of(maxX, maxY, maxZ));
    }

    /**
     * このフィールドのワールドを取得する。
     *
     * @return Bukkit ワールド。未ロードの場合は {@code null}
     */
    public World getWorld() {
        return Bukkit.getWorld(worldName);
    }

    /**
     * WorldEdit の {@link CuboidRegion} に変換する（ワールド付き）。
     *
     * @return CuboidRegion
     * @throws NullPointerException ワールドが未ロードの場合
     */
    public CuboidRegion toRegion() {
        com.sk89q.worldedit.world.World weWorld =
                BukkitAdapter.adapt(Objects.requireNonNull(getWorld(),
                        "World not loaded: " + worldName));
        return new CuboidRegion(weWorld,
                BlockVector3.at(minX, minY, minZ),
                BlockVector3.at(maxX, maxY, maxZ));
    }

    /**
     * エリア内のブロック数を返す。
     *
     * @return ブロック数
     */
    public long getBlockCount() {
        return (long) (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
    }

    /**
     * フィールド中心座標を返す。
     *
     * @param world 対象ワールド
     * @return フィールド中心の Location
     */
    public org.bukkit.Location getCenter(World world) {
        double cx = (minX + maxX) / 2.0;
        double cy = (minY + maxY) / 2.0;
        double cz = (minZ + maxZ) / 2.0;
        return new org.bukkit.Location(world, cx, cy, cz);
    }

    @Override
    public String toString() {
        return "ArenaFieldConfig{" +
                "world='" + worldName + '\'' +
                ", min=(" + minX + "," + minY + "," + minZ + ")" +
                ", max=(" + maxX + "," + maxY + "," + maxZ + ")" +
                ", blocks=" + getBlockCount() +
                '}';
    }
}
