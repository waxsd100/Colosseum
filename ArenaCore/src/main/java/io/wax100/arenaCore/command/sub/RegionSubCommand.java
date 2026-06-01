package io.wax100.arenaCore.command.sub;

import io.wax100.arenaCore.ArenaCore;
import io.wax100.arenaCore.command.CommandHelper;
import io.wax100.arenaCore.command.SubCommand;
import io.wax100.arenaCore.manager.ArenaManager;
import io.wax100.arenaCore.manager.RegionManager;
import io.wax100.arenaCore.model.ArenaSession;
import io.wax100.arenaCore.model.ArenaState;
import io.wax100.arenaCore.util.ArenaMessages;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * {@code /arena region <チーム名>} を処理する。賭けエリアをWE選択範囲で設定する。
 */
public class RegionSubCommand implements SubCommand {

    private final ArenaCore plugin;

    public RegionSubCommand(ArenaCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(CommandSender sender, String [] args) {
        Player player = CommandHelper.requirePlayer(sender);
        if (player == null) return;
        if (!CommandHelper.requireArgs(sender, args, 1, getUsage())) return;

        ArenaManager manager = plugin.getArenaManager();
        ArenaSession session = CommandHelper.requireActiveSession(sender, manager);
        if (session == null) return;

        RegionManager regionManager = plugin.getRegionManager();
        if (!regionManager.isWorldEditAvailable()) {
            sender.sendMessage(ArenaMessages.PREFIX + ChatColor.RED + ArenaMessages.MSG_WE_REQUIRED);
            return;
        }

        String teamName = args[0];
        if (CommandHelper.abortIfTeamNotFound(sender, session, teamName)) return;

        ChatColor teamColor = session.getTeamColor(teamName);

        handleSetRegion(sender, player, session, regionManager, teamName, teamColor);
    }

    private void handleSetRegion(CommandSender sender, Player player, ArenaSession session,
                                 RegionManager regionManager, String teamName, ChatColor teamColor) {
        if (session.getState() != ArenaState.SETUP && session.getState() != ArenaState.BETTING) {
            sender.sendMessage(ArenaMessages.PREFIX + ChatColor.RED
                    + ArenaMessages.MSG_SETUP_OR_BETTING_ONLY);
            return;
        }
        if (regionManager.setBettingRegion(player, teamName)) {
            sender.sendMessage(ArenaMessages.PREFIX + ChatColor.GREEN
                    + teamColor + ChatColor.BOLD + teamName
                    + ChatColor.RESET + ChatColor.GREEN + " の賭けエリアを設定しました。");
        } else {
            sender.sendMessage(ArenaMessages.PREFIX + ChatColor.RED + ArenaMessages.MSG_WE_SELECT_FIRST);
        }
    }


    // ── Tab 補完 ──

    @Override
    public List<String> tabComplete(CommandSender sender, String [] args) {
        if (args.length == 1) {
            return CommandHelper.getTeamNameCandidates(plugin.getArenaManager(), args[0]);
        }
        return List.of();
    }

    @Override
    public String getUsage() {
        return "/arena region <チーム名>";
    }
}
