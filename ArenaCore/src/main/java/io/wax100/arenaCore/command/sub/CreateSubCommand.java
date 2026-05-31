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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * {@code /arena create <名前> [チーム1] [チーム2] [チーム3...]} を処理する。
 */
public class CreateSubCommand implements SubCommand {

    private final ArenaCore plugin;

    public CreateSubCommand(ArenaCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (!CommandHelper.requireArgs(sender, args, 1, getUsage())) return;

        ArenaManager manager = plugin.getArenaManager();
        if (manager.hasActiveSession()) {
            sender.sendMessage(ArenaMessages.PREFIX + ChatColor.RED
                    + ArenaMessages.MSG_SESSION_ALREADY_ACTIVE);
            return;
        }

        String name = args[0];
        List<String> teamNames = new ArrayList<>(Arrays.asList(args).subList(1, args.length));

        // 重複チーム名チェック
        Set<String> uniqueTeams = new HashSet<>(teamNames);
        if (uniqueTeams.size() != teamNames.size()) {
            sender.sendMessage(ArenaMessages.PREFIX + ChatColor.RED
                    + "チーム名が重複しています。異なるチーム名を指定してください。");
            return;
        }

        ArenaSession session = manager.createArena(name, teamNames);
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
        if (teamNames.isEmpty()) {
            Bukkit.broadcastMessage(ArenaMessages.PREFIX + ChatColor.GRAY
                    + "チーム未設定 — " + ChatColor.YELLOW + "/arena team add <チーム名>"
                    + ChatColor.GRAY + " で追加してください。");
        } else {
            for (int i = 0; i < teamNames.size(); i++) {
                ChatColor color = session.getTeamColor(teamNames.get(i));
                Bukkit.broadcastMessage(ArenaMessages.PREFIX + "  " + color + "■ " + teamNames.get(i));
            }
        }

        long entryFee = plugin.getConfig().getLong("entry-fee", 0);
        if (entryFee > 0) {
            Bukkit.broadcastMessage("");
            Bukkit.broadcastMessage(ArenaMessages.PREFIX + ChatColor.GRAY
                    + "参加費: " + ChatColor.YELLOW + ChipManager.formatAmount(entryFee) + " E");
        }
        Bukkit.broadcastMessage("");
        Bukkit.broadcastMessage(ArenaMessages.PREFIX + ChatColor.GRAY + "次のステップ:");
        if (teamNames.isEmpty()) {
            Bukkit.broadcastMessage(ChatColor.GRAY + "  1. " + ChatColor.YELLOW + "/arena team add <チーム名>");
        }
        Bukkit.broadcastMessage(ChatColor.GRAY + "  " + (teamNames.isEmpty() ? "2" : "1") + ". "
                + ChatColor.YELLOW + "/arena team area <チーム> [待機場名]");
        Bukkit.broadcastMessage(ChatColor.GRAY + "  " + (teamNames.isEmpty() ? "3" : "2") + ". "
                + ChatColor.YELLOW + "/arena field set" + ChatColor.GRAY + " or "
                + ChatColor.YELLOW + "/arena field load <名前>");
        Bukkit.broadcastMessage(ArenaMessages.SEPARATOR);
    }

    @Override
    public String getUsage() {
        return "/arena create <名前> [チーム1] [チーム2] [チーム3...]";
    }
}
