package io.wax100.chipLib;

import de.tr7zw.changeme.nbtapi.NBT;
import de.tr7zw.changeme.nbtapi.iface.ReadWriteNBTList;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.text.NumberFormat;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * チップの定義・生成・変換・インベントリ操作を管理するクラス。
 *
 * <p>主な責務:
 * <ul>
 *   <li>チップアイテムの生成（CanPlaceOn NBT 付き）</li>
 *   <li>アイテムがカジノチップかどうかの判定</li>
 *   <li>金額からチップへの分割（貪欲法）</li>
 *   <li>プレイヤーインベントリへのチップ付与・回収・集計</li>
 * </ul>
 *
 * <p>チップはカーペットアイテムとして実装され、{@link Chip} enum で額面と色が定義される。
 *
 * @see Chip
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
    /**
     * 金額フォーマット用の日本ロケール数値フォーマッタ
     */
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

    /**
     * チップ識別用の {@link NamespacedKey}（PersistentDataContainer に額面を格納）
     */
    private final NamespacedKey chipKey;

    /**
     * コンストラクタ。
     *
     * @param plugin プラグインインスタンス
     */
    public ChipManager(JavaPlugin plugin) {
        this.chipKey = new NamespacedKey(plugin, "casino_chip");
    }

    /**
     * 指定されたマテリアルがチップに使用されるカーペットかどうか判定する。
     *
     * @param material 判定対象のマテリアル
     * @return チップ用マテリアルの場合 {@code true}
     */
    public static boolean isChipMaterial(Material material) {
        return CHIP_MATERIALS.contains(material);
    }

    /**
     * 金額をカンマ区切りでフォーマットする。
     *
     * @param amount フォーマットする金額
     * @return カンマ区切り文字列（例: {@code "1,000,000"}）
     */
    public static String formatAmount(long amount) {
        return NUMBER_FORMAT.format(amount);
    }

    /**
     * チップアイテムを生成する（CanPlaceOn 付き）。
     *
     * <p>アドベンチャーモードでブロック上に設置できるよう CanPlaceOn NBT を設定する。
     * アイテムの {@link org.bukkit.persistence.PersistentDataContainer} には額面が格納される。
     *
     * @param chip   チップ種別
     * @param amount スタック数
     * @return 生成されたチップアイテム
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

    /**
     * アイテムがカジノチップかどうか判定する。
     *
     * <p>{@link org.bukkit.persistence.PersistentDataContainer} にチップキーが存在するかで判定する。
     *
     * @param item 判定対象のアイテム
     * @return カジノチップの場合 {@code true}
     */
    public boolean isChip(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(chipKey, PersistentDataType.LONG);
    }

    /**
     * チップの額面を取得する。
     *
     * @param item チップアイテム
     * @return 額面。チップでない場合は {@code 0}
     */
    public long getChipValue(ItemStack item) {
        if (!isChip(item)) return 0;
        Long value = item.getItemMeta().getPersistentDataContainer().get(chipKey, PersistentDataType.LONG);
        return value != null ? value : 0;
    }

    /**
     * 額面から対応する {@link Chip} enum 値を取得する。
     *
     * @param value 額面
     * @return 対応する Chip。見つからない場合は {@code null}
     */
    public Chip getChipByValue(long value) {
        for (Chip chip : Chip.values()) {
            if (chip.getValue() == value) return chip;
        }
        return null;
    }

    /**
     * 金額をチップに分割する（貪欲法: 大きい額面から順に）。
     *
     * <p>指定金額を最少枚数のチップに分割する。
     * 最小額面未満の端数は切り捨てられる。
     *
     * @param amount 分割する金額
     * @return チップ内訳 (額面 → 枚数)
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
     * チップセットに必要なインベントリスロット数を計算する。
     *
     * <p>1スロットあたり最大 64 アイテムとして計算する。
     *
     * @param chips チップ内訳 (額面 → 枚数)
     * @return 必要スロット数
     */
    public int calculateSlotsNeeded(Map<Chip, Integer> chips) {
        int slots = 0;
        for (int count : chips.values()) {
            slots += (int) Math.ceil(count / 64.0);
        }
        return slots;
    }

    /**
     * プレイヤーのストレージインベントリの空きスロット数を数える。
     *
     * @param player 対象プレイヤー
     * @return 空きスロット数
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

    /**
     * プレイヤーにチップを付与する。
     *
     * <p>64 アイテムを超える場合は複数スタックに分割して付与する。
     *
     * @param player 付与対象プレイヤー
     * @param chips  付与するチップの内訳 (額面 → 枚数)
     */
    public void giveChips(Player player, Map<Chip, Integer> chips) {
        for (Map.Entry<Chip, Integer> entry : chips.entrySet()) {
            int remaining = entry.getValue();
            while (remaining > 0) {
                int stackSize = Math.min(remaining, 64);
                ItemStack item = createChipItem(entry.getKey(), stackSize);
                Map<Integer, ItemStack> remainingItems = player.getInventory().addItem(item);
                if (!remainingItems.isEmpty()) {
                    for (ItemStack leftover : remainingItems.values()) {
                        player.getWorld().dropItemNaturally(player.getLocation(), leftover);
                    }
                }
                remaining -= stackSize;
            }
        }
    }

    /**
     * プレイヤーのインベントリ内のチップを種類別に数える。
     *
     * <p>全 {@link Chip} 種別のエントリが含まれる（所持が 0 の種別も含む）。
     *
     * @param player 対象プレイヤー
     * @return チップの集計結果 (額面 → 枚数)
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
                    counts.put(chip, counts.getOrDefault(chip, 0) + item.getAmount());
                }
            }
        }
        return counts;
    }

    /**
     * プレイヤーの手持ちチップの合計額を計算する。
     *
     * @param player 対象プレイヤー
     * @return チップの合計額
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
     * プレイヤーのインベントリから全チップを回収し、内訳を返す。
     *
     * <p>回収前に {@link #countChips(Player)} で内訳を記録してから削除する。
     *
     * @param player 対象プレイヤー
     * @return 回収したチップの内訳 (額面 → 枚数)
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
}
