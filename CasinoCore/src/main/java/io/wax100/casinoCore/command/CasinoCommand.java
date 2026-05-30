package io.wax100.casinoCore.command;

import io.wax100.casinoCore.CasinoCore;
import io.wax100.casinoCore.manager.CasinoManager;
import io.wax100.chipLib.ChipManager;
import io.wax100.casinoCore.util.Messages;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * {@code /casino} コマンドハンドラ。
 *
 * <p>サブコマンド:
 * <ul>
 *   <li>{@code /casino on} — カジノモードを開始し、全プレイヤーをアドベンチャーモードに切替</li>
 *   <li>{@code /casino off} — カジノモードを終了し、全プレイヤーのチップを換金</li>
 *   <li>{@code /casino status} — 現在のカジノモードの ON/OFF 状態を表示</li>
 *   <li>{@code /casino ranking} — 累計損益ランキングを表示</li>
 * </ul>
 *
 * @see CasinoManager
 */
public class CasinoCommand implements CommandExecutor, TabCompleter {

    /**
     * サブコマンド一覧（タブ補完用）
     */
    private static final List<String> SUB_COMMANDS = Arrays.asList("on", "off", "status", "ranking");
    /**
     * プラグインインスタンス
     */
    private final CasinoCore plugin;

    /**
     * コンストラクタ。
     *
     * @param plugin CasinoCore プラグインインスタンス
     */
    public CasinoCommand(CasinoCore plugin) {
        this.plugin = plugin;
    }

    /**
     * {@inheritDoc}
     *
     * <p>第1引数のサブコマンドに応じて各ハンドラメソッドに振り分ける。
     * 引数がない場合や不明なサブコマンドの場合はヘルプを表示する。
     */
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "on":
                handleOn(sender);
                break;
            case "off":
                handleOff(sender);
                break;
            case "status":
                handleStatus(sender);
                break;
            case "ranking":
                handleRanking(sender);
                break;
            default:
                sendUsage(sender);
                break;
        }
        return true;
    }

    /**
     * カジノモードを開始する。
     *
     * <p>実行者がプレイヤーの場合、全プレイヤーをアドベンチャーモードに切り替え、
     * keepInventory を有効化する。カジノモードが既に ON の場合は警告メッセージを送信する。
     *
     * @param sender コマンド実行者
     */
    private void handleOn(CommandSender sender) {
        CasinoManager manager = plugin.getCasinoManager();
        if (manager.isCasinoActive()) {
            sender.sendMessage(Messages.PREFIX + ChatColor.YELLOW + "カジノモードは既にONです。");
            return;
        }
        manager.setCasinoActive(true);

        // ゲームモード切替 + keepInventory ON
        if (sender instanceof Player) {
            manager.applyAdventureMode((Player) sender);
        }

        Bukkit.broadcastMessage(Messages.SEPARATOR);
        Bukkit.broadcastMessage(Messages.PREFIX + ChatColor.GREEN + "カジノモードが "
                + ChatColor.YELLOW + ChatColor.BOLD + "ON "
                + ChatColor.RESET + ChatColor.GREEN + "になりました！");
        Bukkit.broadcastMessage(Messages.PREFIX + ChatColor.GRAY
                + "/chip <額面> <枚数> または /chip <金額> でチップを購入できます。");
        Bukkit.broadcastMessage(Messages.PREFIX + ChatColor.GRAY
                + "/chip info でチップ一覧を確認できます。");
        Bukkit.broadcastMessage(Messages.SEPARATOR);
    }

    /**
     * カジノモードを終了する。
     *
     * <p>全プレイヤーのチップを換金し、ゲームモードを復元し、
     * セッションデータをクリアする。カジノモードが既に OFF の場合は警告メッセージを送信する。
     *
     * @param sender コマンド実行者
     */
    private void handleOff(CommandSender sender) {
        CasinoManager manager = plugin.getCasinoManager();
        if (!manager.isCasinoActive()) {
            sender.sendMessage(Messages.PREFIX + ChatColor.YELLOW + "カジノモードは既にOFFです。");
            return;
        }

        Bukkit.broadcastMessage(Messages.SEPARATOR);
        Bukkit.broadcastMessage(Messages.PREFIX + ChatColor.RED + "カジノモードを終了します。チップを換金します...");
        Bukkit.broadcastMessage(Messages.SEPARATOR);

        manager.cashoutAllPlayers();
        manager.restoreGameModes();
        manager.setCasinoActive(false);
        manager.clearAllSessionData();

        Bukkit.broadcastMessage(Messages.SEPARATOR);
        Bukkit.broadcastMessage(Messages.PREFIX + ChatColor.RED + "カジノモードを終了しました。");
        Bukkit.broadcastMessage(Messages.PREFIX + ChatColor.YELLOW + "またのご来店をお待ちしております！");
        Bukkit.broadcastMessage(Messages.SEPARATOR);
    }

    /**
     * カジノモードの現在の状態（ON/OFF）を表示する。
     *
     * @param sender コマンド実行者
     */
    private void handleStatus(CommandSender sender) {
        boolean active = plugin.getCasinoManager().isCasinoActive();
        String status = active
                ? ChatColor.GREEN.toString() + ChatColor.BOLD + "ON"
                : ChatColor.RED.toString() + ChatColor.BOLD + "OFF";
        sender.sendMessage(Messages.PREFIX + ChatColor.GRAY + "カジノモード: " + status);
    }

    /**
     * 累計損益ランキングを表示する。
     *
     * <p>設定ファイルの {@code ranking-size} で表示件数を制御できる（デフォルト: 10）。
     *
     * @param sender コマンド実行者
     */
    private void handleRanking(CommandSender sender) {
        int size = plugin.getConfig().getInt("ranking-size", 10);
        List<Map.Entry<UUID, Long>> sorted = plugin.getCasinoManager().getSortedRanking(size);

        sender.sendMessage("");
        sender.sendMessage(ChatColor.GOLD.toString() + ChatColor.BOLD + "═══ カジノランキング（累計損益）═══");

        if (sorted.isEmpty()) {
            sender.sendMessage(ChatColor.GRAY + "ランキングデータがありません。");
        } else {
            int rank = 1;
            for (Map.Entry<UUID, Long> entry : sorted) {
                String name = Bukkit.getOfflinePlayer(entry.getKey()).getName();
                if (name == null) name = "???";
                long value = entry.getValue();
                String prefix = value >= 0
                        ? ChatColor.GREEN + "+"
                        : ChatColor.RED.toString();
                sender.sendMessage(ChatColor.YELLOW.toString() + rank + ". "
                        + ChatColor.WHITE + name + " " + ChatColor.GRAY + "- "
                        + prefix + ChipManager.formatAmount(value) + " E");
                rank++;
            }
        }

        sender.sendMessage(ChatColor.GOLD.toString() + ChatColor.BOLD + "══════════════════════");
        sender.sendMessage("");
    }

    /**
     * コマンドの使い方ヘルプを送信する。
     *
     * @param sender コマンド実行者
     */
    private void sendUsage(CommandSender sender) {
        sender.sendMessage(Messages.PREFIX + ChatColor.GRAY + "使い方:");
        sender.sendMessage(ChatColor.YELLOW + "  /casino on " + ChatColor.GRAY + "- カジノモード開始");
        sender.sendMessage(ChatColor.YELLOW + "  /casino off " + ChatColor.GRAY + "- カジノモード終了（換金）");
        sender.sendMessage(ChatColor.YELLOW + "  /casino status " + ChatColor.GRAY + "- 状態確認");
        sender.sendMessage(ChatColor.YELLOW + "  /casino ranking " + ChatColor.GRAY + "- ランキング表示");
    }

    /**
     * {@inheritDoc}
     *
     * <p>第1引数に対してサブコマンドの前方一致補完を提供する。
     */
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1) return Collections.emptyList();
        List<String> result = new ArrayList<>();
        String input = args[0].toLowerCase();
        for (String sub : SUB_COMMANDS) {
            if (sub.startsWith(input)) result.add(sub);
        }
        return result;
    }
}
