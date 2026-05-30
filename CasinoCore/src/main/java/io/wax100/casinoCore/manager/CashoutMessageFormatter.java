package io.wax100.casinoCore.manager;

import io.wax100.casinoCore.util.Messages;
import io.wax100.chipLib.Chip;
import io.wax100.chipLib.ChipManager;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.util.Map;

/**
 * 換金結果メッセージのフォーマットを行うユーティリティクラス。
 *
 * <p>換金時のメッセージ生成ロジックを集約し、{@link CasinoManager} から分離する。
 * インスタンス化不可のユーティリティクラス。
 *
 * @see CasinoManager
 */
public final class CashoutMessageFormatter {

    /**
     * インスタンス化禁止用のプライベートコンストラクタ
     */
    private CashoutMessageFormatter() {
    }

    /**
     * 換金結果メッセージを送信する。
     *
     * <p>
     * 購入額・換金額・損益・勝敗結果・売却内訳をフォーマットしてプレイヤーに送信する。
     *
     * @param player     対象プレイヤー
     * @param totalValue 換金合計額
     * @param purchased  セッション中の購入総額
     * @param netResult  損益（換金額 − 購入額）
     * @param breakdown  チップの売却内訳 (額面 → 枚数)
     */
    public static void sendCashoutMessage(Player player, long totalValue, long purchased,
                                          long netResult, Map<Chip, Integer> breakdown) {
        player.sendMessage(Messages.SEPARATOR);

        if (purchased > 0) {
            player.sendMessage(Messages.PREFIX + ChatColor.GRAY + "購入額: "
                    + ChatColor.WHITE + ChipManager.formatAmount(purchased) + " E");
        }
        if (totalValue > 0) {
            player.sendMessage(Messages.PREFIX + ChatColor.GRAY + "換金額: "
                    + ChatColor.WHITE + ChipManager.formatAmount(totalValue) + " E");
        }

        if (purchased > 0) {
            sendProfitLossMessage(player, netResult);
        } else if (totalValue > 0) {
            player.sendMessage(Messages.PREFIX + ChatColor.GREEN + "チップを換金しました。");
        }

        player.sendMessage(Messages.SEPARATOR);
        player.sendMessage(Messages.PREFIX + ChatColor.GRAY + "[ 売却内訳 ]");

        sendBreakdownLines(player, breakdown);

        player.sendMessage(Messages.SEPARATOR);
    }

    /**
     * 損益メッセージを送信する。
     *
     * @param player    対象プレイヤー
     * @param netResult 損益値
     */
    private static void sendProfitLossMessage(Player player, long netResult) {
        ChatColor color;
        String sign;
        if (netResult > 0) {
            sign = "+";
            color = ChatColor.GREEN;
        } else if (netResult < 0) {
            sign = "";
            color = ChatColor.RED;
        } else {
            sign = "±";
            color = ChatColor.YELLOW;
        }
        player.sendMessage(Messages.PREFIX + ChatColor.GRAY + "損 益: "
                + color + sign + ChipManager.formatAmount(netResult) + " E");
        player.sendMessage("");

        String resultMsg;
        if (netResult > 0) {
            resultMsg = ChatColor.GREEN + "今回の結果: "
                    + ChatColor.GOLD + ChatColor.BOLD + "勝ち "
                    + ChatColor.RESET + ChatColor.GREEN + "(+"
                    + ChipManager.formatAmount(netResult) + " E)";
        } else if (netResult < 0) {
            resultMsg = ChatColor.RED + "今回の結果: "
                    + ChatColor.DARK_RED + ChatColor.BOLD + "負け "
                    + ChatColor.RESET + ChatColor.RED + "("
                    + ChipManager.formatAmount(netResult) + " E)";
        } else {
            resultMsg = ChatColor.YELLOW + "今回の結果: "
                    + ChatColor.GRAY + "引き分け (±0 E)";
        }
        player.sendMessage(Messages.PREFIX + resultMsg);
    }

    /**
     * 売却内訳を2列ずつフォーマットして送信する。
     *
     * @param player    対象プレイヤー
     * @param breakdown チップの売却内訳
     */
    private static void sendBreakdownLines(Player player, Map<Chip, Integer> breakdown) {
        Chip[] chips = Chip.values();
        for (int i = 0; i < chips.length; i += 2) {
            StringBuilder line = new StringBuilder(Messages.PREFIX);
            line.append(formatBreakdownEntry(chips[i], breakdown.getOrDefault(chips[i], 0)));
            if (i + 1 < chips.length) {
                line.append("    ");
                line.append(formatBreakdownEntry(chips[i + 1], breakdown.getOrDefault(chips[i + 1], 0)));
            }
            player.sendMessage(line.toString());
        }
    }

    /**
     * 売却内訳の1行分をフォーマットする。
     *
     * @param chip  チップ種別
     * @param count 売却枚数
     * @return フォーマット済みの内訳文字列
     */
    private static String formatBreakdownEntry(Chip chip, int count) {
        return chip.getChatColor() + ChipManager.formatAmount(chip.getValue())
                + " E" + ChatColor.GRAY + "(" + chip.getChatColor() + chip.getColorName()
                + ChatColor.GRAY + "): " + ChatColor.WHITE + count + " 枚";
    }
}
