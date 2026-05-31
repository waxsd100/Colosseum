package io.wax100.arenaCore.model;


import java.util.Objects;

/**
 * チームの賭けエリア（直方体）を表すレコード。
 *
 * <p>WorldEdit で選択した範囲の2点を保持し、
 * カーペット設置時に座標が範囲内かを判定する。
 *
 * <p>インスタンスは {@link #of(String, String, int, int, int, int, int, int)} で生成する。
 *
 * @param teamName  チーム名
 * @param worldName ワールド名
 * @param minX      最小 X
 * @param minY      最小 Y
 * @param minZ      最小 Z
 * @param maxX      最大 X
 * @param maxY      最大 Y
 * @param maxZ      最大 Z
 */
public record BettingRegion(
        String teamName, String worldName,
        int minX, int minY, int minZ,
        int maxX, int maxY, int maxZ
) implements CuboidArea {

    /**
     * 正規化済みの値でレコードを生成する（コンパクトコンストラクタ）。
     */
    public BettingRegion {
        Objects.requireNonNull(teamName, "teamName must not be null");
        Objects.requireNonNull(worldName, "worldName must not be null");
    }

    /**
     * 2点の座標から最小・最大を自動計算してインスタンスを生成する。
     *
     * @param teamName  チーム名（null不可）
     * @param worldName ワールド名（null不可）
     * @param x1        第1点 X
     * @param y1        第1点 Y
     * @param z1        第1点 Z
     * @param x2        第2点 X
     * @param y2        第2点 Y
     * @param z2        第2点 Z
     * @return 正規化済みの BettingRegion
     */
    public static BettingRegion of(String teamName, String worldName,
                                   int x1, int y1, int z1,
                                   int x2, int y2, int z2) {
        return new BettingRegion(teamName, worldName,
                Math.min(x1, x2), Math.min(y1, y2), Math.min(z1, z2),
                Math.max(x1, x2), Math.max(y1, y2), Math.max(z1, z2));
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
