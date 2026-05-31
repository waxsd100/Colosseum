package io.wax100.chipLib.command;

import io.wax100.chipLib.Chip;
import io.wax100.chipLib.ChipManager;
import io.wax100.chipLib.ChipPlugin;
import io.wax100.chipLib.ChipPurchaseListener;
import io.wax100.chipLib.util.ChipMessages;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
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
import java.util.UUID;

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
 * <p>{@code info} / {@code balance} / {@code cashout} はカジノモードに関係なく使用可能。
 *
 * @see ChipManager
 */
public class ChipCommand implements CommandExecutor, TabCompleter {

    /**
     * プラグインインスタンス
     */
    private final ChipPlugin plugin;

    /**
     * コンストラクタ。
     *
     * @param plugin ChipPlugin プラグインインスタンス
     */
    public ChipCommand(ChipPlugin plugin) {
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
            sender.sendMessage(ChipMessages.PLAYER_ONLY);
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

        // チップ購入はカジノモード or アリーナ賭け中のみ許可
        if (!plugin.isAllowed(player.getUniqueId())) {
            player.sendMessage(ChipMessages.PREFIX + ChatColor.RED
                    + "現在チップを購入できません。カジノモードまたは賭け受付中に使用してください。");
            return true;
        }

        try {
            long firstArg = Long.parseLong(args[0]);
            if (args.length >= 2) {
                int count;
                try {
                    count = Integer.parseInt(args[1]);
                } catch (NumberFormatException e) {
                    player.sendMessage(ChipMessages.INVALID_COUNT);
                    return true;
                }
                handleBuyDenomination(player, firstArg, count);
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
            player.sendMessage(ChipMessages.INVALID_DENOMINATION);
            return;
        }
        if (count <= 0) {
            player.sendMessage(ChipMessages.INVALID_COUNT);
            return;
        }

        long totalCost;
        try {
            totalCost = Math.multiplyExact(denomination, count);
        } catch (ArithmeticException e) {
            // long オーバーフロー検出
            player.sendMessage(ChipMessages.AMOUNT_OVERFLOW);
            return;
        }
        Map<Chip, Integer> chips = new LinkedHashMap<>();
        chips.put(chip, count);
        if (!executePurchase(player, chips, totalCost)) {
            return;
        }

        player.sendMessage(ChipMessages.PREFIX + ChatColor.GREEN
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
            player.sendMessage(ChipMessages.INVALID_AMOUNT);
            return;
        }
        long maxBuy = plugin.getConfig().getLong("max-buy", 1000000);
        if (amount > maxBuy) {
            player.sendMessage(ChipMessages.MAX_BUY_EXCEEDED);
            return;
        }

        Map<Chip, Integer> breakdown = plugin.getChipManager().breakdownAmount(amount);
        long actualTotal = ChipManager.calcTotal(breakdown);
        if (actualTotal == 0) {
            player.sendMessage(ChipMessages.CANNOT_CONVERT);
            return;
        }
        if (!executePurchase(player, breakdown, actualTotal)) {
            return;
        }

        player.sendMessage(ChipMessages.PREFIX + ChatColor.GREEN
                + ChipManager.formatAmount(actualTotal) + " E 分のチップを購入しました！");
        player.sendMessage(ChipMessages.PREFIX + ChatColor.GRAY + "[ 内訳 ]");
        for (Map.Entry<Chip, Integer> entry : breakdown.entrySet()) {
            Chip chip = entry.getKey();
            player.sendMessage(ChipMessages.PREFIX + "  " + chip.getChatColor()
                    + ChipManager.formatAmount(chip.getValue())
                    + " E " + ChatColor.GRAY + "× "
                    + ChatColor.WHITE + entry.getValue() + " 枚");
        }
    }

    /**
     * 購入の共通処理（バリデーション → 引き落とし → チップ付与 → アドベンチャーモード強制）。
     *
     * <p>所持金不足・インベントリ空き不足・引き落とし失敗の場合は
     * エラーメッセージを送信し、購入を中止する。
     * 購入成功時、プレイヤーがアドベンチャーモードでなければ強制的に変更する。
     *
     * @param player    購入プレイヤー
     * @param chips     購入するチップの内訳 (額面 → 枚数)
     * @param totalCost 合計購入額
     * @return 購入が成功した場合 {@code true}、エラーが発生し中止した場合 {@code false}
     */
    private boolean executePurchase(Player player, Map<Chip, Integer> chips, long totalCost) {
        ChipManager cm = plugin.getChipManager();
        Economy economy = plugin.getEconomy();

        if (!economy.has(player, totalCost)) {
            player.sendMessage(ChipMessages.INSUFFICIENT_FUNDS);
            return false;
        }

        int slotsNeeded = cm.calculateSlotsNeeded(chips);
        if (cm.countEmptySlots(player) < slotsNeeded) {
            player.sendMessage(ChipMessages.INVENTORY_FULL);
            return false;
        }

        EconomyResponse resp = economy.withdrawPlayer(player, totalCost);
        if (!resp.transactionSuccess()) {
            player.sendMessage(ChipMessages.PREFIX + ChatColor.RED + "購入に失敗しました: " + resp.errorMessage);
            return false;
        }

        cm.giveChips(player, chips);

        // 購入リスナーに通知（CasinoCore のランキング記録等）
        ChipPurchaseListener listener = plugin.getPurchaseListener();
        if (listener != null) {
            listener.onPurchase(player.getUniqueId(), totalCost);
        }

        // チップ購入成功後、アドベンチャーモードを強制（元のモードを保存）
        if (player.getGameMode() != GameMode.ADVENTURE) {
            plugin.getPreviousGameModes().putIfAbsent(player.getUniqueId(), player.getGameMode());
            player.setGameMode(GameMode.ADVENTURE);
        }

        return true;
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
            player.sendMessage(ChipMessages.NO_CHIPS);
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
     * <p>チップを持っていない場合はエラーメッセージを送信する。
     * ChipLib 単独での換金は、チップの回収と Economy への直接入金で実装する。
     *
     * @param player 対象プレイヤー
     */
    private void handleCashout(Player player) {
        ChipManager cm = plugin.getChipManager();
        long totalValue = cm.calculateTotalValue(player);
        if (totalValue == 0) {
            player.sendMessage(ChipMessages.NO_CHIPS_TO_CASHOUT);
            return;
        }

        cm.removeAllChips(player);
        Economy economy = plugin.getEconomy();
        economy.depositPlayer(player, totalValue);

        // 元のゲームモードに復元
        GameMode previous = plugin.getPreviousGameModes().remove(player.getUniqueId());
        if (previous != null && player.getGameMode() == GameMode.ADVENTURE) {
            player.setGameMode(previous);
        }

        player.sendMessage(ChipMessages.SEPARATOR);
        player.sendMessage(ChipMessages.PREFIX + ChatColor.GREEN
                + ChipManager.formatAmount(totalValue) + " E を換金しました。");
        player.sendMessage(ChipMessages.SEPARATOR);
    }

    /**
     * コマンドの使い方ヘルプを送信する。
     *
     * @param player 対象プレイヤー
     */
    private void sendUsage(Player player) {
        player.sendMessage(ChipMessages.PREFIX + ChatColor.GRAY + "使い方:");
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
     * 前方一致補完を提供する。チップ額面も候補に追加する。
     * 第2引数には枚数候補を提示する。
     */
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> result = new ArrayList<>();
        if (args.length == 1) {
            String input = args[0].toLowerCase();
            for (String s : Arrays.asList("info", "balance", "cashout")) {
                if (s.startsWith(input)) {
                    result.add(s);
                }
            }
            // 常にチップ額面を候補に追加（isPlayerInCasino チェック削除）
            for (Chip chip : Chip.values()) {
                String val = String.valueOf(chip.getValue());
                if (val.startsWith(input)) {
                    result.add(val);
                }
            }
        } else if (args.length == 2) {
            try {
                Long.parseLong(args[0]);
                String input = args[1].toLowerCase();
                for (String s : Arrays.asList("1", "5", "10", "20", "50", "64")) {
                    if (s.startsWith(input)) {
                        result.add(s);
                    }
                }
            } catch (NumberFormatException ignored) {
            }
        }
        return result;
    }
}
