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
import org.bukkit.persistence.PersistentDataType;

import java.util.Arrays;

/**
 * チップ回収用ハサミの生成・配布・回収を担当するヘルパークラス。
 *
 * <p>アドベンチャーモードでカーペット（チップ）を破壊するために使用する。
 * CanDestroy NBT で全チップカーペットを破壊可能にする。
 *
 * @see ChipManager
 * @see ChipPlugin
 */
public class ShearsHelper {

    private static final String SHEARS_DISPLAY_NAME =
            ChatColor.GOLD.toString() + ChatColor.BOLD + "チップ回収ハサミ";
    private static final String NBT_CAN_DESTROY = "CanDestroy";

    private final NamespacedKey shearsKey;

    /**
     * コンストラクタ。
     *
     * @param plugin プラグインインスタンス（NamespacedKey 生成用）
     */
    public ShearsHelper(ChipPlugin plugin) {
        this.shearsKey = new NamespacedKey(plugin, "chip_shears");
    }

    /**
     * チップ回収用ハサミを生成する。
     *
     * <p>CanDestroy NBT で全カーペット素材を指定し、アドベンチャーモードで破壊可能にする。
     *
     * @return チップ回収用ハサミ
     */
    public ItemStack createShears() {
        ItemStack shears = new ItemStack(Material.SHEARS);
        ItemMeta meta = shears.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(SHEARS_DISPLAY_NAME);
            meta.setLore(Arrays.asList(
                    ChatColor.GRAY + "チップ（カーペット）を",
                    ChatColor.GRAY + "回収するためのハサミです。"));
            meta.getPersistentDataContainer().set(shearsKey, PersistentDataType.BYTE, (byte) 1);
            meta.setUnbreakable(true);
            meta.addItemFlags(
                    ItemFlag.HIDE_DESTROYS,
                    ItemFlag.HIDE_UNBREAKABLE);
            shears.setItemMeta(meta);
        }
        NBT.modify(shears, nbt -> {
            ReadWriteNBTList<String> canDestroy = nbt.getStringList(NBT_CAN_DESTROY);
            for (Chip chip : Chip.values()) {
                canDestroy.add("minecraft:" + chip.getMaterial().getKey().getKey());
            }
        });
        return shears;
    }

    /**
     * プレイヤーにチップ回収用ハサミを配布する。
     *
     * <p>既にハサミを持っている場合は重複配布しない。
     *
     * @param player 配布対象プレイヤー
     */
    public void giveShears(Player player) {
        if (hasShears(player)) return;
        ItemStack shears = createShears();
        player.getInventory().addItem(shears);
    }

    /**
     * プレイヤーのインベントリからチップ回収用ハサミを全て回収する。
     *
     * @param player 対象プレイヤー
     */
    public void removeShears(Player player) {
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            if (isChipShears(contents[i])) {
                player.getInventory().setItem(i, null);
            }
        }
    }

    /**
     * プレイヤーがチップ回収用ハサミを所持しているかを返す。
     *
     * @param player 対象プレイヤー
     * @return 所持している場合 {@code true}
     */
    public boolean hasShears(Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (isChipShears(item)) return true;
        }
        return false;
    }

    /**
     * 指定アイテムがチップ回収用ハサミかどうかを判定する。
     *
     * @param item 判定対象のアイテム（{@code null} 許容）
     * @return チップ回収用ハサミの場合 {@code true}
     */
    public boolean isChipShears(ItemStack item) {
        if (item == null || item.getType() != Material.SHEARS) return false;
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(shearsKey, PersistentDataType.BYTE);
    }
}
