package io.wax100.arenaCore.command.sub;

import io.wax100.arenaCore.ArenaCore;
import io.wax100.arenaCore.command.CommandHelper;
import io.wax100.arenaCore.command.SubCommand;
import io.wax100.arenaCore.manager.ArenaManager;
import io.wax100.arenaCore.util.ArenaMessages;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

import java.util.List;

/**
 * {@code /arena loop <true|false>} コマンドを処理する。
 * 試合終了時に自動的に同じプリセットをロードし、次の試合を開始するオートループ機能の有効化・無効化を行う。
 */
public class LoopSubCommand implements SubCommand {

    private final ArenaCore plugin;

    public LoopSubCommand(ArenaCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (!CommandHelper.requireArgs(sender, args, 1, "/arena loop <true|false>")) return;

        ArenaManager manager = plugin.getArenaManager();
        String arg = args[0].toLowerCase();
        
        if (arg.equals("true") || arg.equals("on")) {
            manager.setAutoLoopEnabled(true);
            sender.sendMessage(ArenaMessages.PREFIX + ChatColor.AQUA + "オートループを " + ChatColor.GREEN + ChatColor.BOLD + "有効" + ChatColor.AQUA + " にしました。");
            if (manager.getLastPresetName() == null) {
                sender.sendMessage(ArenaMessages.PREFIX + ChatColor.YELLOW + "※注意: まだプリセットがロードされていません。オートループを作動させるには一度 /arena preset load を実行してください。");
            }
        } else if (arg.equals("false") || arg.equals("off")) {
            manager.setAutoLoopEnabled(false);
            sender.sendMessage(ArenaMessages.PREFIX + ChatColor.AQUA + "オートループを " + ChatColor.RED + ChatColor.BOLD + "無効" + ChatColor.AQUA + " にしました。");
        } else {
            sender.sendMessage(ArenaMessages.PREFIX + ChatColor.RED + "引数は true または false を指定してください。");
        }
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            return CommandHelper.filterStartsWith(List.of("true", "false"), args[0]);
        }
        return List.of();
    }

    @Override
    public String getUsage() {
        return "/arena loop <true|false>";
    }
}
