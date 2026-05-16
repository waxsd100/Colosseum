package io.wax100.casinoCore.listener;

import io.wax100.casinoCore.CasinoCore;
import io.wax100.casinoCore.util.Messages;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * カジノ関連のイベントリスナー
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

        // カジノ中に参加したプレイヤーもアドベンチャーモードに
        plugin.getCasinoManager().applyAdventureModeToPlayer(player);
    }
}
