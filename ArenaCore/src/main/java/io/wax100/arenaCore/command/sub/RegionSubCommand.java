package io.wax100.arenaCore.command.sub;

import io.wax100.arenaCore.ArenaCore;
import io.wax100.arenaCore.command.CommandHelper;
import io.wax100.arenaCore.command.SubCommand;
import io.wax100.arenaCore.manager.ArenaManager;
import io.wax100.arenaCore.manager.RegionManager;
import io.wax100.arenaCore.model.ArenaSession;
import io.wax100.arenaCore.model.ArenaState;
import io.wax100.arenaCore.util.ArenaMessages;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * {@code /arena region <bet|team> <チーム名>} を処理する。
 */
public class RegionSubCommand implements SubCommand {

    private static final List<String> SUB_COMMANDS = Arrays.asList("bet", "team");

    private final ArenaCore plugin;

    public RegionSubCommand(ArenaCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        // args: [sub, teamName]
        Player player = CommandHelper.requirePlayer(sender);
        if (player == null) return;
        if (!CommandHelper.requireArgs(sender, args, 2, getUsage())) return;

        ArenaManager manager = plugin.getArenaManager();
        ArenaSession session = CommandHelper.requireActiveSession(sender, manager);
        if (session == null) return;

        RegionManager regionManager = plugin.getRegionManager();
        if (!regionManager.isWorldEditAvailable()) {
            sender.sendMessage(ArenaMessages.PREFIX + ChatColor.RED + ArenaMessages.MSG_WE_REQUIRED);
            return;
        }

        String teamName = args[1];
        if (!CommandHelper.requireTeamExists(sender, session, teamName)) return;

        int teamIndex = session.getTeamNames().indexOf(teamName);
        ChatColor teamColor = ArenaMessages.getTeamColor(teamIndex);

        switch (args[0].toLowerCase()) {
            case "bet" -> handleBet(sender, player, session, regionManager, teamName, teamColor);
            case "team" -> handleTeam(sender, player, session, manager, regionManager, teamName, teamColor);
            default -> sender.sendMessage(ArenaMessages.PREFIX + ChatColor.RED + "使い方: " + getUsage());
        }
    }

    private void handleBet(CommandSender sender, Player player, ArenaSession session,
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

    private void handleTeam(CommandSender sender, Player player, ArenaSession session,
                            ArenaManager manager, RegionManager regionManager,
                            String teamName, ChatColor teamColor) {
        if (session.getState() != ArenaState.SETUP) {
            sender.sendMessage(ArenaMessages.PREFIX + ChatColor.RED + ArenaMessages.MSG_SETUP_ONLY_USE);
            return;
        }
        List<UUID> playersInArea = regionManager.getPlayersInSelection(player);
        if (playersInArea.isEmpty()) {
            sender.sendMessage(ArenaMessages.PREFIX + ChatColor.RED + ArenaMessages.MSG_NO_PLAYERS_IN_SELECTION);
            return;
        }
        int added = 0;
        for (UUID playerId : playersInArea) {
            Player target = Bukkit.getPlayer(playerId);
            if (target != null && !session.isFighter(playerId)) {
                if (manager.addTeamMember(teamName, target)) added++;
            }
        }
        sender.sendMessage(ArenaMessages.PREFIX + ChatColor.GREEN
                + added + " 人を " + teamColor + ChatColor.BOLD + teamName
                + ChatColor.RESET + ChatColor.GREEN + " に配属しました。");
    }

    // ── Tab 補完 ──

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        // args[0]=sub, args[1]=teamName
        if (args.length == 1) {
            return CommandHelper.filterStartsWith(SUB_COMMANDS, args[0]);
        }
        if (args.length == 2) {
            return CommandHelper.getTeamNameCandidates(plugin.getArenaManager(), args[1]);
        }
        return List.of();
    }

    @Override
    public String getUsage() {
        return "/arena region <bet|team> <チーム名>";
    }
}
