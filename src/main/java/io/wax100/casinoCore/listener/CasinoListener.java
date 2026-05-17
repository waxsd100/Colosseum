package io.wax100.casinoCore.listener;

import io.wax100.casinoCore.CasinoCore;
import io.wax100.casinoCore.manager.ChipManager;
import io.wax100.casinoCore.manager.ChipManager.Chip;
import io.wax100.casinoCore.util.Messages;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;

/**
 * カジノ関連のイベントリスナー
 *
 * <p>カジノモード中の処理:
 * <ul>
 *   <li>ログイン通知 + アドベンチャーモード適用</li>
 *   <li>カーペット破壊時にメタデータ付きチップをドロップ</li>
 * </ul>
 *
 * <p>チップの設置・破壊はバニラの CanPlaceOn / CanDestroy で動作する。
 */
public class CasinoListener implements Listener {

    private final CasinoCore plugin;

    public CasinoListener(CasinoCore plugin) {
        this.plugin = plugin;
    }

    /**
     * プレイヤーログイン時にカジノモードの状態を通知する
     */
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!plugin.getCasinoManager().isCasinoActive()) return;

        Player player = event.getPlayer();
        player.sendMessage("");
        player.sendMessage(Messages.PREFIX + ChatColor.GREEN + "現在カジノモードが "
                + ChatColor.YELLOW + ChatColor.BOLD + "ON " + ChatColor.RESET + ChatColor.GREEN + "です！");
        player.sendMessage(Messages.PREFIX + ChatColor.GRAY + "/chip <額面> <枚数> でチップを購入できます。");
        player.sendMessage(Messages.PREFIX + ChatColor.GRAY + "/chip info でチップ一覧を確認できます。");
        player.sendMessage("");

        plugin.getCasinoManager().applyAdventureModeToPlayer(player);
    }

    /**
     * チップカーペットを破壊した場合、メタデータ付きチップをドロップする。
     * CanDestroy 設定によりアドベンチャーモードでも BlockBreakEvent が発火する。
     */
    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (!plugin.getCasinoManager().isCasinoActive()) return;

        Block block = event.getBlock();
        Material material = block.getType();

        if (!ChipManager.isChipMaterial(material)) return;

        Chip chip = findChipByMaterial(material);
        if (chip == null) return;

        event.setDropItems(false);
        ItemStack chipItem = plugin.getChipManager().createChipItem(chip, 1);
        block.getWorld().dropItemNaturally(block.getLocation(), chipItem);
    }

    /**
     * マテリアルから対応するChipを検索する
     */
    private Chip findChipByMaterial(Material material) {
        for (Chip chip : Chip.values()) {
            if (chip.getMaterial() == material) {
                return chip;
            }
        }
        return null;
    }
}
