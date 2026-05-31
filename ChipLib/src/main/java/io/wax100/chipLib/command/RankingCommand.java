package io.wax100.chipLib.command;

import io.wax100.chipLib.ChipManager;
import io.wax100.chipLib.ChipPlugin;
import io.wax100.chipLib.ranking.RankingManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * {@code /ranking} コマンドハンドラ。
 *
 * <p>カテゴリ別ランキング（カジノ・アリーナ）および総合ランキングの表示・リセットを提供する。
 * <ul>
 *   <li>{@code /ranking} or {@code /ranking total} → 総合ランキング表示</li>
 *   <li>{@code /ranking casino} → カジノランキング表示</li>
 *   <li>{@code /ranking arena} → アリーナランキング表示</li>
 *   <li>{@code /ranking reset [casino|arena|all]} → リセット（OP権限）</li>
 * </ul>
 *
 * @see RankingManager
 */
public class RankingCommand implements CommandExecutor, TabCompleter {

    /**
     * プラグインインスタンス
     */
    private final ChipPlugin plugin;

    /**
     * コンストラクタ。
     *
     * @param plugin ChipPlugin プラグインインスタンス
     */
    public RankingCommand(ChipPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        RankingManager rankingManager = plugin.getRankingManager();

        if (args.length == 0 || args[0].equalsIgnoreCase("total")) {
            displayRanking(sender, rankingManager, "total", "総合ランキング");
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "casino":
                displayRanking(sender, rankingManager, "casino", "カジノランキング");
                break;
            case "arena":
                displayRanking(sender, rankingManager, "arena", "アリーナランキング");
                break;
            case "reset":
                handleReset(sender, rankingManager, args);
                break;
            default:
                sendUsage(sender);
                break;
        }
        return true;
    }

    /**
     * ランキングを表示する。
     *
     * @param sender         コマンド実行者
     * @param rankingManager ランキングマネージャ
     * @param category       カテゴリ名（"total" の場合は総合ランキング）
     * @param title          表示タイトル
     */
    private void displayRanking(CommandSender sender, RankingManager rankingManager,
                                String category, String title) {
        int size = 10;
        List<Map.Entry<UUID, Long>> sorted;
        if ("total".equals(category)) {
            sorted = rankingManager.getTotalRanking(size);
        } else {
            sorted = rankingManager.getSortedRanking(category, size);
        }

        sender.sendMessage("");
        sender.sendMessage(ChatColor.GOLD.toString() + ChatColor.BOLD + "═══ " + title + " ═══");

        if (sorted.isEmpty()) {
            sender.sendMessage(ChatColor.GRAY + "  ランキングデータがありません。");
        } else {
            int rank = 1;
            for (Map.Entry<UUID, Long> entry : sorted) {
                String name = resolvePlayerName(entry.getKey());
                long value = entry.getValue();
                String prefix = value >= 0
                        ? ChatColor.GREEN + "+"
                        : ChatColor.RED.toString();
                sender.sendMessage(ChatColor.YELLOW + "  " + rank + ". "
                        + ChatColor.WHITE + name + "  "
                        + prefix + ChipManager.formatAmount(value) + " E");
                rank++;
            }
        }

        sender.sendMessage(ChatColor.GOLD.toString() + ChatColor.BOLD + "═══════════════════");
        sender.sendMessage("");
    }

    /**
     * ランキングリセット処理。
     *
     * @param sender         コマンド実行者
     * @param rankingManager ランキングマネージャ
     * @param args           コマンド引数
     */
    private void handleReset(CommandSender sender, RankingManager rankingManager, String[] args) {
        if (!sender.isOp()) {
            sender.sendMessage(ChatColor.RED + "このコマンドはOP権限が必要です。");
            return;
        }

        if (args.length < 2) {
            sender.sendMessage(ChatColor.YELLOW + "使い方: /ranking reset <casino|arena|all>");
            return;
        }

        String target = args[1].toLowerCase();
        switch (target) {
            case "casino":
                rankingManager.resetRanking("casino");
                sender.sendMessage(ChatColor.GREEN + "カジノランキングをリセットしました。");
                break;
            case "arena":
                rankingManager.resetRanking("arena");
                sender.sendMessage(ChatColor.GREEN + "アリーナランキングをリセットしました。");
                break;
            case "all":
                rankingManager.resetAllRankings();
                sender.sendMessage(ChatColor.GREEN + "全ランキングをリセットしました。");
                break;
            default:
                sender.sendMessage(ChatColor.RED + "不明なカテゴリ: " + target);
                sender.sendMessage(ChatColor.YELLOW + "使い方: /ranking reset <casino|arena|all>");
                break;
        }
    }

    /**
     * プレイヤー名を解決する。
     *
     * @param playerId プレイヤーの UUID
     * @return プレイヤー名。オフラインの場合は Bukkit のキャッシュを使用
     */
    private String resolvePlayerName(UUID playerId) {
        try {
            org.bukkit.OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(playerId);
            String name = offlinePlayer.getName();
            return name != null ? name : "???";
        } catch (Exception e) {
            return "???";
        }
    }

    /**
     * コマンドの使い方ヘルプを送信する。
     *
     * @param sender コマンド実行者
     */
    private void sendUsage(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "[Ranking] " + ChatColor.GRAY + "使い方:");
        sender.sendMessage(ChatColor.YELLOW + "  /ranking " + ChatColor.GRAY + "- 総合ランキング表示");
        sender.sendMessage(ChatColor.YELLOW + "  /ranking casino " + ChatColor.GRAY + "- カジノランキング表示");
        sender.sendMessage(ChatColor.YELLOW + "  /ranking arena " + ChatColor.GRAY + "- アリーナランキング表示");
        sender.sendMessage(ChatColor.YELLOW + "  /ranking reset <casino|arena|all> " + ChatColor.GRAY + "- リセット（OP権限）");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> result = new ArrayList<>();
            String input = args[0].toLowerCase();
            for (String s : Arrays.asList("total", "casino", "arena", "reset")) {
                if (s.startsWith(input)) {
                    result.add(s);
                }
            }
            return result;
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("reset")) {
            if (!sender.isOp()) return Collections.emptyList();
            List<String> result = new ArrayList<>();
            String input = args[1].toLowerCase();
            for (String s : Arrays.asList("casino", "arena", "all")) {
                if (s.startsWith(input)) {
                    result.add(s);
                }
            }
            return result;
        }

        return Collections.emptyList();
    }
}
