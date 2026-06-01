package io.wax100.arenaCore.command.sub;

import io.wax100.arenaCore.ArenaCore;
import io.wax100.arenaCore.command.CommandHelper;
import io.wax100.arenaCore.command.SubCommand;
import io.wax100.arenaCore.manager.ArenaManager;
import io.wax100.arenaCore.model.ArenaSession;
import io.wax100.arenaCore.model.ArenaState;
import io.wax100.arenaCore.util.ArenaMessages;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

/**
 * {@code /arena close} — 賭け受付を締め切る（試合は開始しない）。
 *
 * <p>{@code /arena open} と対になるコマンド。BETTING 状態でのみ実行可能。
 * 賭けを締め切った後、{@code /arena start} で試合を開始する。
 */
public class CloseSubCommand implements SubCommand {

    private final ArenaCore plugin;

    public CloseSubCommand(ArenaCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        ArenaManager manager = plugin.getArenaManager();
        if (CommandHelper.requireActiveSession(sender, manager) == null) return;

        ArenaSession session = manager.getActiveSession();
        if (session.getState() != ArenaState.BETTING) {
            sender.sendMessage(ArenaMessages.PREFIX + ChatColor.RED
                    + "賭け受付中でなければ締め切れません。");
            return;
        }

        if (!manager.closeBetting()) {
            sender.sendMessage(ArenaMessages.PREFIX + ChatColor.RED
                    + "賭け締切に失敗しました。");
            return;
        }

        Bukkit.broadcastMessage(ArenaMessages.SEPARATOR);
        Bukkit.broadcastMessage(ArenaMessages.PREFIX + ChatColor.RED + ChatColor.BOLD
                + "賭け締め切り！");
        Bukkit.broadcastMessage(ArenaMessages.PREFIX + ChatColor.GRAY
                + "これ以上賭けることはできません。");
        Bukkit.broadcastMessage("");
        plugin.getBettingManager().broadcastOdds(session);
        Bukkit.broadcastMessage(ArenaMessages.PREFIX + ChatColor.GRAY
                + "試合開始を待っています… " + ChatColor.YELLOW + "/arena start");
        Bukkit.broadcastMessage(ArenaMessages.SEPARATOR);
    }

    @Override
    public String getUsage() {
        return "/arena close";
    }
}
