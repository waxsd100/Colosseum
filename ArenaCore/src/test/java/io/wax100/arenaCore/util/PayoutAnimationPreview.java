package io.wax100.arenaCore.util;

import java.util.Random;

/**
 * PayoutAnimation のフレーム出力をコンソールで確認するテスト。
 *
 * <p>Bukkit 不要。{@code main} メソッドを直接実行する。
 * ANSIカラーで実際の見た目に近い出力を行う。
 */
public class PayoutAnimationPreview {

    private static final Random RNG = new Random();
    private static final String GLYPHS = "0123456789#$%&@!?*>";
    private static final int WIND_UP_STEPS = 3;

    // ANSI カラーコード
    private static final String RESET   = "\u001B[0m";
    private static final String BOLD    = "\u001B[1m";
    private static final String RED     = "\u001B[31m";
    private static final String GREEN   = "\u001B[32m";
    private static final String YELLOW  = "\u001B[33m";
    private static final String GOLD    = "\u001B[38;5;214m";
    private static final String GRAY    = "\u001B[90m";
    private static final String WHITE   = "\u001B[97m";

    public static void main(String[] args) throws InterruptedException {
        System.out.println();
        System.out.println(BOLD + "=== PayoutAnimation プレビュー ===" + RESET);
        System.out.println();

        // ── ケース1: 勝利ベッター配当 ──
        System.out.println(GOLD + BOLD + "▼ 勝利ベッター (配当: 10,000 E / ベット: 3,000 E / 損益: +7,000 E)" + RESET);
        preview("PAYOUT", "+10,000 E", "(+7,000 E)", GOLD, YELLOW, GREEN);

        // ── ケース2: 大きい金額 ──
        System.out.println(GOLD + BOLD + "▼ 高額配当 (1,234,567 E)" + RESET);
        preview("PAYOUT", "+1,234,567 E", "(+934,567 E)", GOLD, YELLOW, GREEN);

        // ── ケース3: 闘技者 勝者還元 ──
        System.out.println(GREEN + BOLD + "▼ 勝者闘技者還元 (5,000 E)" + RESET);
        preview("🏆 勝者還元", "+5,000 E", null, GREEN, GREEN, null);

        // ── ケース4: 敗者没収 ──
        System.out.println(RED + BOLD + "▼ 敗者没収 (2,500 E)" + RESET);
        preview("CONFISCATED", "-2,500 E", null, RED, RED, null);

        // ── ケース5: 最低保証金 ──
        System.out.println(GREEN + BOLD + "▼ 最低保証金 (100 E)" + RESET);
        preview("GUARANTEE", "+100 E", null, GREEN, GREEN, null);

        System.out.println();
        System.out.println(BOLD + "=== プレビュー終了 ===" + RESET);
    }

    private static void preview(String title, String amountText, String suffix,
                                 String titleColor, String confirmedColor,
                                 String suffixColor) throws InterruptedException {
        int[] digitIndices = findDigitIndices(amountText);
        int totalSteps = WIND_UP_STEPS + digitIndices.length;

        System.out.println();
        System.out.printf("  Title:    %s%s%s%s%n", titleColor, BOLD, title, RESET);
        System.out.println("  " + GRAY + "─────────────────────────────────" + RESET);

        for (int step = 0; step < totalSteps; step++) {
            int locked = Math.max(0, step - WIND_UP_STEPS + 1);
            String frame = renderAnsi(amountText, digitIndices, locked, confirmedColor, GRAY);

            String label = locked > 0
                    ? String.format("  Step %2d:  %s    ← 確定: %d/%d桁", step, frame, locked, digitIndices.length)
                    : String.format("  Step %2d:  %s    ← シャッフル", step, frame);
            System.out.println(label + RESET);
            Thread.sleep(80); // コンソールでアニメ感を出す
        }

        // 最終確定
        StringBuilder finalFrame = new StringBuilder();
        finalFrame.append("  ").append(BOLD).append(confirmedColor).append(amountText).append(RESET);
        if (suffix != null && suffixColor != null) {
            finalFrame.append(" ").append(suffixColor).append(suffix).append(RESET);
        }
        System.out.println("  " + GRAY + "─────────────────────────────────" + RESET);
        System.out.println("  Final: " + finalFrame + "    ← ✔ 全確定 (ホールド 2.5秒)");
        System.out.println();
    }

    /**
     * ANSI カラー版の render。
     */
    private static String renderAnsi(String original, int[] digitIndices,
                                      int lockedDigits, String confirmedColor,
                                      String unconfirmedColor) {
        char[] chars = original.toCharArray();
        boolean[] isConfirmed = new boolean[chars.length];
        for (int d = 0; d < Math.min(lockedDigits, digitIndices.length); d++) {
            isConfirmed[digitIndices[d]] = true;
        }
        boolean[] isDigitPos = new boolean[chars.length];
        for (int idx : digitIndices) {
            isDigitPos[idx] = true;
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < chars.length; i++) {
            if (isDigitPos[i]) {
                if (isConfirmed[i]) {
                    sb.append(confirmedColor).append(BOLD).append(chars[i]).append(RESET);
                } else {
                    sb.append(unconfirmedColor);
                    sb.append(GLYPHS.charAt(RNG.nextInt(GLYPHS.length())));
                    sb.append(RESET);
                }
            } else {
                sb.append(GRAY).append(chars[i]).append(RESET);
            }
        }
        return sb.toString();
    }

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
