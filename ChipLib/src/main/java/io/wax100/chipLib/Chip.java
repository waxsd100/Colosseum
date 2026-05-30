package io.wax100.chipLib;

import org.bukkit.ChatColor;
import org.bukkit.Material;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * チップの額面定義（低額→高額順）。
 *
 * <p>各チップはカーペットの {@link Material} に対応し、
 * 額面・色名・チャット色が定義される。
 *
 * <p>リファクタリング改善点:
 * <ul>
 *   <li>VALUE_MAP / MATERIAL_MAP による O(1) 逆引きキャッシュを追加</li>
 *   <li>{@link #fromValue(long)} / {@link #fromMaterial(Material)} で Optional を返す安全な API</li>
 *   <li>{@link #getDisplayName()} で表示名生成ロジックを enum に集約（SRP）</li>
 *   <li>CHIP_MATERIALS を Chip 側に移動し、ChipManager の責務を軽減</li>
 *   <li>DENOMINATIONS_DESC（降順配列）を Chip 側に移動（貪欲法分割で使用）</li>
 * </ul>
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

    // ── ルックアップキャッシュ ──
    // 改善: 毎回 values() をループする O(n) 検索を O(1) の Map 参照に置き換え

    /** 額面 → Chip の逆引きマップ */
    private static final Map<Long, Chip> VALUE_MAP;
    /** マテリアル → Chip の逆引きマップ */
    private static final Map<Material, Chip> MATERIAL_MAP;
    /** チップに使用される全マテリアルの不変セット */
    private static final Set<Material> CHIP_MATERIALS;
    /** 額面の大きい順（貪欲法用）の不変配列 */
    private static final Chip[] DENOMINATIONS_DESC;

    static {
        Chip[] values = values();
        Map<Long, Chip> valueMap = new HashMap<>(values.length * 2);
        Map<Material, Chip> materialMap = new EnumMap<>(Material.class);
        Set<Material> materials = EnumSet.noneOf(Material.class);

        for (Chip chip : values) {
            valueMap.put(chip.value, chip);
            materialMap.put(chip.material, chip);
            materials.add(chip.material);
        }

        VALUE_MAP = Collections.unmodifiableMap(valueMap);
        MATERIAL_MAP = Collections.unmodifiableMap(materialMap);
        // 改善: EnumSet は HashSet より省メモリかつ高速
        CHIP_MATERIALS = Collections.unmodifiableSet(materials);

        // 降順配列の構築
        DENOMINATIONS_DESC = new Chip[values.length];
        for (int i = 0; i < values.length; i++) {
            DENOMINATIONS_DESC[i] = values[values.length - 1 - i];
        }
    }

    /** カーペットのマテリアル */
    private final Material material;
    /** 額面（E 単位） */
    private final long value;
    /** 色名（日本語） */
    private final String colorName;
    /** チャット表示色 */
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

    // ── Static ファクトリメソッド ──

    /**
     * 額面から対応する {@link Chip} を取得する。
     *
     * <p>改善: null を返す代わりに {@link Optional} を返すことで null 安全性を向上。
     * 内部的には O(1) の HashMap 参照。
     *
     * @param value 額面
     * @return 対応する Chip を含む Optional。見つからない場合は空
     */
    public static Optional<Chip> fromValue(long value) {
        return Optional.ofNullable(VALUE_MAP.get(value));
    }

    /**
     * マテリアルから対応する {@link Chip} を取得する。
     *
     * @param material マテリアル
     * @return 対応する Chip を含む Optional。見つからない場合は空
     */
    public static Optional<Chip> fromMaterial(Material material) {
        if (material == null) return Optional.empty();
        return Optional.ofNullable(MATERIAL_MAP.get(material));
    }

    /**
     * 指定されたマテリアルがチップに使用されるカーペットかどうか判定する。
     *
     * <p>改善: ChipManager から Chip enum に移動。チップ定義に関する判定は
     * enum 自身が持つべき責務（凝集度向上）。
     *
     * @param material 判定対象のマテリアル
     * @return チップ用マテリアルの場合 {@code true}
     */
    public static boolean isChipMaterial(Material material) {
        return CHIP_MATERIALS.contains(material);
    }

    /**
     * 額面の大きい順に並んだ配列を取得する（貪欲法用）。
     *
     * <p>防御的コピーを返すため、呼び出し元が配列を変更しても
     * 内部状態には影響しない。
     *
     * @return 額面降順の Chip 配列（防御的コピー）
     */
    public static Chip[] denominationsDescending() {
        return DENOMINATIONS_DESC.clone();
    }

    // ── インスタンスメソッド ──

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

    /**
     * チップの表示名を取得する（例: {@code "§c§l100,000 E チップ"}）。
     *
     * <p>改善: 表示名の生成ロジックを ChipManager.createChipItem() から分離し、
     * enum 自身に持たせることで DRY 原則を遵守。
     *
     * @param formattedValue フォーマット済みの額面文字列
     * @return チャット色付きの表示名
     */
    public String getDisplayName(String formattedValue) {
        return chatColor.toString() + ChatColor.BOLD + formattedValue + " E チップ";
    }
}
