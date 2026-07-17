package io.wax100.arenaCore.command.sub;

import io.wax100.arenaCore.ArenaCore;
import io.wax100.arenaCore.command.CommandHelper;
import io.wax100.arenaCore.command.SubCommand;
import io.wax100.arenaCore.manager.SpectatorHealthManager;
import io.wax100.arenaCore.manager.SpectatorHealthManager.DisplayMode;
import io.wax100.arenaCore.util.ArenaMessages;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * {@code /arena hp <bossbar|sidebar|off>} コマンドを処理する（観客向け）。
 *
 * <p>観客時の競技者HP表示方式を切り替える。選択はプレイヤーごとに保存され、
 * 再ログイン後も保持される。引数なしの場合は現在の設定を表示する。
 */
public class HpSubCommand implements SubCommand {

    private final ArenaCore plugin;

    public HpSubCommand(ArenaCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ArenaMessages.PREFIX + ChatColor.RED
                    + "このコマンドはプレイヤーのみ実行できます。");
            return;
        }

        SpectatorHealthManager manager = plugin.getSpectatorHealthManager();
        if (args.length == 0) {
            player.sendMessage(ArenaMessages.PREFIX + ChatColor.GRAY
                    + "現在のHP表示方式: " + ChatColor.YELLOW + ChatColor.BOLD
                    + displayName(manager.getMode(player)));
            player.sendMessage(ArenaMessages.PREFIX + ChatColor.GRAY
                    + "切り替え: " + ChatColor.YELLOW + getUsage());
            return;
        }

        DisplayMode mode = DisplayMode.fromString(args[0]);
        if (mode == null) {
            player.sendMessage(ArenaMessages.PREFIX + ChatColor.RED
                    + "bossbar / sidebar / off のいずれかを指定してください。");
            return;
        }

        manager.setMode(player, mode);
        player.sendMessage(ArenaMessages.PREFIX + ChatColor.AQUA
                + "競技者HPの表示方式を " + ChatColor.YELLOW + ChatColor.BOLD
                + displayName(mode) + ChatColor.AQUA + " に切り替えました。");
    }

    /** 表示方式の日本語表示名を返す。 */
    private static String displayName(DisplayMode mode) {
        return switch (mode) {
            case BOSSBAR -> "ボスバー";
            case SIDEBAR -> "サイドバー";
            case OFF -> "非表示";
        };
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            return CommandHelper.filterStartsWith(
                    List.of("bossbar", "sidebar", "off"), args[0]);
        }
        return List.of();
    }

    @Override
    public String getUsage() {
        return "/arena hp <bossbar|sidebar|off>";
    }
}
