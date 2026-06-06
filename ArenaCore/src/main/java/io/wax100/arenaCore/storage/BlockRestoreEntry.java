package io.wax100.arenaCore.storage;

/**
 * ブロック復元エントリのデータレコード。
 *
 * <p>{@link TerrainStorageProvider} の実装間で共有されるイミュータブルなデータホルダー。
 * Redis Sorted Set への書き込み時は {@link #serialize()} で値文字列に変換し、
 * 読み出し時は {@link #deserialize(String, long)} で復元する。
 *
 * <p>フォーマット: {@code "worldName,x,y,z,blockDataString"}
 *
 * @param worldName       ワールド名
 * @param x               X 座標
 * @param y               Y 座標
 * @param z               Z 座標
 * @param blockDataString {@code BlockData#getAsString()} の結果
 * @param restoreAtTick   復元予定の tick（設置ブロックは {@link Long#MAX_VALUE}）
 */
public record BlockRestoreEntry(
    String worldName,
    int x, int y, int z,
    String blockDataString,
    long restoreAtTick
) {

    /**
     * Redis Sorted Set の値としてシリアライズする。
     *
     * @return {@code "worldName,x,y,z,blockDataString"} 形式の文字列
     */
    public String serialize() {
        return worldName + "," + x + "," + y + "," + z + "," + blockDataString;
    }

    /**
     * Redis Sorted Set の値からデシリアライズする。
     *
     * @param value         {@link #serialize()} で生成された文字列
     * @param restoreAtTick 復元予定の tick（Sorted Set の score から取得）
     * @return 復元されたエントリ
     * @throws NumberFormatException    座標のパースに失敗した場合
     * @throws StringIndexOutOfBoundsException フォーマットが不正な場合
     */
    public static BlockRestoreEntry deserialize(String value, long restoreAtTick) {
        int firstComma = value.indexOf(',');
        int secondComma = value.indexOf(',', firstComma + 1);
        int thirdComma = value.indexOf(',', secondComma + 1);
        int fourthComma = value.indexOf(',', thirdComma + 1);

        String world = value.substring(0, firstComma);
        int x = Integer.parseInt(value.substring(firstComma + 1, secondComma));
        int y = Integer.parseInt(value.substring(secondComma + 1, thirdComma));
        int z = Integer.parseInt(value.substring(thirdComma + 1, fourthComma));
        String blockData = value.substring(fourthComma + 1);

        return new BlockRestoreEntry(world, x, y, z, blockData, restoreAtTick);
    }
}
