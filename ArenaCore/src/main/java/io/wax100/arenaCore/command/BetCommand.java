package io.wax100.arenaCore.command;

import io.wax100.arenaCore.ArenaCore;
import io.wax100.arenaCore.manager.ArenaManager;
import io.wax100.arenaCore.model.ArenaSession;
import io.wax100.arenaCore.model.ArenaState;
import io.wax100.arenaCore.model.Bet;
import io.wax100.arenaCore.util.ArenaMessages;
import io.wax100.chipLib.ChipManager;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * {@code /bet} コマンドハンドラ（観客用: オッズ確認・自分の賭け情報表示）。
 *
 * <p>賭け自体はカーペット設置で行うため、このコマンドは情報表示のみ。
 */
public class BetCommand implements CommandExecutor, TabCompleter {

    private final ArenaCore plugin;

    public BetCommand(ArenaCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ArenaMessages.PREFIX + ChatColor.RED + "プレイヤーのみ使用できます。");
            return true;
        }

        if (args.length == 0) { sendUsage(player); return true; }

        switch (args[0].toLowerCase()) {
            case "odds": handleOdds(player); break;
            case "info": handleInfo(player); break;
            default:     sendUsage(player); break;
        }
        return true;
    }

    private void handleOdds(Player player) {
        ArenaManager manager = plugin.getArenaManager();
        if (!manager.hasActiveSession()) {
            player.sendMessage(ArenaMessages.PREFIX + ChatColor.RED + "闘技場セッションがありません。");
            return;
        }
        ArenaSession session = manager.getActiveSession();
        if (session.getState() != ArenaState.BETTING && session.getState() != ArenaState.ACTIVE) {
            player.sendMessage(ArenaMessages.PREFIX + ChatColor.RED + "賭けが開始されていません。");
            return;
        }
        plugin.getBettingManager().broadcastOdds(session);
    }

    private void handleInfo(Player player) {
        ArenaManager manager = plugin.getArenaManager();
        if (!manager.hasActiveSession()) {
            player.sendMessage(ArenaMessages.PREFIX + ChatColor.RED + "闘技場セッションがありません。");
            return;
        }

        ArenaSession session = manager.getActiveSession();
        Bet bet = session.getBet(player.getUniqueId());

        if (bet == null) {
            player.sendMessage(ArenaMessages.PREFIX + ChatColor.GRAY + "まだ賭けていません。");
            return;
        }

        int teamIndex = session.getTeamNames().indexOf(bet.getTeamName());
        ChatColor teamColor = ArenaMessages.getTeamColor(teamIndex);
        double houseEdge = plugin.getConfig().getDouble("house-edge", 0.1);
        double odds = plugin.getPayoutStrategy().calculateOdds(session, bet.getTeamName(), houseEdge);

        player.sendMessage(ChatColor.DARK_RED.toString() + ChatColor.BOLD + "═══ あなたの賭け情報 ═══");
        player.sendMessage(ArenaMessages.PREFIX + ChatColor.GRAY + "賭け先: "
                + teamColor + ChatColor.BOLD + bet.getTeamName());
        player.sendMessage(ArenaMessages.PREFIX + ChatColor.GRAY + "賭け金: "
                + ChatColor.YELLOW + ChipManager.formatAmount(bet.getAmount()) + " E");

        if (odds > 0) {
            long expectedPayout = (long) (bet.getAmount() * odds);
            long expectedProfit = expectedPayout - bet.getAmount();
            player.sendMessage(ArenaMessages.PREFIX + ChatColor.GRAY + "現在のオッズ: "
                    + ChatColor.YELLOW + String.format("%.2f倍", odds));
            player.sendMessage(ArenaMessages.PREFIX + ChatColor.GRAY + "予想配当: "
                    + ChatColor.YELLOW + ChipManager.formatAmount(expectedPayout) + " E");
            player.sendMessage(ArenaMessages.PREFIX + ChatColor.GRAY + "予想損益: "
                    + (expectedProfit >= 0 ? ChatColor.GREEN + "+" : ChatColor.RED.toString())
                    + ChipManager.formatAmount(expectedProfit) + " E");
        }
    }

    private void sendUsage(Player player) {
        player.sendMessage(ArenaMessages.PREFIX + ChatColor.GRAY + "使い方:");
        player.sendMessage(ChatColor.YELLOW + "  /bet odds" + ChatColor.GRAY + " - 現在のオッズ表示");
        player.sendMessage(ChatColor.YELLOW + "  /bet info" + ChatColor.GRAY + " - 自分の賭け情報表示");
        player.sendMessage("");
        player.sendMessage(ChatColor.GRAY + "※ 賭けはチームの賭けエリアにカーペット（チップ）を置いて行います。");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> result = new ArrayList<>();
        if (args.length == 1) {
            String input = args[0].toLowerCase();
            for (String sub : Arrays.asList("odds", "info")) {
                if (sub.startsWith(input)) result.add(sub);
            }
        }
        return result;
    }
}
