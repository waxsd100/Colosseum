package io.wax100.arenaCore.command.sub;

import io.wax100.arenaCore.ArenaCore;
import io.wax100.arenaCore.command.CommandHelper;
import io.wax100.arenaCore.command.SubCommand;
import io.wax100.arenaCore.manager.JackpotManager;
import io.wax100.arenaCore.util.ArenaMessages;
import io.wax100.chipLib.ChipManager;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * {@code /arena jackpot} — ジャックポット残高の確認と積立を行う。
 *
 * <p>引数なしで現在の残高を表示する。{@code add <金額>} で実行者自身の
 * 所持金から支払ってジャックポットに積み立てる（無料付与ではない）。
 */
public class JackpotSubCommand implements SubCommand {

    private final ArenaCore plugin;

    public JackpotSubCommand(ArenaCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        JackpotManager jackpot = plugin.getJackpotManager();
        if (jackpot == null) {
            sender.sendMessage(ArenaMessages.PREFIX + ChatColor.RED + "ジャックポット機能が利用できません。");
            return;
        }

        // 引数なし: 残高表示のみ
        if (args.length == 0) {
            sender.sendMessage(ArenaMessages.PREFIX + ChatColor.GOLD + "🎰 ジャックポット残高: " + ChatColor.YELLOW + ChipManager.formatAmount(jackpot.getBalance()) + " E");
            sender.sendMessage(ChatColor.GRAY + "積立: /arena jackpot add <金額>");
            return;
        }

        if (!args[0].equalsIgnoreCase("add")) {
            sender.sendMessage(ArenaMessages.PREFIX + ChatColor.RED + "使い方: " + getUsage());
            return;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(ArenaMessages.PREFIX + ChatColor.RED
                    + "このコマンドはプレイヤーのみ実行できます（所持金から支払うため）。");
            return;
        }

        if (args.length < 2) {
            sender.sendMessage(ArenaMessages.PREFIX + ChatColor.RED + "金額を指定してください: /arena jackpot add <金額>");
            return;
        }

        long amount;
        try {
            amount = Long.parseLong(args[1]);
        } catch (NumberFormatException e) {
            sender.sendMessage(ArenaMessages.PREFIX + ChatColor.RED + "金額は1以上の整数で指定してください。");
            return;
        }
        if (amount <= 0) {
            sender.sendMessage(ArenaMessages.PREFIX + ChatColor.RED + "金額は1以上の整数で指定してください。");
            return;
        }

        Economy economy = plugin.getEconomy();
        if (economy == null) {
            sender.sendMessage(ArenaMessages.PREFIX + ChatColor.RED + "経済プラグインが利用できません。");
            return;
        }

        if (!economy.has(player, amount)) {
            sender.sendMessage(ArenaMessages.PREFIX + ChatColor.RED
                    + "所持金が不足しています。（所持: " + ChipManager.formatAmount((long) economy.getBalance(player)) + " E）");
            return;
        }

        // 実際に Vault 残高から引き落とす（無料付与ではない）
        EconomyResponse response = economy.withdrawPlayer(player, amount);
        if (!response.transactionSuccess()) {
            sender.sendMessage(ArenaMessages.PREFIX + ChatColor.RED
                    + "支払いに失敗しました: " + response.errorMessage);
            return;
        }

        // ジャックポットへ積立（永続化は deposit 内部で行われる）
        jackpot.deposit(amount);

        plugin.getLogger().info("ジャックポット積立: " + player.getName() + " が " + ChipManager.formatAmount(amount) + " E を支払い（残高: " + ChipManager.formatAmount(jackpot.getBalance()) + " E）");

        Bukkit.broadcastMessage(ArenaMessages.PREFIX + ChatColor.GOLD
                + "🎰 " + ChatColor.YELLOW + player.getName() + ChatColor.GOLD
                + " がジャックポットに " + ChatColor.YELLOW
                + ChipManager.formatAmount(amount) + " E" + ChatColor.GOLD + " を積み立てました！");
        Bukkit.broadcastMessage(ArenaMessages.PREFIX + ChatColor.GOLD
                + "🎰 現在のジャックポット: " + ChatColor.YELLOW
                + ChipManager.formatAmount(jackpot.getBalance()) + " E");
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            return CommandHelper.filterStartsWith(List.of("add"), args[0]);
        }
        if (args.length == 2 && "add".equalsIgnoreCase(args[0])) {
            return List.of("1000", "10000", "100000");
        }
        return List.of();
    }

    @Override
    public String getUsage() {
        return "/arena jackpot [add <金額>]";
    }
}
