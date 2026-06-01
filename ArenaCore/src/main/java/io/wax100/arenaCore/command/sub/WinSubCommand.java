package io.wax100.arenaCore.command.sub;

import io.wax100.arenaCore.ArenaCore;
import io.wax100.arenaCore.command.CommandHelper;
import io.wax100.arenaCore.command.SubCommand;
import io.wax100.arenaCore.manager.ArenaManager;
import io.wax100.arenaCore.model.ArenaSession;
import io.wax100.arenaCore.model.ArenaState;
import io.wax100.arenaCore.util.ArenaMessages;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

import java.util.List;

/**
 * {@code /arena win <チーム名>} — 勝者宣言を処理する。
 */
public class WinSubCommand implements SubCommand {

    private final ArenaCore plugin;

    public WinSubCommand(ArenaCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(CommandSender sender, String [] args) {
        if (!CommandHelper.requireArgs(sender, args, 1, getUsage())) return;

        ArenaManager manager = plugin.getArenaManager();
        ArenaSession session = CommandHelper.requireActiveSession(sender, manager);
        if (session == null) return;

        if (!manager.declareWinner(args[0])) {
            if (session.getState() != ArenaState.ACTIVE) {
                sender.sendMessage(ArenaMessages.PREFIX + ChatColor.RED
                        + ArenaMessages.MSG_WIN_ACTIVE_ONLY);
            } else {
                sender.sendMessage(ArenaMessages.PREFIX + ChatColor.RED
                        + "チーム「" + args[0] + "」が見つかりません。");
            }
        }
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String [] args) {
        if (args.length == 1) {
            return CommandHelper.getTeamNameCandidates(plugin.getArenaManager(), args[0]);
        }
        return List.of();
    }

    @Override
    public String getUsage() {
        return "/arena win <チーム名>";
    }
}
