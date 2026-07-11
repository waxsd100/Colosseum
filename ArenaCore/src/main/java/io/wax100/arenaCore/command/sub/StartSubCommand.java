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
 * {@code /arena start} — 試合開始（ベット締切）を処理する。
 */
public class StartSubCommand implements SubCommand {

    private final ArenaCore plugin;

    public StartSubCommand(ArenaCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        ArenaManager manager = plugin.getArenaManager();
        ArenaSession session = CommandHelper.requireActiveSession(sender, manager);
        if (session == null) return;

        if (!manager.startMatch()) {
            ArenaState state = session.getState();
            if (state != ArenaState.CLOSED) {
                sender.sendMessage(ArenaMessages.PREFIX + ChatColor.RED
                        + "ベット締切後でなければ試合を開始できません。（現在: " + state.getDisplayName() + "）");
            }
            // TP未設定エラーは ArenaManager 側で broadcastMessage 済み
            return;
        }

        Bukkit.broadcastMessage(ArenaMessages.SEPARATOR);
        Bukkit.broadcastMessage(ArenaMessages.PREFIX + ChatColor.RED + ChatColor.BOLD + "⚔ 試合開始！");
        plugin.getBettingManager().broadcastOdds(session);
        Bukkit.broadcastMessage(ArenaMessages.SEPARATOR);
    }

    @Override
    public String getUsage() {
        return "/arena start";
    }
}
