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
 * {@code /arena start} — 試合開始（賭け締切）を処理する。
 */
public class StartSubCommand implements SubCommand {

    private final ArenaCore plugin;

    public StartSubCommand(ArenaCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        ArenaManager manager = plugin.getArenaManager();
        if (CommandHelper.requireActiveSession(sender, manager) == null) return;

        if (!manager.startMatch()) {
            ArenaSession session = manager.getActiveSession();
            if (session == null) {
                sender.sendMessage(ArenaMessages.PREFIX + ChatColor.RED
                        + ArenaMessages.MSG_START_BETTING_ONLY);
            } else {
                ArenaState state = session.getState();
                if (state != ArenaState.BETTING && state != ArenaState.CLOSED) {
                    sender.sendMessage(ArenaMessages.PREFIX + ChatColor.RED
                            + "賭け受付中または賭け締切後でなければ試合を開始できません。");
                }
            }
            // TP未設定エラーは ArenaManager 側で broadcastMessage 済み
            return;
        }

        ArenaSession session = manager.getActiveSession();

        Bukkit.broadcastMessage(ArenaMessages.SEPARATOR);
        Bukkit.broadcastMessage(ArenaMessages.PREFIX + ChatColor.RED + ChatColor.BOLD + "試合開始！");
        Bukkit.broadcastMessage(ArenaMessages.PREFIX + ChatColor.YELLOW + "賭けは締め切りました！");
        Bukkit.broadcastMessage("");
        plugin.getBettingManager().broadcastOdds(session);
        Bukkit.broadcastMessage(ArenaMessages.SEPARATOR);
    }

    @Override
    public String getUsage() {
        return "/arena start";
    }
}
