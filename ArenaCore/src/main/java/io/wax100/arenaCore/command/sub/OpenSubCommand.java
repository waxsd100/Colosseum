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

import java.util.List;

/**
 * {@code /arena open [秒数]} — ベット受付開始を処理する。
 *
 * <p>秒数を指定すると、指定時間後に自動的にベットを締め切る。
 * 省略した場合は手動で {@code /arena close} するまで受付を継続する。
 */
public class OpenSubCommand implements SubCommand {

    private final ArenaCore plugin;

    public OpenSubCommand(ArenaCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        ArenaManager manager = plugin.getArenaManager();
        ArenaSession session = CommandHelper.requireActiveSession(sender, manager);
        if (session == null) return;

        if (!manager.openBetting()) {
            if (session.getState() != ArenaState.SETUP) {
                sender.sendMessage(ArenaMessages.PREFIX + ChatColor.RED
                        + ArenaMessages.MSG_OPEN_SETUP_ONLY);
            }
            return;
        }

        // 制限時間の解析
        int timerSeconds = 0;
        if (args.length >= 1) {
            try {
                timerSeconds = Integer.parseInt(args[0]);
                if (timerSeconds <= 0) {
                    sender.sendMessage(ArenaMessages.PREFIX + ChatColor.RED
                            + "秒数は1以上を指定してください。");
                    timerSeconds = 0;
                }
            } catch (NumberFormatException ignored) {
                // 数値でなければ無視
            }
        }

        Bukkit.broadcastMessage(ArenaMessages.SEPARATOR);
        Bukkit.broadcastMessage(ArenaMessages.PREFIX + ChatColor.GOLD + ChatColor.BOLD
                + "⚔ 闘技者募集中！");
        Bukkit.broadcastMessage("");
        Bukkit.broadcastMessage(ArenaMessages.PREFIX + ChatColor.GRAY
                + "チーム待機エリアに入って参加！");
        Bukkit.broadcastMessage("");

        List<String> teamNames = session.getTeamNames();
        for (int i = 0; i < teamNames.size(); i++) {
            String team = teamNames.get(i);
            ChatColor color = session.getTeamColor(team);

            String label = ArenaMessages.formatTeamLabel(session, team);

            Bukkit.broadcastMessage(ArenaMessages.PREFIX + "  " + color + "■ " + team
                    + ChatColor.GRAY + " (" + label + ")");
        }

        if (timerSeconds > 0) {
            Bukkit.broadcastMessage("");
            Bukkit.broadcastMessage(ArenaMessages.PREFIX + ChatColor.YELLOW + ChatColor.BOLD
                    + "⏱ 募集制限時間: " + timerSeconds + "秒");
        }
        Bukkit.broadcastMessage(ArenaMessages.SEPARATOR);

        // 募集タイマー開始
        if (timerSeconds > 0) {
            manager.scheduleRecruitingTimer(timerSeconds);
        }
    }

    @Override
    public String getUsage() {
        return "/arena open [秒数]";
    }
}
