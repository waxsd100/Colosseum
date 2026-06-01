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
 * {@code /arena lock [秒数]} — 参加者を締め切り、ベット受付（BETTING）に移行する。
 *
 * <p>RECRUITING 状態でのみ実行可能。各チームに最低1人、
 * 2チーム以上にメンバーがいることを検証する。
 * 秒数を指定するとベットの制限時間タイマーが開始される。
 * 省略した場合は config の {@code default-betting-duration} を使用する（0以下ならタイマーなし）。
 */
public class LockSubCommand implements SubCommand {

    private final ArenaCore plugin;

    public LockSubCommand(ArenaCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        ArenaManager manager = plugin.getArenaManager();
        ArenaSession session = CommandHelper.requireActiveSession(sender, manager);
        if (session == null) return;

        if (session.getState() != ArenaState.RECRUITING) {
            sender.sendMessage(ArenaMessages.PREFIX + ChatColor.RED
                    + "参加者募集中（RECRUITING）のみ実行できます。");
            return;
        }

        // バリデーション: 各チームに最低1人、2チーム以上にメンバー
        int teamsWithMembers = 0;
        for (String team : session.getTeamNames()) {
            int size = session.getEffectiveTeamSize(team);
            if (size > 0) {
                teamsWithMembers++;
            } else {
                ChatColor color = session.getTeamColor(team);
                sender.sendMessage(ArenaMessages.PREFIX + ChatColor.RED
                        + "✗ チーム " + color + team + ChatColor.RED + " に闘技者がいません。");
            }
        }
        if (teamsWithMembers < 2) {
            sender.sendMessage(ArenaMessages.PREFIX + ChatColor.RED
                    + "2チーム以上に闘技者が必要です。");
            return;
        }

        // ベット制限時間を解析
        int bettingSeconds = 0;
        if (args.length >= 1) {
            try {
                bettingSeconds = Integer.parseInt(args[0]);
                if (bettingSeconds <= 0) {
                    sender.sendMessage(ArenaMessages.PREFIX + ChatColor.YELLOW
                            + "秒数は1以上を指定してください。タイマーなしで続行します。");
                    bettingSeconds = 0;
                }
            } catch (NumberFormatException ignored) {
                // 数値でなければ無視
            }
        }

        // 秒数未指定時は config のデフォルト値を使用
        if (bettingSeconds <= 0) {
            bettingSeconds = plugin.getConfig().getInt("default-betting-duration", 0);
        }

        // ロック実行（RECRUITING → BETTING + アナウンス + タイマー開始）
        manager.lockParticipants(bettingSeconds);
    }

    @Override
    public String getUsage() {
        return "/arena lock [秒数]";
    }
}
