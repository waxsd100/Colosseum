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
import org.bukkit.entity.Player;

import java.util.List;

/**
 * {@code /arena area <set|info>} を処理する。
 *
 * <p>WE選択範囲から待機場を作成してセッションに設定する。
 * 永続保存は {@code /arena preset save} で一括して行う。
 */
public class AreaSubCommand implements SubCommand {

    private static final List<String> SUB_COMMANDS = List.of("set", "info");

    private final ArenaCore plugin;

    public AreaSubCommand(ArenaCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(CommandSender sender, String [] args) {
        if (!CommandHelper.requireArgs(sender, args, 1, getUsage())) return;

        switch (args[0].toLowerCase()) {
            case "set"  -> handleSet(sender);
            case "info" -> handleInfo(sender);
            default -> sender.sendMessage(ArenaMessages.PREFIX + ChatColor.RED + "使い方: " + getUsage());
        }
    }

    // ── set ──

    /**
     * {@code /arena area set} — WE選択範囲から待機場を作成し、直近の team area に設定。
     * ※ 実際の待機場セットは {@code /arena team area <チーム> } で行うため、
     *   このコマンドは情報確認用途に近い。
     */
    private void handleSet(CommandSender sender) {
        Player player = CommandHelper.requirePlayer(sender);
        if (player == null) return;

        sender.sendMessage(ArenaMessages.PREFIX + ChatColor.GRAY
                + "待機場の設定は " + ChatColor.YELLOW + "/arena team area <チーム名>"
                + ChatColor.GRAY + " で行ってください。");
    }

    // ── info ──

    private void handleInfo(CommandSender sender) {
        ArenaManager manager = plugin.getArenaManager();
        ArenaSession session = CommandHelper.requireActiveSession(sender, manager);
        if (session == null) return;

        boolean found = false;
        for (String team : session.getTeamNames()) {
            TeamAreaConfig config = session.getTeamAreaConfig(team);
            if (config == null) continue;
            found = true;

            ChatColor teamColor = session.getTeamColor(team);
            sender.sendMessage(ArenaMessages.PREFIX + teamColor + team + ChatColor.GRAY
                    + " ワールド: " + ChatColor.WHITE + config.worldName()
                    + ChatColor.GRAY + " 範囲: " + ChatColor.WHITE
                    + "(" + config.minX() + ", " + config.minY() + ", " + config.minZ() + ")"
                    + ChatColor.GRAY + " → " + ChatColor.WHITE
                    + "(" + config.maxX() + ", " + config.maxY() + ", " + config.maxZ() + ")");

            Location dest = config.getDestination();
            if (dest != null && dest.getWorld() != null) {
                sender.sendMessage("  " + ChatColor.GRAY + "TP先: " + ChatColor.WHITE
                        + String.format("%.1f, %.1f, %.1f", dest.getX(), dest.getY(), dest.getZ()));
            } else {
                sender.sendMessage("  " + ChatColor.GRAY + "TP先: " + ChatColor.RED + "未設定");
            }
        }
        if (!found) {
            sender.sendMessage(ArenaMessages.PREFIX + ChatColor.GRAY + "待機場は設定されていません。");
        }
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String [] args) {
        if (args.length == 1) {
            return CommandHelper.filterStartsWith(SUB_COMMANDS, args[0]);
        }
        return List.of();
    }

    @Override
    public String getUsage() {
        return "/arena area <info>";
    }
}
