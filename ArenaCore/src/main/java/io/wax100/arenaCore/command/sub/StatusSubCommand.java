package io.wax100.arenaCore.command.sub;

import io.wax100.arenaCore.ArenaCore;
import io.wax100.arenaCore.command.CommandHelper;
import io.wax100.arenaCore.command.SubCommand;
import io.wax100.arenaCore.manager.ArenaManager;
import io.wax100.arenaCore.model.ArenaSession;
import io.wax100.arenaCore.util.ArenaMessages;
import io.wax100.chipLib.ChipManager;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

import java.util.List;

/**
 * {@code /arena status} — セッション状態表示を処理する。
 */
public class StatusSubCommand implements SubCommand {

    private final ArenaCore plugin;

    public StatusSubCommand(ArenaCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        ArenaManager manager = plugin.getArenaManager();
        if (!manager.hasActiveSession()) {
            sender.sendMessage(ArenaMessages.PREFIX + ChatColor.GRAY + "現在、闘技場セッションはありません。");
            return;
        }

        ArenaSession session = manager.getActiveSession();
        sender.sendMessage(ChatColor.DARK_RED.toString() + ChatColor.BOLD
                + "═══ 闘技場「" + session.getName() + "」═══");
        sender.sendMessage(ArenaMessages.PREFIX + ChatColor.GRAY + "状態: "
                + ChatColor.YELLOW + session.getState().getDisplayName());
        sender.sendMessage(ArenaMessages.PREFIX + ChatColor.GRAY + "配当方式: "
                + ChatColor.YELLOW + plugin.getConfig().getString("payout-method", "pari-mutuel"));
        sender.sendMessage(ArenaMessages.PREFIX + ChatColor.GRAY + "勝利条件: "
                + ChatColor.YELLOW + plugin.getConfig().getString("win-condition", "last-team-standing"));
        sender.sendMessage(ArenaMessages.PREFIX + ChatColor.GRAY + "総プール: "
                + ChatColor.YELLOW + ChipManager.formatAmount(session.getTotalPool()) + " E");

        List<String> teamNames = session.getTeamNames();
        for (int i = 0; i < teamNames.size(); i++) {
            String team = teamNames.get(i);
            ChatColor color = ArenaMessages.getTeamColor(i);

            String label = session.isMobTeam(team)
                    ? "[MOB] " + session.getEffectiveTeamSize(team) + "体"
                    : session.getTeamSize(team) + "人";

            sender.sendMessage(ArenaMessages.PREFIX + "  " + color + "■ " + team
                    + ChatColor.GRAY + " (" + label + ")"
                    + " | 賭け: " + ChatColor.WHITE + ChipManager.formatAmount(session.getTeamPool(team)) + " E"
                    + " | スコア: " + ChatColor.WHITE + session.getScore(team));
        }
    }

    @Override
    public String getUsage() {
        return "/arena status";
    }
}
