package io.wax100.arenaCore.command;

import io.wax100.arenaCore.ArenaCore;
import io.wax100.arenaCore.command.sub.*;
import io.wax100.arenaCore.model.ArenaSession;
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

    /** 管理者権限なしで実行できるプレイヤー向けサブコマンド（内部で闘技者チェック等を行う） */
    private static final Set<String> PLAYER_SUB_COMMANDS = Set.of("deathmatch");

    private final ArenaCore plugin;
    private final Map<String, SubCommand> subCommands = new LinkedHashMap<>();

    public ArenaCommand(ArenaCore plugin) {
        this.plugin = plugin;
        subCommands.put("create", new CreateSubCommand(plugin));
        subCommands.put("team", new TeamSubCommand(plugin));
        subCommands.put("region", new RegionSubCommand(plugin));
        subCommands.put("field", new FieldSubCommand(plugin));
        subCommands.put("preset", new PresetSubCommand(plugin));
        subCommands.put("open", new OpenSubCommand(plugin));
        subCommands.put("lock", new LockSubCommand(plugin));
        subCommands.put("close", new CloseSubCommand(plugin));
        subCommands.put("start", new StartSubCommand(plugin));
        subCommands.put("win", new WinSubCommand(plugin));
        subCommands.put("cancel", new CancelSubCommand(plugin));
        subCommands.put("status", new StatusSubCommand(plugin));
        subCommands.put("deathmatch", new DeathmatchSubCommand(plugin));
        subCommands.put("config", new ConfigSubCommand(plugin));
        subCommands.put("jackpot", new JackpotSubCommand(plugin));
        subCommands.put("loop", new LoopSubCommand(plugin));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String [] args) {
        boolean isAdmin = sender.hasPermission("arenacore.admin");

        if (args.length == 0) {
            if (isAdmin) {
                sendUsage(sender);
            } else {
                sendPlayerUsage(sender);
            }
            return true;
        }

        String subName = args[0].toLowerCase();
        SubCommand sub = subCommands.get(subName);
        if (sub == null) {
            if (isAdmin) {
                sendUsage(sender);
            } else {
                sendPlayerUsage(sender);
            }
            return true;
        }

        // 管理サブコマンドは admin 権限が必要（deathmatch 等は誰でも実行可）
        if (!isAdmin && !PLAYER_SUB_COMMANDS.contains(subName)) {
            sender.sendMessage(ArenaMessages.PREFIX + ChatColor.RED + "権限がありません。");
            return true;
        }

        // サブコマンド名を除いた残りの引数を渡す
        String[] subArgs = Arrays.copyOfRange(args, 1, args.length);
        sub.execute(sender, subArgs);

        // 次ステップのヒントを表示（運営向けのため管理者のみ）
        if (isAdmin) {
            sendNextStepHint(sender);
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String [] args) {
        boolean isAdmin = sender.hasPermission("arenacore.admin");

        if (args.length == 1) {
            return CommandHelper.filterStartsWith(
                    isAdmin ? subCommands.keySet() : PLAYER_SUB_COMMANDS, args[0]);
        }

        String subName = args[0].toLowerCase();
        if (!isAdmin && !PLAYER_SUB_COMMANDS.contains(subName)) {
            return List.of();
        }

        SubCommand sub = subCommands.get(subName);
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
        sender.sendMessage(ChatColor.YELLOW + "  /arena team area <チーム>" + ChatColor.GRAY + " <- WE選択範囲を待機場に");
        sender.sendMessage(ChatColor.YELLOW + "  /arena team dest <チーム名>" + ChatColor.GRAY + " <- 現在地をTP先に");
        sender.sendMessage(ChatColor.YELLOW + "  /arena team color <チーム名> <色>");
        sender.sendMessage(ChatColor.YELLOW + "  /arena region <チーム名>" + ChatColor.GRAY + " <- WE選択範囲をベットエリアに");
        sender.sendMessage(ChatColor.YELLOW + "  /arena field set" + ChatColor.GRAY + " <- WE選択範囲を戦闘エリアに");
        sender.sendMessage(ChatColor.YELLOW + "  /arena preset save/load/list/delete" + ChatColor.GRAY + " <- 全設定の保存・復元");
        sender.sendMessage(ChatColor.YELLOW + "  /arena open [秒数]" + ChatColor.GRAY + " <- 参加者募集開始");
        sender.sendMessage(ChatColor.YELLOW + "  /arena lock [秒数]" + ChatColor.GRAY + " <- 参加者締切 → ベット受付開始");
        sender.sendMessage(ChatColor.YELLOW + "  /arena close" + ChatColor.GRAY + " <- ベット締切");
        sender.sendMessage(ChatColor.YELLOW + "  /arena start" + ChatColor.GRAY + " <- 試合開始");
        sender.sendMessage(ChatColor.YELLOW + "  /arena win <チーム名>");
        sender.sendMessage(ChatColor.YELLOW + "  /arena cancel" + ChatColor.GRAY + " <- 中止（試合中なら引き分け）");
        sender.sendMessage(ChatColor.YELLOW + "  /arena deathmatch <金額|yes|no|cancel|info>" + ChatColor.GRAY + " <- デスマッチ提案・投票");
        sender.sendMessage(ChatColor.YELLOW + "  /arena config [<設定名> <値>]" + ChatColor.GRAY + " <- 動的設定");
        sender.sendMessage(ChatColor.YELLOW + "  /arena loop <true|false>" + ChatColor.GRAY + " <- 試合の自動ループ");
        sender.sendMessage(ChatColor.YELLOW + "  /arena jackpot [add <金額>]" + ChatColor.GRAY + " <- 残高確認・自腹で積立");
        sender.sendMessage(ChatColor.YELLOW + "  /arena status");
    }

    /**
     * 非管理者（闘技者・観客）向けの使い方を表示する。
     */
    private void sendPlayerUsage(CommandSender sender) {
        sender.sendMessage(ArenaMessages.PREFIX + ChatColor.GRAY + "使い方:");
        sender.sendMessage(ChatColor.YELLOW + "  /arena deathmatch <金額|all>" + ChatColor.GRAY + " <- デスマッチ提案（闘技者）");
        sender.sendMessage(ChatColor.YELLOW + "  /arena deathmatch yes/no" + ChatColor.GRAY + " <- 投票（闘技者）");
        sender.sendMessage(ChatColor.YELLOW + "  /arena deathmatch info" + ChatColor.GRAY + " <- 投票状況");
    }

    /**
     * 現在のセッション状態に基づいて、次に打つべきコマンドのヒントを送信する。
     */
    private void sendNextStepHint(CommandSender sender) {
        ArenaSession session = plugin.getArenaManager().hasActiveSession()
                ? plugin.getArenaManager().getActiveSession()
                : null;
        String[] hints = ArenaMessages.getNextStepHint(session);
        for (String hint : hints) {
            sender.sendMessage(ArenaMessages.PREFIX + hint);
        }
    }
}
