package io.wax100.arenaCore.util;

import io.wax100.chipLib.ChipManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.Random;

/**
 * 支払いアニメーション。
 *
 * <p>サブタイトルに金額を表示し、各桁がランダム文字で高速シャッフルした後、
 * 左から順に1文字ずつ確定していく演出を行う。
 *
 * <pre>
 * Step 0:  +$#,%!& E     ← 全桁シャッフル
 * Step 1:  +$#,%!& E
 * Step 2:  +1#,%!& E     ← 左端確定
 * Step 3:  +10,%!& E
 * Step 4:  +10,#!& E
 * Step 5:  +10,0!& E
 * Step 6:  +10,00& E
 * Step 7:  +10,000 E     ← 全桁確定 → ホールド
 * </pre>
 */
public final class PayoutAnimation {

    private static final Random RNG = new Random();

    /** シャッフル用のランダム文字セット */
    private static final String GLYPHS = "0123456789#$%&@!?*>";

    /** 全桁シャッフルのみのステップ数（確定が始まる前の溜め） */
    private static final int WIND_UP_STEPS = 3;
    /** 各ステップの間隔（tick）— 1tick = 50ms */
    private static final int TICK_PER_STEP = 1;
    /** 最終確定表示の持続時間（tick） */
    private static final int HOLD_TICKS = 50;
    /** フェードアウト（tick） */
    private static final int FADE_OUT = 12;

    private PayoutAnimation() {}

    // ── 公開API ──────────────────────────────────────

    /**
     * 勝利ベッター — 配当受取アニメーション。
     */
    public static void playWinnerPayout(Plugin plugin, Player player,
                                         long payout, long originalBet, long delay) {
        long profit = payout - originalBet;
        String profitTag = (profit >= 0 ? ChatColor.GREEN + "(+" : ChatColor.RED + "(")
                + ChipManager.formatAmount(profit) + " E)";

        play(plugin, player,
                ChatColor.GOLD.toString() + ChatColor.BOLD + "PAYOUT",
                "+" + ChipManager.formatAmount(payout) + " E",
                profitTag,
                ChatColor.YELLOW, ChatColor.WHITE,
                Sound.ENTITY_PLAYER_LEVELUP, delay);
    }

    /**
     * 闘技者還元アニメーション。
     */
    public static void playFighterShare(Plugin plugin, Player player,
                                         long amount, String label, String emoji, long delay) {
        boolean isWinner = !label.contains("敗者");
        ChatColor accent = isWinner ? ChatColor.GREEN : ChatColor.YELLOW;
        play(plugin, player,
                accent.toString() + ChatColor.BOLD + emoji + " " + label,
                "+" + ChipManager.formatAmount(amount) + " E",
                null,
                accent, ChatColor.WHITE,
                Sound.ENTITY_EXPERIENCE_ORB_PICKUP, delay);
    }

    /**
     * 最低保証金アニメーション。
     */
    public static void playGuarantee(Plugin plugin, Player player, long amount, long delay) {
        play(plugin, player,
                ChatColor.GREEN.toString() + ChatColor.BOLD + "GUARANTEE",
                "+" + ChipManager.formatAmount(amount) + " E",
                null,
                ChatColor.GREEN, ChatColor.WHITE,
                Sound.ENTITY_EXPERIENCE_ORB_PICKUP, delay);
    }

    /**
     * 敗者没収アニメーション。
     */
    public static void playLoserConfiscation(Plugin plugin, Player player, long amount, long delay) {
        play(plugin, player,
                ChatColor.RED.toString() + ChatColor.BOLD + "CONFISCATED",
                "-" + ChipManager.formatAmount(amount) + " E",
                null,
                ChatColor.RED, ChatColor.GRAY,
                Sound.BLOCK_ANVIL_LAND, delay);
    }

    // ── コアアニメーション ──────────────────────────────

    /**
     * Hitman風シャッフル → 左から確定アニメーションを実行する。
     *
     * @param plugin      プラグイン
     * @param player      対象プレイヤー
     * @param title       タイトル行（上段）
     * @param amountText  金額テキスト（例: "+10,000 E"）
     * @param suffix      確定後に右側に追加するテキスト（null可）
     * @param confirmed   確定済み文字の色
     * @param unconfirmed 未確定シャッフル文字の色
     * @param doneSound   全確定時のサウンド
     * @param delay       開始遅延（tick）
     */
    private static void play(Plugin plugin, Player player,
                              String title, String amountText, String suffix,
                              ChatColor confirmed, ChatColor unconfirmed,
                              Sound doneSound, long delay) {

        // シャッフル対象の桁位置を特定（数字のみ。カンマ・符号・スペースは固定）
        int[] digitIndices = findDigitIndices(amountText);
        int digitCount = digitIndices.length;

        // 総ステップ = ウィンドアップ + 桁数
        int totalSteps = WIND_UP_STEPS + digitCount;

        // ── シャッフル → 順次確定 ──
        for (int i = 0; i < totalSteps; i++) {
            final int step = i;
            long tickDelay = delay + ((long) i * TICK_PER_STEP);

            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!player.isOnline()) return;

                // 左から何桁確定したか
                int locked = Math.max(0, step - WIND_UP_STEPS + 1);
                String sub = render(amountText, digitIndices, locked, confirmed, unconfirmed);

                player.sendTitle(title, sub, 0, TICK_PER_STEP + 4, 0);

                // ティック音: 確定時は少し高い音
                boolean isLocking = step >= WIND_UP_STEPS;
                float pitch = isLocking ? 1.4f + (locked * 0.06f) : 0.8f + RNG.nextFloat() * 0.4f;
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT,
                        0.15f, Math.min(pitch, 2.0f));
            }, tickDelay);
        }

        // ── 全確定 → ホールド表示 ──
        long finalDelay = delay + ((long) totalSteps * TICK_PER_STEP) + 1L;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;

            StringBuilder finalSub = new StringBuilder();
            finalSub.append(confirmed).append(ChatColor.BOLD).append(amountText);
            if (suffix != null) {
                finalSub.append(" ").append(ChatColor.RESET).append(suffix);
            }

            player.sendTitle(title, finalSub.toString(), 3, HOLD_TICKS, FADE_OUT);
            player.playSound(player.getLocation(), doneSound, 0.8f, 1.2f);
        }, finalDelay);
    }

    /**
     * 指定ステップにおけるサブタイトルテキストを構築する。
     *
     * @param original      元テキスト
     * @param digitIndices  シャッフル対象の文字位置配列
     * @param lockedDigits  左から確定済みの桁数
     * @param confirmedCol  確定文字の色
     * @param unconfirmedCol 未確定文字の色
     * @return フォーマット済みテキスト
     */
    private static String render(String original, int[] digitIndices,
                                  int lockedDigits, ChatColor confirmedCol,
                                  ChatColor unconfirmedCol) {
        char[] chars = original.toCharArray();
        StringBuilder sb = new StringBuilder();

        // 確定済みの桁のインデックスをセットに入れる
        boolean[] isConfirmed = new boolean[chars.length];
        for (int d = 0; d < Math.min(lockedDigits, digitIndices.length); d++) {
            isConfirmed[digitIndices[d]] = true;
        }

        // 全桁がシャッフル対象かを判定する配列
        boolean[] isDigitPos = new boolean[chars.length];
        for (int idx : digitIndices) {
            isDigitPos[idx] = true;
        }

        for (int i = 0; i < chars.length; i++) {
            if (isDigitPos[i]) {
                if (isConfirmed[i]) {
                    // 確定済み → 明るい色で実際の文字
                    sb.append(confirmedCol).append(chars[i]);
                } else {
                    // 未確定 → 暗い色でランダムグリフ
                    sb.append(unconfirmedCol);
                    sb.append(GLYPHS.charAt(RNG.nextInt(GLYPHS.length())));
                }
            } else {
                // 固定文字（カンマ、スペース、符号、E）
                sb.append(ChatColor.DARK_GRAY).append(chars[i]);
            }
        }

        return sb.toString();
    }

    /**
     * テキスト中の数字（0-9）の位置インデックスを左から順に返す。
     */
    private static int[] findDigitIndices(String text) {
        int count = 0;
        for (char c : text.toCharArray()) {
            if (Character.isDigit(c)) count++;
        }
        int[] result = new int[count];
        int idx = 0;
        for (int i = 0; i < text.length(); i++) {
            if (Character.isDigit(text.charAt(i))) {
                result[idx++] = i;
            }
        }
        return result;
    }
}
