package io.wax100.casinoCore.manager;

import de.tr7zw.changeme.nbtapi.NBT;
import de.tr7zw.changeme.nbtapi.iface.ReadWriteNBTList;
import io.wax100.casinoCore.CasinoCore;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.text.NumberFormat;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * チップの定義・生成・変換・インベントリ操作を管理するクラス
 */
public class ChipManager {

    /**
     * 額面の大きい順（貪欲法用）
     */
    private static final Chip[] DENOMINATIONS_DESC;
    /**
     * チップに使用される全マテリアル
     */
    private static final Set<Material> CHIP_MATERIALS;
    private static final NumberFormat NUMBER_FORMAT = NumberFormat.getNumberInstance(Locale.JAPAN);

    /**
     * チップ（カーペット）の CanPlaceOn に設定するブロック一覧。
     * アドベンチャーモードでカーペットを設置可能にするための主要ブロック。
     */
    private static final String[] CAN_PLACE_ON_BLOCKS = {
            // 木材系
            "minecraft:oak_planks", "minecraft:spruce_planks", "minecraft:birch_planks",
            "minecraft:jungle_planks", "minecraft:acacia_planks", "minecraft:dark_oak_planks",
            "minecraft:mangrove_planks", "minecraft:cherry_planks", "minecraft:bamboo_planks",
            "minecraft:crimson_planks", "minecraft:warped_planks",
            // 石系
            "minecraft:stone", "minecraft:cobblestone", "minecraft:stone_bricks",
            "minecraft:mossy_stone_bricks", "minecraft:smooth_stone", "minecraft:polished_andesite",
            "minecraft:polished_granite", "minecraft:polished_diorite", "minecraft:deepslate_bricks",
            "minecraft:polished_deepslate", "minecraft:polished_blackstone",
            // レンガ・テラコッタ
            "minecraft:bricks", "minecraft:terracotta", "minecraft:white_terracotta",
            "minecraft:gray_terracotta", "minecraft:black_terracotta", "minecraft:brown_terracotta",
            "minecraft:red_terracotta", "minecraft:orange_terracotta",
            // コンクリート
            "minecraft:white_concrete", "minecraft:gray_concrete", "minecraft:black_concrete",
            "minecraft:green_concrete", "minecraft:red_concrete", "minecraft:blue_concrete",
            "minecraft:light_gray_concrete", "minecraft:brown_concrete",
            // 羊毛
            "minecraft:white_wool", "minecraft:gray_wool", "minecraft:black_wool",
            "minecraft:green_wool", "minecraft:red_wool", "minecraft:blue_wool",
            "minecraft:light_gray_wool", "minecraft:brown_wool",
            // その他装飾
            "minecraft:quartz_block", "minecraft:smooth_quartz", "minecraft:sandstone",
            "minecraft:smooth_sandstone", "minecraft:red_sandstone", "minecraft:smooth_red_sandstone",
            "minecraft:prismarine", "minecraft:dark_prismarine", "minecraft:purpur_block",
            // 鉱石ブロック
            "minecraft:gold_block", "minecraft:iron_block", "minecraft:diamond_block",
            "minecraft:emerald_block", "minecraft:lapis_block", "minecraft:netherite_block",
            // 自然系
            "minecraft:dirt", "minecraft:grass_block", "minecraft:sand", "minecraft:gravel",
            "minecraft:clay", "minecraft:mud_bricks",
            // ガラス
            "minecraft:glass", "minecraft:white_stained_glass", "minecraft:gray_stained_glass",
            // カーペット自体（重ね置き用）
            "minecraft:white_carpet", "minecraft:gray_carpet", "minecraft:black_carpet",
            "minecraft:brown_carpet", "minecraft:red_carpet", "minecraft:orange_carpet",
            "minecraft:yellow_carpet", "minecraft:lime_carpet", "minecraft:green_carpet",
            "minecraft:light_blue_carpet", "minecraft:blue_carpet", "minecraft:purple_carpet",
            "minecraft:magenta_carpet", "minecraft:pink_carpet",
            // その他よく使われるブロック
            "minecraft:obsidian", "minecraft:crying_obsidian", "minecraft:end_stone_bricks",
            "minecraft:barrel", "minecraft:crafting_table", "minecraft:bookshelf",
            "minecraft:hay_block", "minecraft:dried_kelp_block"
    };

    static {
        Chip[] values = Chip.values();
        DENOMINATIONS_DESC = new Chip[values.length];
        Set<Material> materials = new HashSet<>();
        for (int i = 0; i < values.length; i++) {
            DENOMINATIONS_DESC[i] = values[values.length - 1 - i];
            materials.add(values[i].getMaterial());
        }
        CHIP_MATERIALS = Collections.unmodifiableSet(materials);
    }

    private final NamespacedKey chipKey;

    public ChipManager(CasinoCore plugin) {
        this.chipKey = new NamespacedKey(plugin, "casino_chip");
    }

    /**
     * チップに使用されるマテリアルかどうか判定する
     */
    public static boolean isChipMaterial(Material material) {
        return CHIP_MATERIALS.contains(material);
    }

    /**
     * 金額をカンマ区切りでフォーマットする
     */
    public static String formatAmount(long amount) {
        return NUMBER_FORMAT.format(amount);
    }

    // ── アイテム生成 ──

    /**
     * チップアイテムを生成する（CanPlaceOn 付き）。
     * アドベンチャーモードでブロック上に設置できるよう CanPlaceOn NBT を設定する。
     */
    public ItemStack createChipItem(Chip chip, int amount) {
        ItemStack item = new ItemStack(chip.getMaterial(), amount);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(chip.getChatColor().toString() + ChatColor.BOLD
                    + formatAmount(chip.getValue()) + " E チップ");
            meta.setLore(Arrays.asList(
                    ChatColor.GRAY + "カジノチップ",
                    ChatColor.GRAY + "額面: " + ChatColor.YELLOW + formatAmount(chip.getValue()) + " E",
                    ChatColor.GRAY + "色: " + chip.getChatColor() + chip.getColorName()
            ));
            meta.getPersistentDataContainer().set(chipKey, PersistentDataType.LONG, chip.getValue());
            meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_PLACED_ON);
            item.setItemMeta(meta);
        }
        NBT.modify(item, nbt -> {
            ReadWriteNBTList<String> canPlaceOn = nbt.getStringList("CanPlaceOn");
            for (String block : CAN_PLACE_ON_BLOCKS) {
                canPlaceOn.add(block);
            }
        });
        return item;
    }

    // ── 判定 ──

    /**
     * アイテムがカジノチップかどうか判定する
     */
    public boolean isChip(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(chipKey, PersistentDataType.LONG);
    }

    /**
     * チップの額面を取得する
     */
    public long getChipValue(ItemStack item) {
        if (!isChip(item)) return 0;
        Long value = item.getItemMeta().getPersistentDataContainer().get(chipKey, PersistentDataType.LONG);
        return value != null ? value : 0;
    }

    /**
     * 額面からChip enumを取得する
     */
    public Chip getChipByValue(long value) {
        for (Chip chip : Chip.values()) {
            if (chip.getValue() == value) return chip;
        }
        return null;
    }

    // ── 変換ロジック ──

    /**
     * 金額をチップに分割する（貪欲法: 大きい額面から順に）
     */
    public Map<Chip, Integer> breakdownAmount(long amount) {
        Map<Chip, Integer> result = new LinkedHashMap<>();
        long remaining = amount;
        for (Chip chip : DENOMINATIONS_DESC) {
            if (remaining >= chip.getValue()) {
                int count = (int) (remaining / chip.getValue());
                result.put(chip, count);
                remaining -= (long) count * chip.getValue();
            }
        }
        return result;
    }

    /**
     * チップセットに必要なインベントリスロット数を計算する
     */
    public int calculateSlotsNeeded(Map<Chip, Integer> chips) {
        int slots = 0;
        for (int count : chips.values()) {
            slots += (int) Math.ceil(count / 64.0);
        }
        return slots;
    }

    /**
     * プレイヤーの空きスロット数を数える
     */
    public int countEmptySlots(Player player) {
        int empty = 0;
        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (item == null || item.getType() == Material.AIR) {
                empty++;
            }
        }
        return empty;
    }

    // ── インベントリ操作 ──

    /**
     * プレイヤーにチップを付与する
     */
    public void giveChips(Player player, Map<Chip, Integer> chips) {
        for (Map.Entry<Chip, Integer> entry : chips.entrySet()) {
            int remaining = entry.getValue();
            while (remaining > 0) {
                int stackSize = Math.min(remaining, 64);
                player.getInventory().addItem(createChipItem(entry.getKey(), stackSize));
                remaining -= stackSize;
            }
        }
    }

    /**
     * プレイヤーのインベントリ内のチップを数える
     */
    public Map<Chip, Integer> countChips(Player player) {
        Map<Chip, Integer> counts = new LinkedHashMap<>();
        for (Chip chip : Chip.values()) {
            counts.put(chip, 0);
        }
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && isChip(item)) {
                Chip chip = getChipByValue(getChipValue(item));
                if (chip != null) {
                    counts.merge(chip, item.getAmount(), Integer::sum);
                }
            }
        }
        return counts;
    }

    /**
     * プレイヤーの手持ちチップの合計額を計算する
     */
    public long calculateTotalValue(Player player) {
        long total = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && isChip(item)) {
                total += getChipValue(item) * item.getAmount();
            }
        }
        return total;
    }

    /**
     * プレイヤーのインベントリから全チップを回収し、内訳を返す
     */
    public Map<Chip, Integer> removeAllChips(Player player) {
        Map<Chip, Integer> removed = countChips(player);
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            if (contents[i] != null && isChip(contents[i])) {
                player.getInventory().setItem(i, null);
            }
        }
        return removed;
    }

    /**
     * チップの額面定義（低額→高額順）
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

        private final Material material;
        private final long value;
        private final String colorName;
        private final ChatColor chatColor;

        Chip(Material material, long value, String colorName, ChatColor chatColor) {
            this.material = material;
            this.value = value;
            this.colorName = colorName;
            this.chatColor = chatColor;
        }

        public Material getMaterial() {
            return material;
        }

        public long getValue() {
            return value;
        }

        public String getColorName() {
            return colorName;
        }

        public ChatColor getChatColor() {
            return chatColor;
        }
    }
}
