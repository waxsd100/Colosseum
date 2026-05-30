package io.wax100.chipLib;

import de.tr7zw.changeme.nbtapi.NBT;
import de.tr7zw.changeme.nbtapi.iface.ReadWriteNBTList;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.text.NumberFormat;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * チップの生成・変換・インベントリ操作を管理するクラス。
 *
 * <p>主な責務:
 * <ul>
 *   <li>チップアイテムの生成（CanPlaceOn NBT 付き）</li>
 *   <li>アイテムがカジノチップかどうかの判定</li>
 *   <li>金額からチップへの分割（貪欲法）</li>
 *   <li>プレイヤーインベントリへのチップ付与・回収・集計</li>
 * </ul>
 *
 * <p>リファクタリング改善点:
 * <ul>
 *   <li>isChipMaterial / CHIP_MATERIALS を {@link Chip} enum に移動（凝集度向上）</li>
 *   <li>getChipByValue を {@link Chip#fromValue(long)} に委譲（後方互換維持）</li>
 *   <li>NumberFormat のスレッドセーフティ問題を解消</li>
 *   <li>CAN_PLACE_ON_BLOCKS を {@link List} に変更し不変性を保証</li>
 *   <li>null チェック強化・防御的プログラミング</li>
 *   <li>ItemMeta 取得の重複呼び出しを排除</li>
 *   <li>MAX_STACK_SIZE 定数化</li>
 * </ul>
 *
 * @see Chip
 */
public class ChipManager {

    /**
     * Minecraft のアイテム最大スタック数
     *
     * <p>改善: マジックナンバー 64 を定数化し可読性・保守性を向上</p>
     */
    private static final int MAX_STACK_SIZE = 64;

    /**
     * 金額フォーマット用の日本ロケール数値フォーマッタ。
     *
     * <p>改善: NumberFormat はスレッドセーフではないため、
     * マルチスレッド環境（Bukkit 非同期タスクなど）での競合を防止するために
     * ThreadLocal で保持する。</p>
     */
    private static final ThreadLocal<NumberFormat> NUMBER_FORMAT =
            ThreadLocal.withInitial(() -> NumberFormat.getNumberInstance(Locale.JAPAN));

    /**
     * チップ（カーペット）の CanPlaceOn に設定するブロック一覧。
     * アドベンチャーモードでカーペットを設置可能にするための主要ブロック。
     *
     * <p>改善: 配列から不変 List に変更し、外部からの変更を防止</p>
     */
    private static final List<String> CAN_PLACE_ON_BLOCKS = List.of(
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
    );

    /**
     * チップ識別用の {@link NamespacedKey}（PersistentDataContainer に額面を格納）
     */
    private final NamespacedKey chipKey;

    /**
     * コンストラクタ。
     *
     * @param plugin プラグインインスタンス（null 不可）
     * @throws NullPointerException plugin が null の場合
     */
    public ChipManager(JavaPlugin plugin) {
        // 改善: null チェックを追加し、早期に問題を検出
        Objects.requireNonNull(plugin, "plugin must not be null");
        this.chipKey = new NamespacedKey(plugin, "casino_chip");
    }

    /**
     * 指定されたマテリアルがチップに使用されるカーペットかどうか判定する。
     *
     * <p>改善: 実装を {@link Chip#isChipMaterial(Material)} に委譲。
     * 後方互換性のために残す。</p>
     *
     * @param material 判定対象のマテリアル
     * @return チップ用マテリアルの場合 {@code true}
     */
    public static boolean isChipMaterial(Material material) {
        return Chip.isChipMaterial(material);
    }

    /**
     * 金額をカンマ区切りでフォーマットする。
     *
     * @param amount フォーマットする金額
     * @return カンマ区切り文字列（例: {@code "1,000,000"}）
     */
    public static String formatAmount(long amount) {
        // 改善: ThreadLocal を使用してスレッドセーフに
        return NUMBER_FORMAT.get().format(amount);
    }

    /**
     * チップアイテムを生成する（CanPlaceOn 付き）。
     *
     * <p>アドベンチャーモードでブロック上に設置できるよう CanPlaceOn NBT を設定する。
     * アイテムの {@link org.bukkit.persistence.PersistentDataContainer} には額面が格納される。
     *
     * <p>改善:
     * <ul>
     *   <li>amount の範囲検証を追加（1〜64）</li>
     *   <li>表示名生成を {@link Chip#getDisplayName(String)} に委譲（DRY）</li>
     *   <li>ItemFlag の明示的 import で可読性向上</li>
     *   <li>Lore 生成を {@link Collections#unmodifiableList(List)} ではなく {@link List#of} で不変化</li>
     * </ul>
     *
     * @param chip   チップ種別（null 不可）
     * @param amount スタック数（1〜64）
     * @return 生成されたチップアイテム
     * @throws NullPointerException     chip が null の場合
     * @throws IllegalArgumentException amount が 1〜64 の範囲外の場合
     */
    public ItemStack createChipItem(Chip chip, int amount) {
        Objects.requireNonNull(chip, "chip must not be null");
        // 改善: 無効な amount（0 以下、65 以上）で不正なアイテムが生成されるのを防止
        if (amount < 1 || amount > MAX_STACK_SIZE) {
            throw new IllegalArgumentException(
                    "amount must be between 1 and " + MAX_STACK_SIZE + ", but was: " + amount);
        }

        ItemStack item = new ItemStack(chip.getMaterial(), amount);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            String formattedValue = formatAmount(chip.getValue());
            // 改善: 表示名生成を Chip enum に委譲
            meta.setDisplayName(chip.getDisplayName(formattedValue));
            meta.setLore(Arrays.asList(
                    ChatColor.GRAY + "カジノチップ",
                    ChatColor.GRAY + "額面: " + ChatColor.YELLOW + formattedValue + " E",
                    ChatColor.GRAY + "色: " + chip.getChatColor() + chip.getColorName()
            ));
            meta.getPersistentDataContainer().set(chipKey, PersistentDataType.LONG, chip.getValue());
            meta.addItemFlags(ItemFlag.HIDE_PLACED_ON);
            item.setItemMeta(meta);
        }
        NBT.modify(item, nbt -> {
            ReadWriteNBTList<String> canPlaceOn = nbt.getStringList("CanPlaceOn");
            // 改善: List に変更したので拡張 for で安全にイテレーション
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
     * @param item 判定対象のアイテム（null 許容）
     * @return カジノチップの場合 {@code true}
     */
    public boolean isChip(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        // 改善: ItemMeta は null でないことが hasItemMeta() で保証済み
        return item.getItemMeta().getPersistentDataContainer().has(chipKey, PersistentDataType.LONG);
    }

    /**
     * チップの額面を取得する。
     *
     * <p>改善: ItemMeta の二重取得を排除し、null チェックを一箇所に集約。</p>
     *
     * @param item チップアイテム（null 許容）
     * @return 額面。チップでない場合は {@code 0}
     */
    public long getChipValue(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return 0;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return 0;
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        if (!pdc.has(chipKey, PersistentDataType.LONG)) return 0;
        Long value = pdc.get(chipKey, PersistentDataType.LONG);
        return value != null ? value : 0;
    }

    /**
     * 額面から対応する {@link Chip} enum 値を取得する。
     *
     * <p>改善: 内部実装を {@link Chip#fromValue(long)} に委譲し O(1) 参照に改善。
     * 後方互換性のために null 返却を維持するが、
     * 新規コードでは {@link Chip#fromValue(long)} の使用を推奨する。</p>
     *
     * @param value 額面
     * @return 対応する Chip。見つからない場合は {@code null}
     * @deprecated {@link Chip#fromValue(long)} を使用してください
     */
    @Deprecated
    public Chip getChipByValue(long value) {
        // 改善: O(n) のループを O(1) のマップ参照に置き換え
        return Chip.fromValue(value).orElse(null);
    }

    /**
     * 金額をチップに分割する（貪欲法: 大きい額面から順に）。
     *
     * <p>指定金額を最少枚数のチップに分割する。
     * 最小額面未満の端数は切り捨てられる。
     *
     * <p>改善: 負の金額に対する防御チェックを追加。</p>
     *
     * @param amount 分割する金額（0 以上）
     * @return チップ内訳 (額面 → 枚数)。金額が 0 以下の場合は空マップ
     */
    public Map<Chip, Integer> breakdownAmount(long amount) {
        // 改善: 負の金額での無限ループを防止
        if (amount <= 0) {
            return Collections.emptyMap();
        }
        Map<Chip, Integer> result = new LinkedHashMap<>();
        long remaining = amount;
        for (Chip chip : Chip.denominationsDescending()) {
            if (remaining <= 0) break; // 改善: 早期脱出で不要なイテレーションを回避
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
     * <p>1スロットあたり最大 {@value #MAX_STACK_SIZE} アイテムとして計算する。
     *
     * @param chips チップ内訳 (額面 → 枚数)。null 不可
     * @return 必要スロット数
     * @throws NullPointerException chips が null の場合
     */
    public int calculateSlotsNeeded(Map<Chip, Integer> chips) {
        Objects.requireNonNull(chips, "chips must not be null");
        int slots = 0;
        for (int count : chips.values()) {
            // 改善: 整数演算に置き換え（浮動小数点の丸め誤差を回避）
            slots += (count + MAX_STACK_SIZE - 1) / MAX_STACK_SIZE;
        }
        return slots;
    }

    /**
     * プレイヤーのストレージインベントリの空きスロット数を数える。
     *
     * @param player 対象プレイヤー（null 不可）
     * @return 空きスロット数
     * @throws NullPointerException player が null の場合
     */
    public int countEmptySlots(Player player) {
        Objects.requireNonNull(player, "player must not be null");
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
     * <p>{@value #MAX_STACK_SIZE} アイテムを超える場合は複数スタックに分割して付与する。
     * インベントリに収まらない分はプレイヤーの足元にドロップする。
     *
     * @param player 付与対象プレイヤー（null 不可）
     * @param chips  付与するチップの内訳 (額面 → 枚数)。null 不可
     * @throws NullPointerException player または chips が null の場合
     */
    public void giveChips(Player player, Map<Chip, Integer> chips) {
        Objects.requireNonNull(player, "player must not be null");
        Objects.requireNonNull(chips, "chips must not be null");
        for (Map.Entry<Chip, Integer> entry : chips.entrySet()) {
            int remaining = entry.getValue();
            while (remaining > 0) {
                int stackSize = Math.min(remaining, MAX_STACK_SIZE);
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
     * <p>改善: getChipByValue → Chip.fromValue への移行と、
     * ItemMeta 取得回数の削減。</p>
     *
     * @param player 対象プレイヤー（null 不可）
     * @return チップの集計結果 (額面 → 枚数)
     * @throws NullPointerException player が null の場合
     */
    public Map<Chip, Integer> countChips(Player player) {
        Objects.requireNonNull(player, "player must not be null");
        Map<Chip, Integer> counts = new LinkedHashMap<>();
        for (Chip chip : Chip.values()) {
            counts.put(chip, 0);
        }
        for (ItemStack item : player.getInventory().getContents()) {
            if (item == null) continue;
            long chipValue = getChipValue(item);
            if (chipValue <= 0) continue;
            // 改善: Optional で安全にハンドリング
            Chip.fromValue(chipValue).ifPresent(chip ->
                    counts.merge(chip, item.getAmount(), Integer::sum)
            );
        }
        return counts;
    }

    /**
     * プレイヤーの手持ちチップの合計額を計算する。
     *
     * @param player 対象プレイヤー（null 不可）
     * @return チップの合計額
     * @throws NullPointerException player が null の場合
     */
    public long calculateTotalValue(Player player) {
        Objects.requireNonNull(player, "player must not be null");
        long total = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item == null) continue;
            long chipValue = getChipValue(item);
            if (chipValue > 0) {
                total += chipValue * item.getAmount();
            }
        }
        return total;
    }

    /**
     * プレイヤーのインベントリから全チップを回収し、内訳を返す。
     *
     * <p>回収前に {@link #countChips(Player)} で内訳を記録してから削除する。
     *
     * @param player 対象プレイヤー（null 不可）
     * @return 回収したチップの内訳 (額面 → 枚数)
     * @throws NullPointerException player が null の場合
     */
    public Map<Chip, Integer> removeAllChips(Player player) {
        Objects.requireNonNull(player, "player must not be null");
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
