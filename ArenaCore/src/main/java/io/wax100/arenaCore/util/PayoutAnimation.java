package io.wax100.arenaCore.util;

import io.wax100.chipLib.ChipManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * 支払い時のタイトルアニメーション表示ユーティリティ。
 *
 * <p>金額を段階的にカウントアップしながらサブタイトルに表示し、
 * 最終的な金額で確定表示する。中央から展開するテキストアニメーション付き。
 */
public final class PayoutAnimation {

    /** カウントアップのステップ数 */
    private static final int COUNT_UP_STEPS = 8;
    /** 各ステップの間隔（tick） */
    private static final int TICK_INTERVAL = 2;
    /** 最終表示の持続時間（tick） */
    private static final int FINAL_HOLD_TICKS = 40;
    /** タイトルのフェードイン（tick） */
    private static final int FADE_IN = 5;
    /** タイトルのフェードアウト（tick） */
    private static final int FADE_OUT = 10;

    private PayoutAnimation() {}

    /**
     * 勝利ベッターへの配当アニメーションを表示する。
     *
     * @param plugin      プラグインインスタンス
     * @param player      対象プレイヤー
     * @param payout      配当額
     * @param originalBet 元のベット額
     * @param delay       表示開始までの遅延（tick）
     */
    public static void playWinnerPayout(Plugin plugin, Player player,
                                         long payout, long originalBet, long delay) {
        long profit = payout - originalBet;
        String profitText = (profit >= 0 ? ChatColor.GREEN + "+" : ChatColor.RED.toString())
                + ChipManager.formatAmount(profit) + " E";

        playCountUpAnimation(plugin, player,
                ChatColor.GOLD.toString() + ChatColor.BOLD + "🎉 配当受取",
                payout,
                ChatColor.GRAY + "損益: " + profitText,
                Sound.ENTITY_PLAYER_LEVELUP,
                delay);
    }

    /**
     * 闘技者への還元アニメーションを表示する。
     *
     * @param plugin プラグインインスタンス
     * @param player 対象プレイヤー
     * @param amount 還元額
     * @param label  ラベル（例: "勝者還元", "敗者還元"）
     * @param emoji  絵文字（例: "🏆", "💸"）
     * @param delay  表示開始までの遅延（tick）
     */
    public static void playFighterShare(Plugin plugin, Player player,
                                         long amount, String label, String emoji, long delay) {
        ChatColor color = label.contains("敗者") ? ChatColor.YELLOW : ChatColor.GREEN;
        playCountUpAnimation(plugin, player,
                color.toString() + ChatColor.BOLD + emoji + " " + label,
                amount,
                null,
                Sound.ENTITY_EXPERIENCE_ORB_PICKUP,
                delay);
    }

    /**
     * 最低保証金のアニメーションを表示する。
     *
     * @param plugin プラグインインスタンス
     * @param player 対象プレイヤー
     * @param amount 保証金額
     * @param delay  表示開始までの遅延（tick）
     */
    public static void playGuarantee(Plugin plugin, Player player, long amount, long delay) {
        playCountUpAnimation(plugin, player,
                ChatColor.GREEN.toString() + ChatColor.BOLD + "💰 最低保証金",
                amount,
                null,
                Sound.ENTITY_EXPERIENCE_ORB_PICKUP,
                delay);
    }

    /**
     * 敗者への没収アニメーションを表示する。
     *
     * @param plugin プラグインインスタンス
     * @param player 対象プレイヤー
     * @param amount 没収額
     * @param delay  表示開始までの遅延（tick）
     */
    public static void playLoserConfiscation(Plugin plugin, Player player, long amount, long delay) {
        // 敗者はカウントアップなし、即表示
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            String subtitle = ChatColor.RED + "-" + ChipManager.formatAmount(amount) + " E";
            player.sendTitle(
                    ChatColor.RED.toString() + ChatColor.BOLD + "没収",
                    subtitle,
                    FADE_IN, FINAL_HOLD_TICKS, FADE_OUT);
            player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_LAND, 0.5f, 0.8f);
        }, delay);
    }

    /**
     * 金額カウントアップアニメーションを実行する。
     *
     * <p>ランダムな数字 → 段階的に近づく → 確定額の順にサブタイトルを更新。
     *
     * @param plugin    プラグインインスタンス
     * @param player    対象プレイヤー
     * @param titleText タイトル行のテキスト
     * @param amount    最終確定金額
     * @param extraLine 確定後の追加表示（null可）
     * @param sound     確定時のサウンド
     * @param delay     初回表示までの遅延（tick）
     */
    private static void playCountUpAnimation(Plugin plugin, Player player,
                                              String titleText, long amount,
                                              String extraLine, Sound sound, long delay) {
        // カウントアップ各ステップ
        for (int i = 0; i < COUNT_UP_STEPS; i++) {
            final int step = i;
            long stepDelay = delay + ((long) i * TICK_INTERVAL);

            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!player.isOnline()) return;

                // 段階的に正解に近づく金額を生成
                long displayAmount = calculateStepAmount(amount, step, COUNT_UP_STEPS);
                String amountText = buildStepText(displayAmount, step, COUNT_UP_STEPS);

                player.sendTitle(
                        titleText,
                        amountText,
                        0, TICK_INTERVAL + 5, 0);

                // カチカチ音
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.3f,
                        1.0f + (step * 0.1f));
            }, stepDelay);
        }

        // 最終確定表示
        long finalDelay = delay + ((long) COUNT_UP_STEPS * TICK_INTERVAL);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;

            String finalAmount = ChatColor.YELLOW.toString() + ChatColor.BOLD
                    + "+" + ChipManager.formatAmount(amount) + " E";

            String subtitle = extraLine != null
                    ? finalAmount + "  " + extraLine
                    : finalAmount;

            player.sendTitle(
                    titleText,
                    subtitle,
                    FADE_IN, FINAL_HOLD_TICKS, FADE_OUT);

            // 確定サウンド
            player.playSound(player.getLocation(), sound, 0.8f, 1.2f);
        }, finalDelay);
    }

    /**
     * カウントアップ中の表示金額を計算する。
     *
     * <p>最初はランダムに大きく振れ、ステップが進むにつれ正解に収束する。
     */
    private static long calculateStepAmount(long target, int step, int maxSteps) {
        if (step >= maxSteps - 1) return target;

        double progress = (double) step / (maxSteps - 1);
        // イージング: 最初は大きく振れ、後半で収束
        double eased = progress * progress;

        // 振れ幅（残りの不確実性）
        double uncertainty = 1.0 - eased;
        double randomFactor = 0.5 + Math.random() * 1.0; // 0.5〜1.5倍
        long deviation = (long) (target * uncertainty * randomFactor * 0.5);

        long result = target + (Math.random() > 0.5 ? deviation : -deviation);
        return Math.max(0, result);
    }

    /**
     * ステップに応じた表示テキストを構築する。
     *
     * <p>確定前は灰色・スクランブル風、確定に近づくと黄色に変化。
     */
    private static String buildStepText(long displayAmount, int step, int maxSteps) {
        double progress = (double) step / (maxSteps - 1);
        String formatted = ChipManager.formatAmount(displayAmount);

        if (progress < 0.3) {
            // 初期: 暗い色、揺れている印象
            return ChatColor.DARK_GRAY.toString() + "+" + formatted + " E";
        } else if (progress < 0.6) {
            // 中間: 灰色
            return ChatColor.GRAY.toString() + "+" + formatted + " E";
        } else if (progress < 0.9) {
            // 後半: 白に近づく
            return ChatColor.WHITE.toString() + "+" + formatted + " E";
        } else {
            // ほぼ確定: 黄色
            return ChatColor.YELLOW.toString() + "+" + formatted + " E";
        }
    }
}
