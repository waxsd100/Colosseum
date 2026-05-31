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

import java.util.Arrays;
import java.util.List;

/**
 * {@code /bet} コマンドハンドラ（観客用: オッズ確認・自分の賭け情報表示）。
 *
 * <p>賭け自体はカーペット設置で行うため、このコマンドは情報表示のみ。
 */
public class BetCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUB_COMMANDS = Arrays.asList("odds", "info");

    private final ArenaCore plugin;

    public BetCommand(ArenaCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Player player = CommandHelper.requirePlayer(sender);
        if (player == null) return true;

        if (args.length == 0) {
            sendUsage(player);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "odds" -> handleOdds(player);
            case "info" -> handleInfo(player);
            default -> sendUsage(player);
        }
        return true;
    }

    private void handleOdds(Player player) {
        ArenaManager manager = plugin.getArenaManager();
        ArenaSession session = CommandHelper.requireActiveSession(player, manager);
        if (session == null) return;

        if (session.getState() != ArenaState.BETTING && session.getState() != ArenaState.ACTIVE) {
            player.sendMessage(ArenaMessages.PREFIX + ChatColor.RED + ArenaMessages.MSG_BETTING_NOT_STARTED);
            return;
        }
        plugin.getBettingManager().broadcastOdds(session);
    }

    private void handleInfo(Player player) {
        ArenaManager manager = plugin.getArenaManager();
        ArenaSession session = CommandHelper.requireActiveSession(player, manager);
        if (session == null) return;

        Bet bet = session.getBet(player.getUniqueId());

        if (bet == null) {
            player.sendMessage(ArenaMessages.PREFIX + ChatColor.GRAY + ArenaMessages.MSG_NO_BET);
            return;
        }

        ChatColor teamColor = session.getTeamColor(bet.teamName());
        double houseEdge = plugin.getConfig().getDouble("house-edge", 0.1);
        double odds = plugin.getPayoutStrategy().calculateOdds(session, bet.teamName(), houseEdge);

        player.sendMessage(ChatColor.DARK_RED.toString() + ChatColor.BOLD + "═══ あなたの賭け情報 ═══");
        player.sendMessage(ArenaMessages.PREFIX + ChatColor.GRAY + "賭け先: "
                + teamColor + ChatColor.BOLD + bet.teamName());
        player.sendMessage(ArenaMessages.PREFIX + ChatColor.GRAY + "賭け金: "
                + ChatColor.YELLOW + ChipManager.formatAmount(bet.amount()) + " E");

        if (odds > 0) {
            long expectedPayout = (long) (bet.amount() * odds);
            long expectedProfit = expectedPayout - bet.amount();
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
        if (args.length == 1) {
            return CommandHelper.filterStartsWith(SUB_COMMANDS, args[0]);
        }
        return List.of();
    }
}
