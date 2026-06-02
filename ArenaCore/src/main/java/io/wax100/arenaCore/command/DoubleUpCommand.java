package io.wax100.arenaCore.command;

import io.wax100.arenaCore.ArenaCore;
import io.wax100.arenaCore.manager.DoubleUpManager;
import io.wax100.arenaCore.util.ArenaMessages;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.List;

/**
 * ダブルアップコマンドハンドラ。
 *
 * <p>クリッカブルチャットから実行される内部コマンド。
 * <ul>
 *   <li>{@code /doubleup continue} — ダブルアップ（続行）</li>
 *   <li>{@code /doubleup stop} — キャッシュアウト（確定）</li>
 * </ul>
 */
public class DoubleUpCommand implements CommandExecutor, TabCompleter {

    private final ArenaCore plugin;

    public DoubleUpCommand(ArenaCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("プレイヤーのみ実行可能です。");
            return true;
        }

        DoubleUpManager doubleUp = plugin.getDoubleUpManager();
        if (doubleUp == null || !doubleUp.isEnabled()) {
            player.sendMessage(ArenaMessages.PREFIX + ChatColor.RED + "ダブルアップは無効です。");
            return true;
        }

        if (!doubleUp.isPendingChoice(player.getUniqueId())) {
            player.sendMessage(ArenaMessages.PREFIX + ChatColor.RED + "現在ダブルアップの選択待ちではありません。");
            return true;
        }

        if (args.length == 0) {
            player.sendMessage(ArenaMessages.PREFIX + ChatColor.RED + "/doubleup continue | stop");
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "continue", "double", "up" -> doubleUp.handleDoubleUp(player.getUniqueId());
            case "stop", "cash", "cashout" -> doubleUp.handleCashOut(player.getUniqueId());
            default -> player.sendMessage(ArenaMessages.PREFIX + ChatColor.RED + "/doubleup continue | stop");
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return List.of("continue", "stop");
        }
        return Collections.emptyList();
    }
}
