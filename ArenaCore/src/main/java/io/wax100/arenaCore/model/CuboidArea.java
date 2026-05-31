package io.wax100.arenaCore.model;

import org.bukkit.Location;

/**
 * 軸平行境界ボックス（AABB）の共通インターフェース。
 *
 * <p>{@link ArenaFieldConfig}、{@link BettingRegion}、{@link TeamAreaConfig}
 * が共通で持つ直方体エリア判定を提供する。
 */
public interface CuboidArea {

    String worldName();
    int minX();
    int minY();
    int minZ();
    int maxX();
    int maxY();
    int maxZ();

    /**
     * 指定座標がこのエリア内にあるかを判定する。
     *
     * @param loc 判定対象座標
     * @return エリア内の場合 {@code true}
     */
    default boolean contains(Location loc) {
        if (loc.getWorld() == null) return false;
        if (!loc.getWorld().getName().equals(worldName())) return false;
        int x = loc.getBlockX();
        int y = loc.getBlockY();
        int z = loc.getBlockZ();
        return x >= minX() && x <= maxX()
                && y >= minY() && y <= maxY()
                && z >= minZ() && z <= maxZ();
    }
}
