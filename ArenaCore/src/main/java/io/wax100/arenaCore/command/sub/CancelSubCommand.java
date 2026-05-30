package io.wax100.arenaCore.command.sub;

import io.wax100.arenaCore.ArenaCore;
import io.wax100.arenaCore.command.CommandHelper;
import io.wax100.arenaCore.command.SubCommand;
import io.wax100.arenaCore.manager.ArenaManager;
import io.wax100.arenaCore.util.ArenaMessages;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

/**
 * {@code /arena cancel} — セッションキャンセルを処理する。
 */
public class CancelSubCommand implements SubCommand {

    private final ArenaCore plugin;

    public CancelSubCommand(ArenaCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        ArenaManager manager = plugin.getArenaManager();
        if (CommandHelper.requireActiveSession(sender, manager) == null) return;

        if (manager.cancelArena()) {
            Bukkit.broadcastMessage(ArenaMessages.SEPARATOR);
            Bukkit.broadcastMessage(ArenaMessages.PREFIX + ChatColor.RED
                    + "闘技場がキャンセルされました。賭け金・参加費は返金されます。");
            Bukkit.broadcastMessage(ArenaMessages.SEPARATOR);
        }
    }

    @Override
    public String getUsage() {
        return "/arena cancel";
    }
}
