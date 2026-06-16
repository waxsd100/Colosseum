package io.wax100.arenaCore.listener;

import io.wax100.arenaCore.ArenaCore;
import io.wax100.arenaCore.manager.ArenaManager;
import io.wax100.arenaCore.util.ArenaMessages;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

/**
 * 参加費不足時の借金/辞退選択リスナー。
 *
 * <p>試合開始時に所持金が不足しているプレイヤーに表示される
 * クリック可能メッセージの応答を処理する。
 */
public class EntryFeeListener implements Listener {

    private final ArenaCore plugin;

    public EntryFeeListener(ArenaCore plugin) {
        this.plugin = plugin;
    }

    /**
     * 参加費選択の内部コマンドを傍受する。
     *
     * <p>{@code /arena _fee borrow} または {@code /arena _fee withdraw} を
     * 検出し、ArenaManager に応答を委譲する。
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        String message = event.getMessage().toLowerCase();
        if (!message.startsWith("/arena _fee ")) return;

        event.setCancelled(true);
        Player player = event.getPlayer();
        ArenaManager manager = plugin.getArenaManager();

        if (!manager.getPendingFeeResponses().containsKey(player.getUniqueId())) {
            player.sendMessage(ArenaMessages.PREFIX + ChatColor.RED + "現在、回答待ちではありません。");
            return;
        }

        if (message.endsWith("borrow")) {
            manager.handleFeeResponse(player.getUniqueId(), true);
        } else if (message.endsWith("withdraw")) {
            manager.handleFeeResponse(player.getUniqueId(), false);
        } else {
            player.sendMessage(ArenaMessages.PREFIX + ChatColor.RED + "無効なコマンドです。");
        }
    }
}
