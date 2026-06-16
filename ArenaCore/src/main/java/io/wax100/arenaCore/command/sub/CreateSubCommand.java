package io.wax100.arenaCore.command.sub;

import io.wax100.arenaCore.ArenaCore;
import io.wax100.arenaCore.command.CommandHelper;
import io.wax100.arenaCore.command.SubCommand;
import io.wax100.arenaCore.manager.ArenaManager;
import io.wax100.arenaCore.model.ArenaSession;
import io.wax100.arenaCore.util.ArenaMessages;
import io.wax100.chipLib.ChipManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

import java.util.List;

/**
 * {@code /arena create <名前>} を処理する。
 */
public class CreateSubCommand implements SubCommand {

    private final ArenaCore plugin;

    public CreateSubCommand(ArenaCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(CommandSender sender, String [] args) {
        if (!CommandHelper.requireArgs(sender, args, 1, getUsage())) return;

        ArenaManager manager = plugin.getArenaManager();
        if (manager.hasActiveSession()) {
            sender.sendMessage(ArenaMessages.PREFIX + ChatColor.RED
                    + ArenaMessages.MSG_SESSION_ALREADY_ACTIVE);
            return;
        }

        String name = args[0];

        ArenaSession session = manager.createArena(name, List.of());
        if (session == null) {
            if (plugin.getTerrainManager() != null
                    && plugin.getTerrainManager().isBlocking()) {
                sender.sendMessage(ArenaMessages.MSG_TERRAIN_BLOCKING);
            } else {
                sender.sendMessage(ArenaMessages.PREFIX + ChatColor.RED
                        + ArenaMessages.MSG_SESSION_CREATE_FAILED);
            }
            return;
        }

        Bukkit.broadcastMessage(ArenaMessages.SEPARATOR);
        Bukkit.broadcastMessage(ArenaMessages.PREFIX + ChatColor.GOLD + ChatColor.BOLD
                + "闘技場「" + name + "」が開設されました！");
        Bukkit.broadcastMessage("");

        long entryFee = session.getArenaConfig().getEntryFee();
        if (entryFee > 0) {
            Bukkit.broadcastMessage(ArenaMessages.PREFIX + ChatColor.GRAY
                    + "参加費: " + ChatColor.YELLOW + ChipManager.formatAmount(entryFee) + " E");
            Bukkit.broadcastMessage("");
        }

        Bukkit.broadcastMessage(ArenaMessages.PREFIX + ChatColor.GRAY + "次のステップ:");
        Bukkit.broadcastMessage(ChatColor.GRAY + "  1. " + ChatColor.YELLOW + "/arena team add <チーム名>");
        Bukkit.broadcastMessage(ChatColor.GRAY + "  2. " + ChatColor.YELLOW + "/arena team area <チーム> [待機場名]");
        Bukkit.broadcastMessage(ChatColor.GRAY + "  3. " + ChatColor.YELLOW + "/arena field set [名前]");
        Bukkit.broadcastMessage(ChatColor.GRAY + "  4. " + ChatColor.YELLOW + "/arena config" + ChatColor.GRAY + " <- 設定変更");
        Bukkit.broadcastMessage(ArenaMessages.SEPARATOR);
    }

    @Override
    public String getUsage() {
        return "/arena create <名前>";
    }
}
