package io.wax100.arenaCore.command.sub;

import io.wax100.arenaCore.ArenaCore;
import io.wax100.arenaCore.command.CommandHelper;
import io.wax100.arenaCore.command.SubCommand;
import io.wax100.arenaCore.manager.ArenaManager;
import io.wax100.arenaCore.model.ArenaSession;
import io.wax100.arenaCore.model.ArenaState;
import io.wax100.arenaCore.util.ArenaMessages;
import io.wax100.chipLib.ChipManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

import java.util.List;

/**
 * {@code /arena open} — 賭け受付開始を処理する。
 */
public class OpenSubCommand implements SubCommand {

    private final ArenaCore plugin;

    public OpenSubCommand(ArenaCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        ArenaManager manager = plugin.getArenaManager();
        if (CommandHelper.requireActiveSession(sender, manager) == null) return;

        if (!manager.openBetting()) {
            ArenaSession session = manager.getActiveSession();
            if (session == null) {
                sender.sendMessage(ArenaMessages.PREFIX + ChatColor.RED
                        + "セッションが存在しません。");
                return;
            }
            if (session.getState() != ArenaState.SETUP) {
                sender.sendMessage(ArenaMessages.PREFIX + ChatColor.RED
                        + ArenaMessages.MSG_OPEN_SETUP_ONLY);
            } else {
                // TP未設定エラーは ArenaManager 側で broadcastMessage 済み
                // メンバー不足チェック
                int teamsWithMembers = 0;
                for (String team : session.getTeamNames()) {
                    boolean hasPlayer = session.getTeamSize(team) > 0;
                    boolean hasMob = false;
                    var areaConfig = session.getTeamAreaConfig(team);
                    if (areaConfig != null) {
                        hasMob = !areaConfig.scanEntities().isEmpty();
                    }
                    if (hasPlayer || hasMob) {
                        teamsWithMembers++;
                    }
                }
                if (teamsWithMembers < 2) {
                    sender.sendMessage(ArenaMessages.PREFIX + ChatColor.RED
                            + ArenaMessages.MSG_MIN_TEAMS_REQUIRED);
                    for (String team : session.getTeamNames()) {
                        boolean hasPlayer = session.getTeamSize(team) > 0;
                        boolean hasMob = false;
                        var areaConfig = session.getTeamAreaConfig(team);
                        if (areaConfig != null) {
                            hasMob = !areaConfig.scanEntities().isEmpty();
                        }
                        if (!hasPlayer && !hasMob) {
                            sender.sendMessage(ArenaMessages.PREFIX + ChatColor.GRAY
                                    + "  ✗ " + team + " (メンバーなし)");
                        }
                    }
                }
            }
            return;
        }

        ArenaSession session = manager.getActiveSession();
        double houseEdge = plugin.getConfig().getDouble("house-edge", 0.1);

        Bukkit.broadcastMessage(ArenaMessages.SEPARATOR);
        Bukkit.broadcastMessage(ArenaMessages.PREFIX + ChatColor.GOLD + ChatColor.BOLD + "賭け受付開始！");
        Bukkit.broadcastMessage("");
        Bukkit.broadcastMessage(ArenaMessages.PREFIX + ChatColor.GRAY
                + "各チームの賭けエリアにカーペット（チップ）を置いて賭けよう！");
        Bukkit.broadcastMessage(ArenaMessages.PREFIX + ChatColor.GRAY
                + "手数料率: " + ChatColor.YELLOW + String.format("%.0f%%", houseEdge * 100));

        List<String> teamNames = session.getTeamNames();
        for (int i = 0; i < teamNames.size(); i++) {
            String team = teamNames.get(i);
            ChatColor color = session.getTeamColor(team);
            boolean hasRegion = plugin.getRegionManager().hasBettingRegion(team);

            String label = ArenaMessages.formatTeamLabel(session, team);

            Bukkit.broadcastMessage(ArenaMessages.PREFIX + "  " + color + "■ " + team
                    + ChatColor.GRAY + " (" + label + ")"
                    + (hasRegion ? ChatColor.GREEN + " エリア設定済" : ChatColor.RED + " エリア未設定"));
        }
        Bukkit.broadcastMessage(ArenaMessages.SEPARATOR);
    }

    @Override
    public String getUsage() {
        return "/arena open";
    }
}
