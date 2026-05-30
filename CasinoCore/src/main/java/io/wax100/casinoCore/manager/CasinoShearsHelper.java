package io.wax100.casinoCore.manager;

import de.tr7zw.changeme.nbtapi.NBT;
import de.tr7zw.changeme.nbtapi.iface.ReadWriteNBTList;
import io.wax100.bindingCurseLib.BindingCurseManager;
import io.wax100.casinoCore.CasinoCore;
import io.wax100.chipLib.Chip;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.Arrays;

/**
 * カジノシザース（カーペット破壊用ハサミ）の生成・配布・回収を担当するヘルパークラス。
 *
 * <p>
 * {@link CasinoManager} からシザース関連のロジックを分離し、
 * 単一責務の原則に従ったクラス設計を実現する。
 *
 * <p>
 * カジノシザースは以下の特性を持つ:
 * <ul>
 * <li>CanDestroy NBT により全チップカーペットを破壊可能</li>
 * <li>束縛の呪いにより配布先プレイヤーのみ使用可能</li>
 * <li>壊れないフラグ付き</li>
 * <li>{@link PersistentDataType#BYTE} のカスタムキーで識別</li>
 * </ul>
 *
 * @see CasinoManager
 * @see BindingCurseManager
 */
public class CasinoShearsHelper {

    /**
     * カジノシザースの表示名
     */
    private static final String SHEARS_DISPLAY_NAME =
            ChatColor.GOLD.toString() + ChatColor.BOLD + "カジノシザース";
    /**
     * CanDestroy NBT キー名
     */
    private static final String NBT_CAN_DESTROY = "CanDestroy";

    /**
     * カジノシザース識別用の {@link NamespacedKey}
     */
    private final NamespacedKey shearsKey;
    /**
     * 束縛の呪いアイテム所有者管理マネージャ
     */
    private final BindingCurseManager bindingCurseManager;

    /**
     * コンストラクタ。
     *
     * @param plugin              CasinoCore プラグインインスタンス（NamespacedKey 生成用）
     * @param bindingCurseManager 束縛の呪いアイテム管理マネージャ
     */
    public CasinoShearsHelper(CasinoCore plugin, BindingCurseManager bindingCurseManager) {
        this.shearsKey = new NamespacedKey(plugin, "casino_shears");
        this.bindingCurseManager = bindingCurseManager;
    }

    /**
     * カーペット破壊用のカジノシザースを配布する。
     *
     * <p>
     * CanDestroy NBT で全カーペット素材を指定し、アドベンチャーモードで破壊可能にする。
     * 束縛の呪いを付与し、{@link BindingCurseManager} で所有者を設定することで、
     * 配布先のプレイヤーのみが使用可能になる。
     *
     * @param player 配布対象プレイヤー
     */
    public void giveCasinoShears(Player player) {
        ItemStack shears = new ItemStack(Material.SHEARS);
        ItemMeta meta = shears.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(SHEARS_DISPLAY_NAME);
            meta.setLore(Arrays.asList(
                    ChatColor.GRAY + "カジノチップ（カーペット）を",
                    ChatColor.GRAY + "回収するためのハサミです。"));
            meta.getPersistentDataContainer().set(shearsKey, PersistentDataType.BYTE, (byte) 1);
            meta.setUnbreakable(true);
            // 束縛の呪いを付与（BindingCurseLib の所有者判定に必要）
            meta.addEnchant(Enchantment.BINDING_CURSE, 1, true);
            meta.addItemFlags(
                    ItemFlag.HIDE_DESTROYS,
                    ItemFlag.HIDE_UNBREAKABLE,
                    ItemFlag.HIDE_ENCHANTS);
            shears.setItemMeta(meta);
        }
        NBT.modify(shears, nbt -> {
            ReadWriteNBTList<String> canDestroy = nbt.getStringList(NBT_CAN_DESTROY);
            for (Chip chip : Chip.values()) {
                canDestroy.add("minecraft:" + chip.getMaterial().getKey().getKey());
            }
        });
        // BindingCurseLib で所有者を設定（このプレイヤーのみ使用可能）
        bindingCurseManager.setItemOwner(shears, player);
        player.getInventory().addItem(shears);
    }

    /**
     * カジノシザースをインベントリから回収する。
     *
     * <p>
     * カスタムキーを持つハサミを検索し、カジノシザースを削除する。
     *
     * @param player 対象プレイヤー
     */
    public void removeCasinoShears(Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (isCasinoShears(item)) {
                player.getInventory().remove(item);
            }
        }
    }

    /**
     * 指定アイテムがカジノシザースかどうかを判定する。
     *
     * @param item 判定対象のアイテム（{@code null} 許容）
     * @return カジノシザースの場合 {@code true}
     */
    public boolean isCasinoShears(ItemStack item) {
        if (item == null || item.getType() != Material.SHEARS || !item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(shearsKey, PersistentDataType.BYTE);
    }
}
