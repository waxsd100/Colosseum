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

/**
 * {@code /arena cancel} — セッションキャンセルを処理する。
 */
public class CancelSubCommand implements SubCommand {

    private final ArenaCore plugin;

    public CancelSubCommand(ArenaCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        ArenaManager manager = plugin.getArenaManager();
        ArenaSession session = CommandHelper.requireActiveSession(sender, manager);
        if (session == null) return;

        ArenaState prevState = session.getState();

        // 手動キャンセル時はオートループも無効化する。
        // cancelArena 内のクリーンアップが次試合をスケジュールする前に無効化する必要がある。
        boolean loopWasEnabled = manager.isAutoLoopEnabled();
        if (loopWasEnabled) {
            manager.setAutoLoopEnabled(false);
        }

        if (manager.cancelArena()) {
            Bukkit.broadcastMessage(ArenaMessages.SEPARATOR);
            if (prevState == ArenaState.ACTIVE) {
                Bukkit.broadcastMessage(ArenaMessages.PREFIX + ChatColor.YELLOW + ChatColor.BOLD
                        + "⚖ 引き分け！");
                Bukkit.broadcastMessage(ArenaMessages.PREFIX + ChatColor.GRAY
                        + "試合が強制終了されました。全ベット額・参加費を返金します。");
            } else {
                Bukkit.broadcastMessage(ArenaMessages.PREFIX + ChatColor.RED
                        + "闘技場がキャンセルされました。ベット額・参加費は返金されます。");
            }
            if (loopWasEnabled) {
                Bukkit.broadcastMessage(ArenaMessages.PREFIX + ChatColor.YELLOW
                        + "オートループを無効にしました。（再開: /arena loop true）");
            }
            Bukkit.broadcastMessage(ArenaMessages.SEPARATOR);
        } else {
            // キャンセルに失敗した場合はループ設定を元に戻す
            if (loopWasEnabled) {
                manager.setAutoLoopEnabled(true);
            }
            sender.sendMessage(ArenaMessages.PREFIX + ChatColor.RED
                    + "キャンセルに失敗しました。セッションの状態を確認してください。");
        }
    }

    @Override
    public String getUsage() {
        return "/arena cancel";
    }
}
