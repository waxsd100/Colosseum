package io.wax100.casinoCore.command;

import io.wax100.casinoCore.CasinoCore;
import io.wax100.chipLib.Chip;
import io.wax100.chipLib.ChipManager;
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
 * {@code /chip} コマンドハンドラ。
 *
 * <p>チップの購入・確認・換金に関するサブコマンドを提供する。
 * <ul>
 *   <li>{@code /chip <額面> <枚数>} — 指定額面のチップを指定枚数購入</li>
 *   <li>{@code /chip <金額>} — 金額分のチップを自動分割で購入（貪欲法）</li>
 *   <li>{@code /chip info} — チップ額面一覧を表示</li>
 *   <li>{@code /chip balance} — 手持ちチップの内訳と合計を表示</li>
 *   <li>{@code /chip cashout} — 手持ちチップを換金して所持金に戻す</li>
 * </ul>
 *
 * <p>購入系サブコマンドはカジノモードが ON のときのみ使用可能。
 * {@code info} / {@code balance} / {@code cashout} はカジノモードに関係なく使用可能。
 *
 * @see ChipManager
 * @see CasinoManager
 */
public class ChipCommand implements CommandExecutor, TabCompleter {

    /**
     * プラグインインスタンス
     */
    private final CasinoCore plugin;

    /**
     * コンストラクタ。
     *
     * @param plugin CasinoCore プラグインインスタンス
     */
    public ChipCommand(CasinoCore plugin) {
        this.plugin = plugin;
    }

    /**
     * {@inheritDoc}
     *
     * <p>第1引数を解析し、サブコマンド（{@code info}, {@code balance}, {@code cashout}）
     * または数値（額面指定 / 金額指定）に応じて各ハンドラに振り分ける。
     * プレイヤー以外からの実行は拒否する。
     */
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

    /**
     * 額面指定モード: {@code /chip [額面] [枚数]}。
     *
     * <p>指定された額面のチップを指定枚数購入する。
     * 無効な額面や 0 以下の枚数の場合はエラーメッセージを送信する。
     *
     * @param player       購入プレイヤー
     * @param denomination 額面
     * @param count        枚数
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
     * 自動分割モード: {@code /chip [金額]}。
     *
     * <p>指定金額を貪欲法でチップに分割して購入する。
     * 最大購入額 ({@code max-buy}) を超える場合はエラーを返す。
     *
     * @param player 購入プレイヤー
     * @param amount 購入金額
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
     * 購入の共通処理（バリデーション → 引き落とし → チップ付与 → 記録）。
     *
     * <p>所持金不足・インベントリ空き不足・引き落とし失敗の場合は
     * エラーメッセージを送信し、購入を中止する。
     *
     * @param player    購入プレイヤー
     * @param chips     購入するチップの内訳 (額面 → 枚数)
     * @param totalCost 合計購入額
     * @return エラーが発生し購入を中止した場合 {@code true}、成功した場合 {@code false}
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

    /**
     * チップセットの合計金額を計算する。
     *
     * @param chips チップ内訳 (額面 → 枚数)
     * @return 合計金額
     */
    private long calcTotal(Map<Chip, Integer> chips) {
        long total = 0;
        for (Map.Entry<Chip, Integer> e : chips.entrySet()) {
            total += e.getKey().getValue() * e.getValue();
        }
        return total;
    }


    /**
     * チップ額面一覧を表示する。
     *
     * <p>全{@link Chip}値を色付きで一覧表示する。
     *
     * @param player 対象プレイヤー
     */
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

    /**
     * 手持ちチップの内訳と合計額を表示する。
     *
     * <p>チップを持っていない場合はその旨を表示する。
     *
     * @param player 対象プレイヤー
     */
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

    /**
     * 手持ちチップを換金する。
     *
     * <p>カジノモードが OFF の場合やチップを持っていない場合は
     * エラーメッセージを送信する。
     *
     * @param player 対象プレイヤー
     * @see CasinoManager#cashoutSinglePlayer(Player)
     */
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

    /**
     * コマンドの使い方ヘルプを送信する。
     *
     * @param player 対象プレイヤー
     */
    private void sendUsage(Player player) {
        player.sendMessage(Messages.PREFIX + ChatColor.GRAY + "使い方:");
        player.sendMessage(ChatColor.YELLOW + "  /chip <額面> <枚数> " + ChatColor.GRAY + "- 指定額面のチップを購入");
        player.sendMessage(ChatColor.YELLOW + "  /chip <金額> " + ChatColor.GRAY + "- 金額分のチップを自動分割で購入");
        player.sendMessage(ChatColor.YELLOW + "  /chip info " + ChatColor.GRAY + "- チップ一覧を表示");
        player.sendMessage(ChatColor.YELLOW + "  /chip balance " + ChatColor.GRAY + "- 手持ちチップの確認");
        player.sendMessage(ChatColor.YELLOW + "  /chip cashout " + ChatColor.GRAY + "- 手持ちチップを換金する");
    }


    /**
     * {@inheritDoc}
     *
     * <p>第1引数に対してサブコマンド（{@code info}, {@code balance}, {@code cashout}）の
     * 前方一致補完を提供する。第2引数には枚数候補を提示する。
     */
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
