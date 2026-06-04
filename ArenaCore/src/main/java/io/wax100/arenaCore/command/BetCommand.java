package io.wax100.arenaCore.command;

import io.wax100.arenaCore.ArenaCore;
import io.wax100.arenaCore.manager.ArenaManager;
import io.wax100.arenaCore.model.ArenaSession;
import io.wax100.arenaCore.model.ArenaState;
import io.wax100.arenaCore.model.Bet;
import io.wax100.arenaCore.util.ArenaMessages;
import io.wax100.chipLib.ChipManager;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * {@code /bet} コマンドハンドラ。
 *
 * <p>サブコマンド:
 * <ul>
 *   <li>{@code /bet <チーム> <金額>} — コマンドでベットする</li>
 *   <li>{@code /bet odds} — 現在のオッズ表示</li>
 *   <li>{@code /bet info} — 自分のベット情報表示</li>
 * </ul>
 */
public class BetCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUB_COMMANDS = List.of("odds", "info");

    private final ArenaCore plugin;

    public BetCommand(ArenaCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String [] args) {
        Player player = CommandHelper.requirePlayer(sender);
        if (player == null) return true;

        if (args.length == 0) {
            sendUsage(player);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "odds" -> handleOdds(player);
            case "info" -> handleInfo(player);
            default -> {
                // /bet <チーム> <金額> として処理を試みる
                if (args.length >= 2) {
                    handlePlaceBet(player, args[0], args[1]);
                } else {
                    sendUsage(player);
                }
            }
        }
        return true;
    }

    // ── /bet <チーム> <金額> ──

    private void handlePlaceBet(Player player, String teamArg, String amountArg) {
        ArenaManager manager = plugin.getArenaManager();
        ArenaSession session = CommandHelper.requireActiveSession(player, manager);
        if (session == null) return;

        if (session.getState() != ArenaState.BETTING && session.getState() != ArenaState.BLIND) {
            player.sendMessage(ArenaMessages.PREFIX + ChatColor.RED + "ベット受付中ではありません。");
            return;
        }

        // チーム名の解決（大文字小文字無視）
        String teamName = null;
        for (String name : session.getTeamNames()) {
            if (name.equalsIgnoreCase(teamArg)) {
                teamName = name;
                break;
            }
        }
        if (teamName == null) {
            player.sendMessage(ArenaMessages.PREFIX + ChatColor.RED
                    + "チーム「" + teamArg + "」が見つかりません。");
            return;
        }

        // 戦闘員チェック
        if (session.isFighter(player.getUniqueId())) {
            player.sendMessage(ArenaMessages.PREFIX + ChatColor.RED + "戦闘員はベットに参加できません。");
            return;
        }

        // 金額パース
        long amount;
        try {
            amount = Long.parseLong(amountArg);
        } catch (NumberFormatException e) {
            player.sendMessage(ArenaMessages.PREFIX + ChatColor.RED
                    + "金額は数値で指定してください。");
            return;
        }
        if (amount <= 0) {
            player.sendMessage(ArenaMessages.PREFIX + ChatColor.RED
                    + "1 E 以上の金額を指定してください。");
            return;
        }

        // 所持金チェック & 引き落とし
        Economy economy = plugin.getEconomy();
        double balance = economy.getBalance(player);
        if (balance < amount) {
            player.sendMessage(ArenaMessages.PREFIX + ChatColor.RED
                    + "所持金が足りません。 (所持金: "
                    + ChipManager.formatAmount((long) balance) + " E)");
            return;
        }

        economy.withdrawPlayer(player, amount);

        // ベット記録
        try {
            session.addOrUpdateBet(player.getUniqueId(), teamName, amount);
        } catch (IllegalStateException e) {
            // 失敗した場合は返金
            economy.depositPlayer(player, amount);
            player.sendMessage(ArenaMessages.PREFIX + ChatColor.RED + e.getMessage());
            return;
        }

        ChatColor teamColor = session.getTeamColor(teamName);
        Bet bet = session.getBet(player.getUniqueId(), teamName);
        long total = bet != null ? bet.amount() : amount;

        String msg = ChatColor.GREEN + "✔ " + teamColor + ChatColor.BOLD + teamName
                + ChatColor.RESET + ChatColor.GREEN + " に "
                + ChatColor.YELLOW + ChipManager.formatAmount(amount) + " E"
                + ChatColor.GREEN + " ベット"
                + ChatColor.GRAY + " (合計: "
                + ChipManager.formatAmount(total) + " E)";
        // actionbar 5秒間表示（20tick × 5回 = 100tick = 5秒）
        new org.bukkit.scheduler.BukkitRunnable() {
            int remaining = 5;
            @Override
            public void run() {
                if (remaining-- <= 0 || !player.isOnline()) {
                    cancel();
                    return;
                }
                player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(msg));
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    // ── /bet odds ──

    private void handleOdds(Player player) {
        ArenaManager manager = plugin.getArenaManager();
        ArenaSession session = CommandHelper.requireActiveSession(player, manager);
        if (session == null) return;

        if (session.getState() != ArenaState.BETTING
                && session.getState() != ArenaState.BLIND
                && session.getState() != ArenaState.ACTIVE) {
            player.sendMessage(ArenaMessages.PREFIX + ChatColor.RED + ArenaMessages.MSG_BETTING_NOT_STARTED);
            return;
        }
        plugin.getBettingManager().broadcastOdds(session);
    }

    // ── /bet info ──

    private void handleInfo(Player player) {
        ArenaManager manager = plugin.getArenaManager();
        ArenaSession session = CommandHelper.requireActiveSession(player, manager);
        if (session == null) return;

        Map<String, Bet> playerBets = session.getPlayerBets(player.getUniqueId());

        if (playerBets.isEmpty()) {
            player.sendMessage(ArenaMessages.PREFIX + ChatColor.GRAY + ArenaMessages.MSG_NO_BET);
            return;
        }

        player.sendMessage(ChatColor.DARK_RED.toString() + ChatColor.BOLD + "═══ あなたのベット情報 ═══");
        for (Bet bet : playerBets.values()) {
            ChatColor teamColor = session.getTeamColor(bet.teamName());
            double odds = plugin.getBettingManager().calculateOdds(session, bet.teamName());

            player.sendMessage(ArenaMessages.PREFIX + ChatColor.GRAY + "ベット先: "
                    + teamColor + ChatColor.BOLD + bet.teamName()
                    + ChatColor.RESET + ChatColor.GRAY + " / ベット額: "
                    + ChatColor.YELLOW + ChipManager.formatAmount(bet.amount()) + " E");

            if (odds > 0) {
                long expectedPayout = (long) (bet.amount() * odds);
                long expectedProfit = expectedPayout - bet.amount();
                player.sendMessage(ArenaMessages.PREFIX + ChatColor.GRAY + "  オッズ: "
                        + ChatColor.YELLOW + String.format("%.2f倍", odds)
                        + ChatColor.GRAY + " / 予想配当: "
                        + ChatColor.YELLOW + ChipManager.formatAmount(expectedPayout) + " E"
                        + ChatColor.GRAY + " / 損益: "
                        + (expectedProfit >= 0 ? ChatColor.GREEN + "+" : ChatColor.RED.toString())
                        + ChipManager.formatAmount(expectedProfit) + " E");
            }
        }
    }

    // ── ヘルプ ──

    private void sendUsage(Player player) {
        player.sendMessage(ArenaMessages.PREFIX + ChatColor.GRAY + "使い方:");
        player.sendMessage(ChatColor.YELLOW + "  /bet <チーム名> <金額>"
                + ChatColor.GRAY + " - 所持金から直接ベットする");
        player.sendMessage(ChatColor.YELLOW + "  /bet odds"
                + ChatColor.GRAY + " - 現在のオッズ表示");
        player.sendMessage(ChatColor.YELLOW + "  /bet info"
                + ChatColor.GRAY + " - 自分のベット情報表示");
        player.sendMessage("");
        player.sendMessage(ChatColor.GRAY + "※ ベットエリアにチップを置いてベットすることもできます。");
    }

    // ── Tab 補完 ──

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String [] args) {
        if (args.length == 1) {
            // odds, info + チーム名
            List<String> completions = new ArrayList<>(SUB_COMMANDS);
            ArenaManager manager = plugin.getArenaManager();
            if (manager.hasActiveSession()) {
                completions.addAll(manager.getActiveSession().getTeamNames());
            }
            return CommandHelper.filterStartsWith(completions, args[0]);
        }
        if (args.length == 2) {
            String sub = args[0].toLowerCase();
            if (!"odds".equals(sub) && !"info".equals(sub)) {
                // 金額のサジェスト
                return CommandHelper.filterStartsWith(
                        List.of("100", "500", "1000", "5000", "10000"), args[1]);
            }
        }
        return List.of();
    }
}
