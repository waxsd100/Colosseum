package io.wax100.casinoCore.manager;

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

    static {
        Chip[] values = Chip.values();
        DENOMINATIONS_DESC = new Chip[values.length];
        Set<Material> materials = new java.util.HashSet<>();
        for (int i = 0; i < values.length; i++) {
            DENOMINATIONS_DESC[i] = values[values.length - 1 - i];
            materials.add(values[i].getMaterial());
        }
        CHIP_MATERIALS = java.util.Collections.unmodifiableSet(materials);
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

    /**
     * チップアイテムを生成する
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
            item.setItemMeta(meta);
        }
        return item;
    }

    // ── アイテム生成・判定 ──

    /**
     * アイテムがカジノチップかどうか判定する
     */
    public boolean isChip(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        return item.getItemMeta().getPersistentDataContainer().has(chipKey, PersistentDataType.LONG);
    }

    /**
     * チップの額面を取得する
     */
    public long getChipValue(ItemStack item) {
        if (!isChip(item)) {
            return 0;
        }
        Long value = item.getItemMeta().getPersistentDataContainer().get(chipKey, PersistentDataType.LONG);
        return value != null ? value : 0;
    }

    /**
     * 額面からChip enumを取得する
     */
    public Chip getChipByValue(long value) {
        for (Chip chip : Chip.values()) {
            if (chip.getValue() == value) {
                return chip;
            }
        }
        return null;
    }

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

    // ── 変換ロジック ──

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
                    counts.put(chip, counts.get(chip) + item.getAmount());
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
        CHIP_1(Material.BROWN_CARPET, 1, "茶", ChatColor.DARK_GRAY),
        CHIP_5(Material.PURPLE_CARPET, 5, "紫", ChatColor.DARK_PURPLE),
        CHIP_10(Material.BLUE_CARPET, 10, "青", ChatColor.BLUE),
        CHIP_50(Material.LIGHT_BLUE_CARPET, 50, "水", ChatColor.AQUA),
        CHIP_100(Material.MAGENTA_CARPET, 100, "薄紫", ChatColor.LIGHT_PURPLE),
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
