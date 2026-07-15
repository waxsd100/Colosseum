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
 * {@code /arena close} — ベット受付を締め切る（試合は開始しない）。
 *
 * <p>{@code /arena open} と対になるコマンド。BETTING 状態でのみ実行可能。
 * ベットを締め切った後、{@code /arena start} で試合を開始する。
 */
public class CloseSubCommand implements SubCommand {

    private final ArenaCore plugin;

    public CloseSubCommand(ArenaCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        ArenaManager manager = plugin.getArenaManager();
        ArenaSession session = CommandHelper.requireActiveSession(sender, manager);
        if (session == null) return;

        if (session.getState() != ArenaState.BETTING && session.getState() != ArenaState.BLIND) {
            sender.sendMessage(ArenaMessages.PREFIX + ChatColor.RED
                    + "ベット受付中でなければ締め切れません。");
            return;
        }

        if (!manager.closeBetting()) {
            sender.sendMessage(ArenaMessages.PREFIX + ChatColor.RED
                    + "ベット締切に失敗しました。");
            return;
        }

        Bukkit.broadcastMessage(ArenaMessages.SEPARATOR);
        Bukkit.broadcastMessage(ArenaMessages.PREFIX + ChatColor.RED + ChatColor.BOLD
                + "ベット締め切り！");
        Bukkit.broadcastMessage(ArenaMessages.PREFIX + ChatColor.GRAY
                + "これ以上ベットすることはできません。");
        Bukkit.broadcastMessage("");
        plugin.getBettingManager().broadcastOdds(session);
        Bukkit.broadcastMessage(ArenaMessages.PREFIX + ChatColor.GRAY
                + "試合開始を待っています…");
        Bukkit.broadcastMessage(ArenaMessages.SEPARATOR);
        // コマンド案内は実行者のみに表示
        sender.sendMessage(ArenaMessages.PREFIX + ChatColor.GRAY + "→ "
                + ChatColor.YELLOW + "/arena start" + ChatColor.GRAY + " で試合を開始");
    }

    @Override
    public String getUsage() {
        return "/arena close";
    }
}
