package io.wax100.casinoCore.command;

import io.wax100.casinoCore.CasinoCore;
import io.wax100.casinoCore.manager.ChipManager;
import io.wax100.casinoCore.manager.ChipManager.Chip;
import io.wax100.casinoCore.util.Messages;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * /chip コマンドハンドラ
 */
public class ChipCommand implements CommandExecutor, TabCompleter {

    private final CasinoCore plugin;

    public ChipCommand(CasinoCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Messages.PREFIX + ChatColor.RED + "このコマンドはプレイヤーのみ使用できます。");
            return true;
        }
        if (args.length == 0) {
            sendUsage(player);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "info":
                handleInfo(player);
                return true;
            case "balance":
                handleBalance(player);
                return true;
            case "cashout":
                handleCashout(player);
                return true;
            default:
                break;
        }

        if (!plugin.getCasinoManager().isCasinoActive()) {
            player.sendMessage(Messages.PREFIX + ChatColor.RED + "カジノモードがOFFのため、チップを購入できません。");
            return true;
        }

        try {
            long firstArg = Long.parseLong(args[0]);
            if (args.length >= 2) {
                handleBuyDenomination(player, firstArg, Integer.parseInt(args[1]));
            } else {
                handleBuyAutoSplit(player, firstArg);
            }
        } catch (NumberFormatException e) {
            sendUsage(player);
        }
        return true;
    }

    // ── 購入処理 ──

    /**
     * 額面指定モード: /chip [額面] [枚数]
     */
    private void handleBuyDenomination(Player player, long denomination, int count) {
        Chip chip = plugin.getChipManager().getChipByValue(denomination);
        if (chip == null) {
            player.sendMessage(Messages.PREFIX + ChatColor.RED
                    + "無効な額面です。/chip info で有効な額面を確認してください。");
            return;
        }
        if (count <= 0) {
            player.sendMessage(Messages.PREFIX + ChatColor.RED + "枚数は1以上を指定してください。");
            return;
        }

        Map<Chip, Integer> chips = new LinkedHashMap<>();
        chips.put(chip, count);
        long totalCost = denomination * count;
        if (executePurchase(player, chips, totalCost)) {
            return;
        }

        player.sendMessage(Messages.PREFIX + ChatColor.GREEN
                + chip.getChatColor() + ChatColor.BOLD
                + ChipManager.formatAmount(denomination) + " E チップ "
                + ChatColor.RESET + ChatColor.GREEN + "× " + count
                + "枚 を購入しました！（合計: "
                + ChatColor.YELLOW + ChipManager.formatAmount(totalCost) + " E"
                + ChatColor.GREEN + "）");
    }

    /**
     * 自動分割モード: /chip [金額]
     */
    private void handleBuyAutoSplit(Player player, long amount) {
        if (amount <= 0) {
            player.sendMessage(Messages.PREFIX + ChatColor.RED + "金額は1以上を指定してください。");
            return;
        }
        long maxBuy = plugin.getConfig().getLong("max-buy", 1000000);
        if (amount > maxBuy) {
            player.sendMessage(Messages.PREFIX + ChatColor.RED + "最大購入額は "
                    + ChatColor.YELLOW + ChipManager.formatAmount(maxBuy) + " E "
                    + ChatColor.RED + "です。");
            return;
        }

        Map<Chip, Integer> breakdown = plugin.getChipManager().breakdownAmount(amount);
        long actualTotal = calcTotal(breakdown);
        if (actualTotal == 0) {
            player.sendMessage(Messages.PREFIX + ChatColor.RED + "指定した金額ではチップに変換できません。");
            return;
        }
        if (executePurchase(player, breakdown, actualTotal)) {
            return;
        }

        player.sendMessage(Messages.PREFIX + ChatColor.GREEN
                + ChipManager.formatAmount(actualTotal) + " E 分のチップを購入しました！");
        player.sendMessage(Messages.PREFIX + ChatColor.GRAY + "[ 内訳 ]");
        for (Map.Entry<Chip, Integer> entry : breakdown.entrySet()) {
            Chip chip = entry.getKey();
            player.sendMessage(Messages.PREFIX + "  " + chip.getChatColor()
                    + ChipManager.formatAmount(chip.getValue())
                    + " E " + ChatColor.GRAY + "× "
                    + ChatColor.WHITE + entry.getValue() + " 枚");
        }
    }

    /**
     * 購入の共通処理（バリデーション → 引き落とし → チップ付与 → 記録）
     *
     * @return 成功した場合 true
     */
    private boolean executePurchase(Player player, Map<Chip, Integer> chips, long totalCost) {
        ChipManager cm = plugin.getChipManager();
        Economy economy = plugin.getEconomy();

        if (!economy.has(player, totalCost)) {
            player.sendMessage(Messages.PREFIX + ChatColor.RED + "所持金が足りません。（必要: "
                    + ChatColor.YELLOW + ChipManager.formatAmount(totalCost) + " E"
                    + ChatColor.RED + ", 所持: "
                    + ChatColor.YELLOW + ChipManager.formatAmount((long) economy.getBalance(player)) + " E"
                    + ChatColor.RED + "）");
            return true;
        }

        int slotsNeeded = cm.calculateSlotsNeeded(chips);
        if (cm.countEmptySlots(player) < slotsNeeded) {
            player.sendMessage(Messages.PREFIX + ChatColor.RED
                    + "インベントリに空きがありません。（必要スロット: " + slotsNeeded + "）");
            return true;
        }

        EconomyResponse resp = economy.withdrawPlayer(player, totalCost);
        if (!resp.transactionSuccess()) {
            player.sendMessage(Messages.PREFIX + ChatColor.RED + "購入に失敗しました: " + resp.errorMessage);
            return true;
        }

        cm.giveChips(player, chips);
        plugin.getCasinoManager().recordPurchase(player.getUniqueId(), totalCost);
        return false;
    }

    private long calcTotal(Map<Chip, Integer> chips) {
        long total = 0;
        for (Map.Entry<Chip, Integer> e : chips.entrySet()) {
            total += e.getKey().getValue() * e.getValue();
        }
        return total;
    }

    // ── 情報表示 ──

    private void handleInfo(Player player) {
        player.sendMessage("");
        player.sendMessage(ChatColor.GOLD.toString() + ChatColor.BOLD + "═══ チップ一覧 ═══");
        for (Chip chip : Chip.values()) {
            player.sendMessage("  " + chip.getChatColor() + "■ "
                    + chip.getColorName() + " " + ChatColor.GRAY + "= "
                    + ChatColor.YELLOW + ChipManager.formatAmount(chip.getValue()) + " E");
        }
        player.sendMessage(ChatColor.GOLD.toString() + ChatColor.BOLD + "══════════════");
        player.sendMessage("");
    }

    private void handleBalance(Player player) {
        ChipManager cm = plugin.getChipManager();
        Map<Chip, Integer> counts = cm.countChips(player);
        long total = cm.calculateTotalValue(player);
        boolean hasChips = counts.values().stream().anyMatch(c -> c > 0);

        player.sendMessage("");
        player.sendMessage(ChatColor.GOLD.toString() + ChatColor.BOLD + "═══ 手持ちチップ ═══");
        if (!hasChips) {
            player.sendMessage(ChatColor.GRAY + "  チップを持っていません。");
        } else {
            for (Map.Entry<Chip, Integer> entry : counts.entrySet()) {
                if (entry.getValue() > 0) {
                    Chip chip = entry.getKey();
                    long subtotal = chip.getValue() * entry.getValue();
                    player.sendMessage("  " + chip.getChatColor()
                            + ChipManager.formatAmount(chip.getValue()) + " E "
                            + ChatColor.GRAY + "× "
                            + ChatColor.WHITE + entry.getValue() + " 枚 "
                            + ChatColor.GRAY + "= "
                            + ChatColor.YELLOW + ChipManager.formatAmount(subtotal) + " E");
                }
            }
            player.sendMessage(ChatColor.GRAY + "  ─────────────");
            player.sendMessage(ChatColor.GRAY + "  合計: "
                    + ChatColor.YELLOW + ChatColor.BOLD
                    + ChipManager.formatAmount(total) + " E");
        }
        player.sendMessage(ChatColor.GOLD.toString() + ChatColor.BOLD + "══════════════");
        player.sendMessage("");
    }

    private void handleCashout(Player player) {
        if (!plugin.getCasinoManager().isCasinoActive()) {
            player.sendMessage(Messages.PREFIX + ChatColor.RED + "カジノモードがOFFのため、換金できません。");
            return;
        }
        long totalValue = plugin.getChipManager().calculateTotalValue(player);
        if (totalValue == 0) {
            player.sendMessage(Messages.PREFIX + ChatColor.YELLOW + "換金できるチップがありません。");
            return;
        }
        plugin.getCasinoManager().cashoutSinglePlayer(player);
    }

    private void sendUsage(Player player) {
        player.sendMessage(Messages.PREFIX + ChatColor.GRAY + "使い方:");
        player.sendMessage(ChatColor.YELLOW + "  /chip <額面> <枚数> " + ChatColor.GRAY + "- 指定額面のチップを購入");
        player.sendMessage(ChatColor.YELLOW + "  /chip <金額> " + ChatColor.GRAY + "- 金額分のチップを自動分割で購入");
        player.sendMessage(ChatColor.YELLOW + "  /chip info " + ChatColor.GRAY + "- チップ一覧を表示");
        player.sendMessage(ChatColor.YELLOW + "  /chip balance " + ChatColor.GRAY + "- 手持ちチップの確認");
        player.sendMessage(ChatColor.YELLOW + "  /chip cashout " + ChatColor.GRAY + "- 手持ちチップを換金する");
    }

    // ── タブ補完 ──

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> result = new ArrayList<>();
        if (args.length == 1) {
            List<String> opts = new ArrayList<>(Arrays.asList("info", "balance", "cashout"));
            String input = args[0].toLowerCase();
            for (String s : opts) {
                if (s.startsWith(input)) {
                    result.add(s);
                }
            }
        } else if (args.length == 2) {
            try {
                Long.parseLong(args[0]);
                result.addAll(Arrays.asList("1", "5", "10", "20", "50", "64"));
            } catch (NumberFormatException ignored) {
            }
        }
        return result;
    }
}
