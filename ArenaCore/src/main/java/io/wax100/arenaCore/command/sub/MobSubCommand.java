package io.wax100.arenaCore.command.sub;

import io.wax100.arenaCore.ArenaCore;
import io.wax100.arenaCore.command.CommandHelper;
import io.wax100.arenaCore.command.SubCommand;
import io.wax100.arenaCore.manager.ArenaManager;
import io.wax100.arenaCore.model.ArenaSession;
import io.wax100.arenaCore.model.ArenaState;
import io.wax100.arenaCore.model.TeamAreaConfig;
import io.wax100.arenaCore.util.ArenaMessages;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.List;

/**
 * {@code /arena mob <チーム名> <area|dest|list>} を処理する。
 */
public class MobSubCommand implements SubCommand {

    private static final List<String> SUB_COMMANDS = Arrays.asList("area", "dest", "list");

    private final ArenaCore plugin;

    public MobSubCommand(ArenaCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        // args: [teamName, sub, ...]
        if (!CommandHelper.requireArgs(sender, args, 2, getUsage())) return;

        String teamName = args[0];
        switch (args[1].toLowerCase()) {
            case "area" -> handleArea(sender, teamName);
            case "dest" -> handleDest(sender, teamName);
            case "list" -> handleList(sender, teamName);
            default -> sender.sendMessage(ArenaMessages.PREFIX + ChatColor.RED + "使い方: " + getUsage());
        }
    }

    // ── area (Mob待機場設定) ──

    private void handleArea(CommandSender sender, String teamName) {
        Player player = CommandHelper.requirePlayer(sender);
        if (player == null) return;

        ArenaManager manager = plugin.getArenaManager();
        ArenaSession session = CommandHelper.requireSessionInState(
                sender, manager, ArenaState.SETUP, ArenaMessages.MSG_SETUP_ONLY);
        if (session == null) return;
        if (!CommandHelper.requireTeamExists(sender, session, teamName)) return;

        if (!plugin.getRegionManager().isWorldEditAvailable()) {
            sender.sendMessage(ArenaMessages.PREFIX + ChatColor.RED + ArenaMessages.MSG_WE_REQUIRED);
            return;
        }

        TeamAreaConfig newConfig = CommandHelper.createAreaConfigFromSelection(player);
        if (newConfig == null) {
            sender.sendMessage(ArenaMessages.PREFIX + ChatColor.RED + ArenaMessages.MSG_WE_SELECT_FIRST);
            return;
        }

        // 既存の destination を引き継ぎ
        TeamAreaConfig existing = session.getTeamAreaConfig(teamName);
        if (existing != null) {
            newConfig.setDestination(existing.getDestination());
        }
        session.setTeamAreaConfig(teamName, newConfig);
        session.markAsMobTeam(teamName);

        ChatColor teamColor = session.getTeamColor(teamName);

        int count = newConfig.scanEntities().size();
        sender.sendMessage(ArenaMessages.PREFIX + teamColor + ChatColor.BOLD + teamName
                + ChatColor.RESET + ChatColor.GREEN + " の待機場を設定しました。"
                + ChatColor.GRAY + " (エリア内: " + ChatColor.WHITE + count + "体"
                + ChatColor.GRAY + ")");
    }

    // ── dest (MobTP先設定) ──

    private void handleDest(CommandSender sender, String teamName) {
        Player player = CommandHelper.requirePlayer(sender);
        if (player == null) return;

        ArenaManager manager = plugin.getArenaManager();
        ArenaSession session = CommandHelper.requireSessionInState(
                sender, manager, ArenaState.SETUP, ArenaMessages.MSG_SETUP_ONLY);
        if (session == null) return;
        if (!CommandHelper.requireTeamExists(sender, session, teamName)) return;

        TeamAreaConfig config = session.getTeamAreaConfig(teamName);
        if (config == null) {
            sender.sendMessage(ArenaMessages.PREFIX + ChatColor.RED
                    + String.format(ArenaMessages.MSG_AREA_NOT_SET_FMT, "/arena mob " + teamName + " area"));
            return;
        }

        config.setDestination(player.getLocation());

        ChatColor teamColor = session.getTeamColor(teamName);
        sender.sendMessage(ArenaMessages.PREFIX + teamColor + ChatColor.BOLD + teamName
                + ChatColor.RESET + ChatColor.GREEN + " のTP先を現在地に設定しました。");
    }

    // ── list (Mob一覧) ──

    private void handleList(CommandSender sender, String teamName) {
        ArenaManager manager = plugin.getArenaManager();
        ArenaSession session = CommandHelper.requireActiveSession(sender, manager);
        if (session == null) return;
        if (!CommandHelper.requireTeamExists(sender, session, teamName)) return;

        ChatColor teamColor = session.getTeamColor(teamName);

        TeamAreaConfig config = session.getTeamAreaConfig(teamName);
        if (config == null) {
            sender.sendMessage(ArenaMessages.PREFIX + teamColor + teamName
                    + ChatColor.GRAY + " に待機場は未設定です。");
            return;
        }

        List<LivingEntity> mobs = config.scanEntities();
        sender.sendMessage(ArenaMessages.PREFIX + teamColor + ChatColor.BOLD + teamName
                + ChatColor.RESET + ChatColor.GRAY + " の待機場Mob (" + mobs.size() + "体):");
        for (LivingEntity mob : mobs) {
            sender.sendMessage(ChatColor.GRAY + "  - " + ChatColor.WHITE + mob.getName()
                    + ChatColor.GRAY + " (" + mob.getType().name() + ")");
        }
        Location dest = config.getDestination();
        if (dest != null) {
            sender.sendMessage(ChatColor.GRAY + "  TP先: " + ChatColor.WHITE
                    + String.format("%.0f, %.0f, %.0f", dest.getX(), dest.getY(), dest.getZ()));
        } else {
            sender.sendMessage(ChatColor.RED + "  TP先: 未設定");
        }
    }

    // ── Tab 補完 ──

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        // args[0]=teamName, args[1]=sub
        if (args.length == 1) {
            return CommandHelper.getTeamNameCandidates(plugin.getArenaManager(), args[0]);
        }
        if (args.length == 2) {
            return CommandHelper.filterStartsWith(SUB_COMMANDS, args[1]);
        }
        return List.of();
    }

    @Override
    public String getUsage() {
        return "/arena mob <チーム名> <area|dest|list>";
    }

}
