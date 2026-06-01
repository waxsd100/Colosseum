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
 * {@code /arena end} — 試合を強制終了し引き分けとして処理する。
 *
 * <p>ACTIVE 状態でのみ実行可能。全賭け金を返金し、参加費も返金する。
 */
public class EndSubCommand implements SubCommand {

    private final ArenaCore plugin;

    public EndSubCommand(ArenaCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        ArenaManager manager = plugin.getArenaManager();
        if (CommandHelper.requireActiveSession(sender, manager) == null) return;

        ArenaSession session = manager.getActiveSession();
        if (session.getState() != ArenaState.ACTIVE) {
            sender.sendMessage(ArenaMessages.PREFIX + ChatColor.RED
                    + "試合中でなければ強制終了できません。");
            return;
        }

        if (!manager.drawMatch()) {
            sender.sendMessage(ArenaMessages.PREFIX + ChatColor.RED
                    + "強制終了に失敗しました。");
            return;
        }

        Bukkit.broadcastMessage(ArenaMessages.SEPARATOR);
        Bukkit.broadcastMessage(ArenaMessages.PREFIX + ChatColor.YELLOW + ChatColor.BOLD
                + "⚖ 引き分け！");
        Bukkit.broadcastMessage(ArenaMessages.PREFIX + ChatColor.GRAY
                + "試合が強制終了されました。全賭け金を返金します。");
        Bukkit.broadcastMessage(ArenaMessages.SEPARATOR);
    }

    @Override
    public String getUsage() {
        return "/arena end";
    }
}
