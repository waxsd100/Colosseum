package io.wax100.chipLib;

import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * アクションバーに所持金をリアルタイム表示するタスク。
 *
 * <p>全オンラインプレイヤーに対して定期的に現在の所持金をアクションバーへ表示する。
 * カジノ/アリーナからの通知時はシャッフル文字アニメーション付きで表示する。
 */
public class BalanceDisplay implements Runnable {

    private final Plugin plugin;
    private final ChipPlugin chipPlugin;
    private BukkitTask task;

    // ── 定数 ──
    private static final long INTERVAL_TICKS = 10L;
    private static final int DELTA_DISPLAY_TICKS = 40;
    private static final int NOTIFY_DISPLAY_TICKS = 60;

    /** シャッフル用ランダム文字セット */
    private static final String GLYPHS = "0123456789#$%&@!?*>";
    private static final Random RNG = new Random();

    /** シャッフルのみのステップ数（確定が始まる前の溜め） */
    private static final int WIND_UP_STEPS = 3;

    // ── キャッシュ ──
    private final Map<UUID, Long> previousBalances = new ConcurrentHashMap<>();
    private final Map<UUID, DeltaDisplay> activeDelta = new ConcurrentHashMap<>();

    // ── カラーパレット ──
    private static final ChatColor C_SEPARATOR = ChatColor.DARK_GRAY;
    private static final ChatColor C_LABEL     = ChatColor.of("#AAAAAA");
    private static final ChatColor C_BALANCE   = ChatColor.of("#FFD700");
    private static final ChatColor C_CHIPS     = ChatColor.of("#55FFAA");
    private static final ChatColor C_UNIT      = ChatColor.of("#CCAA00");
    private static final ChatColor C_PLUS      = ChatColor.of("#00FF88");
    private static final ChatColor C_MINUS     = ChatColor.of("#FF5555");
    private static final ChatColor C_SHUFFLE   = ChatColor.of("#888888");

    /**
     * @param chipPlugin ChipPlugin インスタンス
     */
    public BalanceDisplay(ChipPlugin chipPlugin) {
        this.chipPlugin = chipPlugin;
        this.plugin = chipPlugin;
    }

    public void start() {
        if (task != null) return;
        task = Bukkit.getScheduler().runTaskTimer(plugin, this, 0L, INTERVAL_TICKS);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        previousBalances.clear();
        activeDelta.clear();
    }

    @Override
    public void run() {
        Economy economy = chipPlugin.getEconomy();
        if (economy == null) return;

        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID uuid = player.getUniqueId();
            long balance = (long) economy.getBalance(player);
            long chipValue = chipPlugin.getChipManager().calculateTotalValue(player);
            long totalWealth = balance + chipValue;

            // 差分検知（通知APIで既にセットされていなければ自動検知）
            Long prev = previousBalances.put(uuid, totalWealth);
            if (prev != null && prev != totalWealth) {
                DeltaDisplay existing = activeDelta.get(uuid);
                if (existing == null || !existing.animated) {
                    activeDelta.put(uuid, new DeltaDisplay(totalWealth - prev, DELTA_DISPLAY_TICKS, false));
                }
            }

            // アクションバー送信
            String text = buildActionBar(balance, chipValue, uuid);
            player.spigot().sendMessage(
                    ChatMessageType.ACTION_BAR,
                    TextComponent.fromLegacyText(text));
        }

        previousBalances.keySet().removeIf(uuid -> Bukkit.getPlayer(uuid) == null);
        activeDelta.keySet().removeIf(uuid -> Bukkit.getPlayer(uuid) == null);
    }

    private String buildActionBar(long balance, long chipValue, UUID uuid) {
        StringBuilder sb = new StringBuilder();

        sb.append(C_SEPARATOR).append("┃ ");
        sb.append(C_LABEL).append("💰 ");
        sb.append(C_BALANCE).append(ChatColor.BOLD)
          .append(ChipManager.formatAmount(balance));
        sb.append(ChatColor.RESET).append(C_UNIT).append(" E");

        // 差額表示
        DeltaDisplay delta = activeDelta.get(uuid);
        if (delta != null) {
            if (delta.remaining > 0) {
                sb.append(" ").append(renderDelta(delta));
                delta.remaining -= INTERVAL_TICKS;
            } else {
                activeDelta.remove(uuid);
            }
        }

        if (chipValue > 0) {
            sb.append(ChatColor.RESET).append(C_SEPARATOR).append("  ┃  ");
            sb.append(C_LABEL).append("🎰 ");
            sb.append(C_CHIPS).append(ChipManager.formatAmount(chipValue));
            sb.append(C_UNIT).append(" E");
        }

        sb.append(ChatColor.RESET).append(C_SEPARATOR).append(" ┃");
        return sb.toString();
    }

    /**
     * 差額テキストをレンダリングする。
     * animated=true の場合、シャッフル→左から確定のアニメーション付き。
     */
    private String renderDelta(DeltaDisplay delta) {
        String sign = delta.amount > 0 ? "+" : "";
        String amountText = sign + ChipManager.formatAmount(delta.amount);
        ChatColor color = delta.amount > 0 ? C_PLUS : C_MINUS;

        if (!delta.animated) {
            // 通常表示（シャッフルなし）
            return color.toString() + ChatColor.BOLD + amountText;
        }

        // アニメーション: 残りtickから現在のステップを計算
        int totalTicks = NOTIFY_DISPLAY_TICKS;
        int elapsed = totalTicks - delta.remaining;
        int ticksPerStep = 2; // 0.1秒ごとに1ステップ

        int[] digitIndices = findDigitIndices(amountText);
        int digitCount = digitIndices.length;
        int totalSteps = WIND_UP_STEPS + digitCount;

        int currentStep = elapsed / ticksPerStep;

        if (currentStep >= totalSteps) {
            // 全確定後 → 通常表示
            return color.toString() + ChatColor.BOLD + amountText;
        }

        // シャッフル中
        int locked = Math.max(0, currentStep - WIND_UP_STEPS + 1);
        return renderShuffle(amountText, digitIndices, locked, color);
    }

    /**
     * シャッフルテキストをレンダリング。確定済み桁は色付き、未確定桁はランダム文字。
     */
    private String renderShuffle(String original, int[] digitIndices,
                                  int lockedDigits, ChatColor confirmedColor) {
        char[] chars = original.toCharArray();
        StringBuilder sb = new StringBuilder();

        boolean[] isConfirmed = new boolean[chars.length];
        for (int d = 0; d < Math.min(lockedDigits, digitIndices.length); d++) {
            isConfirmed[digitIndices[d]] = true;
        }

        boolean[] isDigitPos = new boolean[chars.length];
        for (int idx : digitIndices) {
            isDigitPos[idx] = true;
        }

        for (int i = 0; i < chars.length; i++) {
            if (isDigitPos[i]) {
                if (isConfirmed[i]) {
                    sb.append(confirmedColor).append(ChatColor.BOLD).append(chars[i]);
                } else {
                    sb.append(C_SHUFFLE);
                    sb.append(GLYPHS.charAt(RNG.nextInt(GLYPHS.length())));
                }
            } else {
                // 符号・カンマ等は確定色で固定表示
                sb.append(confirmedColor).append(ChatColor.BOLD).append(chars[i]);
            }
        }

        return sb.toString();
    }

    /** テキスト中の数字位置を左から順に返す。 */
    private int[] findDigitIndices(String text) {
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

    public void clearPlayer(UUID playerId) {
        previousBalances.remove(playerId);
        activeDelta.remove(playerId);
    }

    // ══════════════════════════════════════
    //  外部通知 API（カジノ・アリーナ用）
    // ══════════════════════════════════════

    /**
     * カジノ・アリーナからの残高変動を音付き＋シャッフルアニメーションで通知する。
     *
     * @param player 対象プレイヤー
     * @param amount 変動額（正: 収入, 負: 支出）
     * @param sound  再生する効果音
     */
    public void notifyDelta(Player player, long amount, Sound sound) {
        UUID uuid = player.getUniqueId();
        activeDelta.put(uuid, new DeltaDisplay(amount, NOTIFY_DISPLAY_TICKS, true));

        if (sound != null) {
            player.playSound(player.getLocation(), sound, 1.0f, 1.0f);
        }
    }

    /**
     * カジノ・アリーナからの残高変動を通知する（デフォルト効果音）。
     *
     * @param player 対象プレイヤー
     * @param amount 変動額（正: 収入, 負: 支出）
     */
    public void notifyDelta(Player player, long amount) {
        Sound sound = amount >= 0
                ? Sound.ENTITY_EXPERIENCE_ORB_PICKUP
                : Sound.BLOCK_NOTE_BLOCK_BASS;
        notifyDelta(player, amount, sound);
    }

    /** 差額表示の一時データ */
    private static class DeltaDisplay {
        final long amount;
        final boolean animated;
        int remaining;

        DeltaDisplay(long amount, int remaining, boolean animated) {
            this.amount = amount;
            this.remaining = remaining;
            this.animated = animated;
        }
    }
}
