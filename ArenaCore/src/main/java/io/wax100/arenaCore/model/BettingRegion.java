package io.wax100.arenaCore.model;

import org.bukkit.Location;
import org.bukkit.World;

import java.util.Objects;

/**
 * チームの賭けエリア（直方体）を表すモデル。
 *
 * <p>WorldEdit で選択した範囲の2点を保持し、
 * カーペット設置時に座標が範囲内かを判定する。
 */
public class BettingRegion {

    private final String teamName;
    private final String worldName;
    private final int minX, minY, minZ;
    private final int maxX, maxY, maxZ;

    /**
     * @param teamName  チーム名（null不可）
     * @param worldName ワールド名（null不可）
     * @param x1        第1点 X
     * @param y1        第1点 Y
     * @param z1        第1点 Z
     * @param x2        第2点 X
     * @param y2        第2点 Y
     * @param z2        第2点 Z
     * @throws NullPointerException teamName または worldName が null の場合
     */
    public BettingRegion(String teamName, String worldName,
                         int x1, int y1, int z1,
                         int x2, int y2, int z2) {
        this.teamName = Objects.requireNonNull(teamName, "teamName must not be null");
        this.worldName = Objects.requireNonNull(worldName, "worldName must not be null");
        this.minX = Math.min(x1, x2);
        this.minY = Math.min(y1, y2);
        this.minZ = Math.min(z1, z2);
        this.maxX = Math.max(x1, x2);
        this.maxY = Math.max(y1, y2);
        this.maxZ = Math.max(z1, z2);
    }

    /**
     * 指定座標がこの賭けエリア内にあるかを判定する。
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

    public String getTeamName() { return teamName; }
    public String getWorldName() { return worldName; }
    public int getMinX() { return minX; }
    public int getMinY() { return minY; }
    public int getMinZ() { return minZ; }
    public int getMaxX() { return maxX; }
    public int getMaxY() { return maxY; }
    public int getMaxZ() { return maxZ; }

    /**
     * teamName に基づく等価性判定（1チーム = 1リージョン）。
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BettingRegion)) return false;
        BettingRegion that = (BettingRegion) o;
        return teamName.equals(that.teamName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(teamName);
    }

    @Override
    public String toString() {
        return "BettingRegion{" +
                "teamName='" + teamName + '\'' +
                ", world='" + worldName + '\'' +
                ", min=(" + minX + "," + minY + "," + minZ + ")" +
                ", max=(" + maxX + "," + maxY + "," + maxZ + ")" +
                '}';
    }
}
