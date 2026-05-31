package io.wax100.arenaCore.model;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

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
) {

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
     * 指定座標がこの戦闘エリア内にあるかを判定する。
     *
     * @param loc 判定対象座標
     * @return エリア内の場合 {@code true}
     */
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
