package io.wax100.chipLib;

import org.bukkit.ChatColor;
import org.bukkit.Material;

/**
 * チップの額面定義（低額→高額順）。
 *
 * <p>各チップはカーペットの {@link Material} に対応し、
 * 額面・色名・チャット色が定義される。
 *
 * @see ChipManager
 */
public enum Chip {
    CHIP_1(Material.BROWN_CARPET, 1, "茶", ChatColor.DARK_RED),
    CHIP_5(Material.PURPLE_CARPET, 5, "紫", ChatColor.DARK_PURPLE),
    CHIP_10(Material.BLUE_CARPET, 10, "青", ChatColor.BLUE),
    CHIP_50(Material.LIGHT_BLUE_CARPET, 50, "水", ChatColor.AQUA),
    CHIP_100(Material.MAGENTA_CARPET, 100, "浅葱", ChatColor.DARK_AQUA),
    CHIP_500(Material.GREEN_CARPET, 500, "緑", ChatColor.DARK_GREEN),
    CHIP_1000(Material.LIME_CARPET, 1000, "黄緑", ChatColor.GREEN),
    CHIP_5000(Material.YELLOW_CARPET, 5000, "黄", ChatColor.YELLOW),
    CHIP_10000(Material.ORANGE_CARPET, 10000, "橙", ChatColor.GOLD),
    CHIP_50000(Material.PINK_CARPET, 50000, "桃", ChatColor.LIGHT_PURPLE),
    CHIP_100000(Material.RED_CARPET, 100000, "赤", ChatColor.RED),
    CHIP_500000(Material.WHITE_CARPET, 500000, "白", ChatColor.WHITE),
    CHIP_1000000(Material.BLACK_CARPET, 1000000, "黒", ChatColor.DARK_GRAY);

    /**
     * カーペットのマテリアル
     */
    private final Material material;
    /**
     * 額面（E 単位）
     */
    private final long value;
    /**
     * 色名（日本語）
     */
    private final String colorName;
    /**
     * チャット表示色
     */
    private final ChatColor chatColor;

    /**
     * コンストラクタ。
     *
     * @param material  カーペットのマテリアル
     * @param value     額面（E 単位）
     * @param colorName 色名（日本語）
     * @param chatColor チャット表示色
     */
    Chip(Material material, long value, String colorName, ChatColor chatColor) {
        this.material = material;
        this.value = value;
        this.colorName = colorName;
        this.chatColor = chatColor;
    }

    /**
     * カーペットのマテリアルを取得する。
     *
     * @return マテリアル
     */
    public Material getMaterial() {
        return material;
    }

    /**
     * 額面を取得する。
     *
     * @return 額面（E 単位）
     */
    public long getValue() {
        return value;
    }

    /**
     * 色名（日本語）を取得する。
     *
     * @return 色名
     */
    public String getColorName() {
        return colorName;
    }

    /**
     * チャット表示色を取得する。
     *
     * @return {@link ChatColor}
     */
    public ChatColor getChatColor() {
        return chatColor;
    }
}
