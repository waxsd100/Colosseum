package io.wax100.arenaCore.command;

import io.wax100.arenaCore.ArenaCore;
import io.wax100.arenaCore.command.sub.*;
import io.wax100.arenaCore.util.ArenaMessages;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.*;

/**
 * {@code /arena} コマンドハンドラ（管理者用）。
 *
 * <p>サブコマンドパターンにより、各機能を独立したクラスに委譲する。
 * このクラスはディスパッチとヘルプ表示のみを担当する。
 */
public class ArenaCommand implements CommandExecutor, TabCompleter {

    private final Map<String, SubCommand> subCommands = new LinkedHashMap<>();

    public ArenaCommand(ArenaCore plugin) {
        subCommands.put("create", new CreateSubCommand(plugin));
        subCommands.put("team", new TeamSubCommand(plugin));
        subCommands.put("area", new AreaSubCommand(plugin));
        subCommands.put("region", new RegionSubCommand(plugin));
        subCommands.put("field", new FieldSubCommand(plugin));
        subCommands.put("preset", new PresetSubCommand(plugin));
        subCommands.put("open", new OpenSubCommand(plugin));
        subCommands.put("start", new StartSubCommand(plugin));
        subCommands.put("win", new WinSubCommand(plugin));
        subCommands.put("cancel", new CancelSubCommand(plugin));
        subCommands.put("status", new StatusSubCommand(plugin));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }

        SubCommand sub = subCommands.get(args[0].toLowerCase());
        if (sub == null) {
            sendUsage(sender);
            return true;
        }

        // サブコマンド名を除いた残りの引数を渡す
        String[] subArgs = Arrays.copyOfRange(args, 1, args.length);
        sub.execute(sender, subArgs);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return CommandHelper.filterStartsWith(subCommands.keySet(), args[0]);
        }

        SubCommand sub = subCommands.get(args[0].toLowerCase());
        if (sub != null) {
            String[] subArgs = Arrays.copyOfRange(args, 1, args.length);
            return sub.tabComplete(sender, subArgs);
        }

        return List.of();
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(ArenaMessages.PREFIX + ChatColor.GRAY + "使い方:");
        sender.sendMessage(ChatColor.YELLOW + "  /arena create <名前>");
        sender.sendMessage(ChatColor.YELLOW + "  /arena team add <チーム名>" + ChatColor.GRAY + " <- チーム追加");
        sender.sendMessage(ChatColor.YELLOW + "  /arena team list");
        sender.sendMessage(ChatColor.YELLOW + "  /arena team area <チーム> [待機場名]" + ChatColor.GRAY + " <- 待機場設定/リンク");
        sender.sendMessage(ChatColor.YELLOW + "  /arena team dest <チーム名>" + ChatColor.GRAY + " <- 現在地をTP先に");
        sender.sendMessage(ChatColor.YELLOW + "  /arena team color <チーム名> <色>" + ChatColor.GRAY + " <- チームカラー設定");
        sender.sendMessage(ChatColor.YELLOW + "  /arena area save/list/delete/info" + ChatColor.GRAY + " <- 待機場管理");
        sender.sendMessage(ChatColor.YELLOW + "  /arena region <チーム名>" + ChatColor.GRAY + " <- WE選択範囲を賭けエリアに");
        sender.sendMessage(ChatColor.YELLOW + "  /arena field set [名前]" + ChatColor.GRAY + " <- 戦闘エリア設定");
        sender.sendMessage(ChatColor.YELLOW + "  /arena field save/list/delete" + ChatColor.GRAY + " <- 戦闘エリア管理");
        sender.sendMessage(ChatColor.YELLOW + "  /arena preset save/load/list/delete" + ChatColor.GRAY + " <- プリセット管理");
        sender.sendMessage(ChatColor.YELLOW + "  /arena open" + ChatColor.GRAY + " <- 賭け受付開始");
        sender.sendMessage(ChatColor.YELLOW + "  /arena start" + ChatColor.GRAY + " <- 試合開始（待機場から自動登録）");
        sender.sendMessage(ChatColor.YELLOW + "  /arena win <チーム名>");
        sender.sendMessage(ChatColor.YELLOW + "  /arena cancel");
        sender.sendMessage(ChatColor.YELLOW + "  /arena status");
    }
}
