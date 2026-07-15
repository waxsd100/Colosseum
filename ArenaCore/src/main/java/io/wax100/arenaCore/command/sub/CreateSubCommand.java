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
 * {@code /arena create <名前>} を処理する。
 */
public class CreateSubCommand implements SubCommand {

    private final ArenaCore plugin;

    public CreateSubCommand(ArenaCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(CommandSender sender, String [] args) {
        if (!CommandHelper.requireArgs(sender, args, 1, getUsage())) return;

        ArenaManager manager = plugin.getArenaManager();
        if (manager.hasActiveSession()) {
            sender.sendMessage(ArenaMessages.PREFIX + ChatColor.RED
                    + ArenaMessages.MSG_SESSION_ALREADY_ACTIVE);
            return;
        }

        String name = args[0];

        ArenaSession session = manager.createArena(name, List.of());
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

        // セットアップ情報は実行者のみに表示（全体アナウンスは /arena open 時に行う）
        sender.sendMessage(ArenaMessages.SEPARATOR);
        sender.sendMessage(ArenaMessages.PREFIX + ChatColor.GOLD + ChatColor.BOLD
                + "闘技場「" + name + "」が開設されました！");
        sender.sendMessage("");

        long entryFee = session.getArenaConfig().getEntryFee();
        if (entryFee > 0) {
            sender.sendMessage(ArenaMessages.PREFIX + ChatColor.GRAY
                    + "参加費: " + ChatColor.YELLOW + ChipManager.formatAmount(entryFee) + " E");
            sender.sendMessage("");
        }

        sender.sendMessage(ArenaMessages.PREFIX + ChatColor.GRAY + "次のステップ:");
        sender.sendMessage(ChatColor.GRAY + "  1. " + ChatColor.YELLOW + "/arena team add <チーム名>");
        sender.sendMessage(ChatColor.GRAY + "  2. " + ChatColor.YELLOW + "/arena team area <チーム> [待機場名]");
        sender.sendMessage(ChatColor.GRAY + "  3. " + ChatColor.YELLOW + "/arena field set [名前]");
        sender.sendMessage(ChatColor.GRAY + "  4. " + ChatColor.YELLOW + "/arena config" + ChatColor.GRAY + " <- 設定変更");
        sender.sendMessage(ArenaMessages.SEPARATOR);
    }

    @Override
    public String getUsage() {
        return "/arena create <名前>";
    }
}
