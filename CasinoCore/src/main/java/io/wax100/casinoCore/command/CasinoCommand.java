package io.wax100.casinoCore.command;

import io.wax100.casinoCore.CasinoCore;
import io.wax100.casinoCore.manager.CasinoManager;
import io.wax100.casinoCore.manager.PlayerStats;
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
 *   <li>{@code /casino stats} — プレイヤー統計を表示</li>
 * </ul>
 *
 * @see CasinoManager
 */
public class CasinoCommand implements CommandExecutor, TabCompleter {

    /**
     * サブコマンド一覧（タブ補完用）
     */
    private static final List<String> SUB_COMMANDS = Arrays.asList("on", "off", "status", "ranking", "stats");
    /**
     * 管理者権限が必要なサブコマンド
     */
    private static final List<String> ADMIN_SUB_COMMANDS = Arrays.asList("on", "off");
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
                handleOn(sender, args);
                break;
            case "off":
                handleOff(sender, args);
                break;
            case "status":
                handleStatus(sender);
                break;
            case "ranking":
                handleRanking(sender, args);
                break;
            case "stats":
                handleStats(sender, args);
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
     * <p>プレイヤー名が指定された場合（{@code args.length >= 2}）、対象プレイヤーを
     * 個別にカジノモードへ追加する。指定がない場合は全体のカジノモードを開始し、
     * 全プレイヤーをアドベンチャーモードに切り替える。
     *
     * @param sender コマンド実行者
     * @param args   コマンド引数（{@code args[0]}="on", {@code args[1]}=プレイヤー名（任意））
     */
    private void handleOn(CommandSender sender, String[] args) {
        if (!sender.hasPermission("casino.admin")) {
            sender.sendMessage(Messages.NO_PERMISSION);
            return;
        }

        CasinoManager manager = plugin.getCasinoManager();

        if (args.length >= 2) {
            // 個別プレイヤー追加モード
            Player target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                sender.sendMessage(String.format(Messages.PLAYER_NOT_FOUND, args[1]));
                return;
            }
            if (manager.isPlayerInCasino(target.getUniqueId())) {
                sender.sendMessage(String.format(Messages.PLAYER_ALREADY_IN_CASINO, target.getName()));
                return;
            }
            manager.addPlayerToCasino(target);
            sender.sendMessage(String.format(Messages.PLAYER_ADDED, target.getName()));
            if (!target.equals(sender)) {
                target.sendMessage(Messages.YOU_ADDED);
            }
            return;
        }

        // 全体カジノモード開始
        if (manager.isCasinoActive()) {
            sender.sendMessage(Messages.CASINO_ALREADY_ON);
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
     * <p>プレイヤー名が指定された場合（{@code args.length >= 2}）、対象プレイヤーを
     * 個別にカジノモードから退出させる（チップ換金＋ゲームモード復元）。
     * 指定がない場合は全体のカジノモードを終了し、全プレイヤーのチップを換金する。
     *
     * @param sender コマンド実行者
     * @param args   コマンド引数（{@code args[0]}="off", {@code args[1]}=プレイヤー名（任意））
     */
    private void handleOff(CommandSender sender, String[] args) {
        if (!sender.hasPermission("casino.admin")) {
            sender.sendMessage(Messages.NO_PERMISSION);
            return;
        }

        CasinoManager manager = plugin.getCasinoManager();

        if (args.length >= 2) {
            // 個別プレイヤー退出モード
            Player target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                sender.sendMessage(String.format(Messages.PLAYER_NOT_FOUND, args[1]));
                return;
            }
            if (!manager.isPlayerInCasino(target.getUniqueId())) {
                sender.sendMessage(String.format(Messages.PLAYER_NOT_IN_CASINO, target.getName()));
                return;
            }
            manager.removePlayerFromCasino(target);
            sender.sendMessage(String.format(Messages.PLAYER_REMOVED, target.getName()));
            if (!target.equals(sender)) {
                target.sendMessage(Messages.YOU_REMOVED);
            }
            return;
        }

        // 全体カジノモード終了
        if (!manager.isCasinoActive()) {
            sender.sendMessage(Messages.CASINO_ALREADY_OFF);
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
     * {@code /casino ranking reset} でランキングと全統計をリセットする。
     *
     * @param sender コマンド実行者
     * @param args   コマンド引数
     */
    private void handleRanking(CommandSender sender, String[] args) {
        if (args.length >= 2 && args[1].equalsIgnoreCase("reset")) {
            if (!sender.hasPermission("casino.admin")) {
                sender.sendMessage(Messages.NO_PERMISSION);
                return;
            }
            plugin.getCasinoManager().resetRanking();
            sender.sendMessage(Messages.RANKING_RESET);
            return;
        }

        int size = plugin.getConfig().getInt("ranking-size", 10);
        List<Map.Entry<UUID, Long>> sorted = plugin.getCasinoManager().getSortedRanking(size);

        sender.sendMessage("");
        sender.sendMessage(ChatColor.GOLD.toString() + ChatColor.BOLD + "═══ カジノランキング（累計損益）═══");

        if (sorted.isEmpty()) {
            sender.sendMessage(Messages.NO_RANKING_DATA);
        } else {
            int rank = 1;
            for (Map.Entry<UUID, Long> entry : sorted) {
                PlayerStats stats = plugin.getCasinoManager().getStatsForPlayer(entry.getKey());
                String name = stats != null && stats.getName() != null
                        ? stats.getName()
                        : Bukkit.getOfflinePlayer(entry.getKey()).getName();
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
     * プレイヤーの詳細統計を表示する。
     *
     * <p>{@code /casino stats [player]} で指定プレイヤーの統計を表示する。
     * プレイヤー名省略時は実行者自身の統計を表示する。
     *
     * @param sender コマンド実行者
     * @param args   コマンド引数
     */
    private void handleStats(CommandSender sender, String[] args) {
        Player target;
        if (args.length >= 2) {
            target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                sender.sendMessage(String.format(Messages.PLAYER_NOT_FOUND, args[1]));
                return;
            }
        } else if (sender instanceof Player) {
            target = (Player) sender;
        } else {
            sender.sendMessage(Messages.SPECIFY_PLAYER);
            return;
        }

        PlayerStats stats = plugin.getCasinoManager().getStatsForPlayer(target.getUniqueId());
        if (stats == null) {
            sender.sendMessage(String.format(Messages.NO_STATS, target.getName()));
            return;
        }

        sender.sendMessage("");
        sender.sendMessage(ChatColor.GOLD.toString() + ChatColor.BOLD + "═══ " + target.getName() + " のカジノ統計 ═══");
        sender.sendMessage(ChatColor.GRAY + "  参加セッション数: " + ChatColor.WHITE + stats.getTotalSessions());
        sender.sendMessage(ChatColor.GRAY + "  累計購入額:     " + ChatColor.WHITE + ChipManager.formatAmount(stats.getTotalPurchases()) + " E");
        sender.sendMessage(ChatColor.GRAY + "  累計換金額:     " + ChatColor.WHITE + ChipManager.formatAmount(stats.getTotalCashouts()) + " E");

        long net = stats.getNetProfit();
        ChatColor netColor = net > 0 ? ChatColor.GREEN : (net < 0 ? ChatColor.RED : ChatColor.YELLOW);
        String netSign = net > 0 ? "+" : (net == 0 ? "±" : "");
        sender.sendMessage(ChatColor.GRAY + "  累計損益:       " + netColor + netSign + ChipManager.formatAmount(net) + " E");

        sender.sendMessage("");
        int totalGames = stats.getWins() + stats.getLosses() + stats.getDraws();
        sender.sendMessage(ChatColor.GRAY + "  勝敗: "
                + ChatColor.GREEN + stats.getWins() + "勝 " + ChatColor.RED + stats.getLosses() + "敗 "
                + ChatColor.YELLOW + stats.getDraws() + "分"
                + ChatColor.GRAY + " (計 " + totalGames + "回)");
        if (totalGames > 0) {
            sender.sendMessage(ChatColor.GRAY + "  勝率: " + ChatColor.WHITE
                    + String.format("%.1f%%", stats.getWinRate() * 100));
        }

        if (stats.getBiggestWin() > 0) {
            sender.sendMessage(ChatColor.GRAY + "  最大勝ち額: " + ChatColor.GREEN + "+"
                    + ChipManager.formatAmount(stats.getBiggestWin()) + " E");
        }
        if (stats.getBiggestLoss() < 0) {
            sender.sendMessage(ChatColor.GRAY + "  最大負け額: " + ChatColor.RED
                    + ChipManager.formatAmount(stats.getBiggestLoss()) + " E");
        }

        if (stats.getFirstPlayed() != null) {
            java.time.format.DateTimeFormatter fmt = java.time.format.DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm");
            sender.sendMessage("");
            sender.sendMessage(ChatColor.GRAY + "  初回参加: " + ChatColor.WHITE + stats.getFirstPlayed().format(fmt));
            sender.sendMessage(ChatColor.GRAY + "  最終参加: " + ChatColor.WHITE + stats.getLastPlayed().format(fmt));
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
        sender.sendMessage(ChatColor.YELLOW + "  /casino on [player] " + ChatColor.GRAY + "- カジノモード開始（プレイヤー指定で個別追加）");
        sender.sendMessage(ChatColor.YELLOW + "  /casino off [player] " + ChatColor.GRAY + "- カジノモード終了（プレイヤー指定で個別退出）");
        sender.sendMessage(ChatColor.YELLOW + "  /casino status " + ChatColor.GRAY + "- 状態確認");
        sender.sendMessage(ChatColor.YELLOW + "  /casino ranking " + ChatColor.GRAY + "- ランキング表示");
        sender.sendMessage(ChatColor.YELLOW + "  /casino ranking reset " + ChatColor.GRAY + "- ランキングリセット");
        sender.sendMessage(ChatColor.YELLOW + "  /casino stats [player] " + ChatColor.GRAY + "- プレイヤー統計表示");
    }

    /**
     * {@inheritDoc}
     *
     * <p>第1引数に対してサブコマンドの前方一致補完を提供する。
     * 管理者権限のないプレイヤーには管理系サブコマンドを表示しない。
     */
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length > 2) return Collections.emptyList();

        if (args.length == 2) {
            String sub = args[0].toLowerCase();
            if (sub.equals("on") || sub.equals("off") || sub.equals("stats")) {
                List<String> result = new ArrayList<>();
                String input = args[1].toLowerCase();
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (player.getName().toLowerCase().startsWith(input)) {
                        result.add(player.getName());
                    }
                }
                return result;
            }
            if (sub.equals("ranking")) {
                if (!sender.hasPermission("casino.admin")) return Collections.emptyList();
                String input = args[1].toLowerCase();
                if ("reset".startsWith(input)) {
                    return Collections.singletonList("reset");
                }
                return Collections.emptyList();
            }
            return Collections.emptyList();
        }

        List<String> result = new ArrayList<>();
        String input = args[0].toLowerCase();
        for (String sub : SUB_COMMANDS) {
            if (sub.startsWith(input)) {
                // 管理者権限が必要なサブコマンドは権限チェック
                if (ADMIN_SUB_COMMANDS.contains(sub) && !sender.hasPermission("casino.admin")) {
                    continue;
                }
                result.add(sub);
            }
        }
        return result;
    }
}
