package io.wax100.chipLib;

import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.Collections;
import java.util.Map;
import java.util.Random;
import java.util.Set;
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
    private final Map<UUID, OverlayMessage> activeOverlay = new ConcurrentHashMap<>();

    /** アクションバー表示を非表示にしているプレイヤーのセット */
    private final Set<UUID> hiddenPlayers = Collections.newSetFromMap(new ConcurrentHashMap<>());

    /** 一時表示（peek）の残りtick。0以下で無効。 */
    private final Map<UUID, Integer> peekRemaining = new ConcurrentHashMap<>();

    // ── カラーパレット ──
    private static final ChatColor C_SEPARATOR = ChatColor.DARK_GRAY;
    private static final ChatColor C_LABEL     = ChatColor.of("#AAAAAA");
    private static final ChatColor C_BALANCE   = ChatColor.of("#FFD700");
    private static final ChatColor C_CHIPS     = ChatColor.of("#55FFAA");
    private static final ChatColor C_UNIT      = ChatColor.of("#CCAA00");
    private static final ChatColor C_PLUS      = ChatColor.of("#00FF88");
    private static final ChatColor C_MINUS     = ChatColor.of("#FF5555");
    private static final ChatColor C_SHUFFLE   = ChatColor.of("#888888");

    /** 表示トグル設定を永続化するための PDC キー */
    private final NamespacedKey displayKey;

    /**
     * @param chipPlugin ChipPlugin インスタンス
     */
    public BalanceDisplay(ChipPlugin chipPlugin) {
        this.chipPlugin = chipPlugin;
        this.plugin = chipPlugin;
        this.displayKey = new NamespacedKey(chipPlugin, "balance_display_hidden");
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
        activeOverlay.clear();
        peekRemaining.clear();
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

            // 非表示プレイヤーでも peek 中またはオーバーレイ中は表示する
            boolean hidden = hiddenPlayers.contains(uuid);
            boolean peeking = false;
            if (hidden) {
                Integer peek = peekRemaining.get(uuid);
                if (peek != null && peek > 0) {
                    peeking = true;
                    peekRemaining.put(uuid, peek - (int) INTERVAL_TICKS);
                } else {
                    peekRemaining.remove(uuid);
                }
            }

            if (!hidden || peeking) {
                String text = buildActionBar(balance, chipValue, uuid);
                player.spigot().sendMessage(
                        ChatMessageType.ACTION_BAR,
                        TextComponent.fromLegacyText(text));
            }
        }

        previousBalances.keySet().removeIf(uuid -> Bukkit.getPlayer(uuid) == null);
        activeDelta.keySet().removeIf(uuid -> Bukkit.getPlayer(uuid) == null);
        activeOverlay.keySet().removeIf(uuid -> Bukkit.getPlayer(uuid) == null);
        peekRemaining.keySet().removeIf(uuid -> Bukkit.getPlayer(uuid) == null);
    }

    private String buildActionBar(long balance, long chipValue, UUID uuid) {
        // オーバーレイメッセージがある場合は残高表示を一時的に置き換え
        OverlayMessage overlay = activeOverlay.get(uuid);
        if (overlay != null) {
            if (overlay.remaining > 0) {
                overlay.remaining -= INTERVAL_TICKS;
                return C_SEPARATOR + "┃ " + overlay.message + ChatColor.RESET + C_SEPARATOR + " ┃";
            } else {
                activeOverlay.remove(uuid);
            }
        }

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
            return color.toString() + ChatColor.BOLD + amountText;
        }

        // 経過tick（hold + アニメーション全体）
        int totalTicks = delta.holdTicks + NOTIFY_DISPLAY_TICKS;
        int elapsed = totalTicks - delta.remaining;
        int ticksPerStep = 2;

        int[] digitIndices = findDigitIndices(amountText);
        int digitCount = digitIndices.length;
        int totalSteps = WIND_UP_STEPS + digitCount;

        // holdTicks期間中 → 全桁シャッフル（ロック0）
        if (elapsed < delta.holdTicks) {
            return renderShuffle(amountText, digitIndices, 0, color);
        }

        // hold後の経過からステップを計算
        int animElapsed = elapsed - delta.holdTicks;
        int currentStep = animElapsed / ticksPerStep;

        if (currentStep >= totalSteps) {
            return color.toString() + ChatColor.BOLD + amountText;
        }

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

    /** テキスト中の数字の個数を返す。 */
    private int countDigits(String text) {
        int count = 0;
        for (char c : text.toCharArray()) {
            if (Character.isDigit(c)) count++;
        }
        return count;
    }



    /**
     * プレイヤーのログイン時に PDC から表示設定を読み込み、キャッシュに反映する。
     *
     * <p>PDC にキーが存在しない場合は初回ログインと判定し、デフォルト OFF（非表示）で
     * 初期化する。既にキーが存在する場合は保存された設定値に従う。
     *
     * @param player 対象プレイヤー
     * @return 初回ログイン（PDC にキーが存在しなかった）の場合 {@code true}
     */
    public boolean loadPlayer(Player player) {
        PersistentDataContainer pdc = player.getPersistentDataContainer();

        if (!pdc.has(displayKey, PersistentDataType.BYTE)) {
            // 初回ログイン: デフォルト OFF（非表示）で初期化
            pdc.set(displayKey, PersistentDataType.BYTE, (byte) 1);
            hiddenPlayers.add(player.getUniqueId());
            return true;
        }

        // 既存プレイヤー: 保存された設定に従う
        byte hidden = pdc.get(displayKey, PersistentDataType.BYTE);
        if (hidden == 1) {
            hiddenPlayers.add(player.getUniqueId());
        } else {
            hiddenPlayers.remove(player.getUniqueId());
        }
        return false;
    }

    /**
     * アクションバー表示の ON/OFF をトグルする。
     *
     * <p>トグル結果はプレイヤーの PDC に保存され、再ログイン時に復元される。
     *
     * @param playerId 対象プレイヤーの UUID
     * @return トグル後に表示が ON の場合 {@code true}、OFF の場合 {@code false}
     */
    public boolean toggleDisplay(UUID playerId) {
        Player player = Bukkit.getPlayer(playerId);
        if (hiddenPlayers.remove(playerId)) {
            // 非表示→表示に切り替え
            peekRemaining.remove(playerId);
            if (player != null) {
                player.getPersistentDataContainer().set(displayKey, PersistentDataType.BYTE, (byte) 0);
            }
            return true;
        } else {
            hiddenPlayers.add(playerId);
            if (player != null) {
                player.getPersistentDataContainer().set(displayKey, PersistentDataType.BYTE, (byte) 1);
            }
            return false;
        }
    }



    /**
     * アクションバーに一時メッセージを表示する。
     *
     * <p>残高表示の右側に指定メッセージを一定時間表示する。
     * BalanceDisplayの描画ループに乗るため、上書きされない。
     *
     * @param player  対象プレイヤー
     * @param message 表示するメッセージ（色コード込み）
     * @param ticks   表示時間（tick）
     */
    public void showOverlay(Player player, String message, int ticks) {
        activeOverlay.put(player.getUniqueId(), new OverlayMessage(message, ticks));
    }

    /**
     * アクションバーに一時メッセージを表示する（非表示プレイヤーでも一時的に表示する）。
     *
     * <p>非表示設定のプレイヤーに対しても peek 表示を有効にしてからオーバーレイを設定する。
     *
     * @param player  対象プレイヤー
     * @param message 表示するメッセージ（色コード込み）
     * @param ticks   表示時間（tick）
     * @param peek    非表示プレイヤーに対して一時的に表示を有効にする場合 {@code true}
     */
    public void showOverlay(Player player, String message, int ticks, boolean peek) {
        activeOverlay.put(player.getUniqueId(), new OverlayMessage(message, ticks));
        if (peek) {
            peekDisplay(player.getUniqueId(), ticks);
        }
    }

    /**
     * 非表示プレイヤーに対して指定tick数だけアクションバー表示を一時的に有効にする。
     *
     * <p>既に表示中のプレイヤーには何もしない。
     * 非表示プレイヤーのみ一時表示を有効にし、指定tick経過後に自動的に元の非表示状態に戻る。
     *
     * @param playerId 対象プレイヤーの UUID
     * @param ticks    一時表示の期間（tick）
     */
    public void peekDisplay(UUID playerId, int ticks) {
        if (!hiddenPlayers.contains(playerId)) return; // 表示中なら不要
        Integer current = peekRemaining.get(playerId);
        if (current == null || current < ticks) {
            peekRemaining.put(playerId, ticks);
        }
    }

    // ══════════════════════════════════════
    //  外部通知 API（カジノ・アリーナ用）
    // ══════════════════════════════════════

    /**
     * カジノ・アリーナからの残高変動を音付き＋シャッフルアニメーションで通知する。
     *
     * <p>holdTicks の間はシャッフルを維持し続け、その後ロックイン開始。
     *
     * @param player    対象プレイヤー
     * @param amount    変動額（正: 収入, 負: 支出）
     * @param doneSound 全確定後の効果音
     * @param holdTicks シャッフル維持期間（tick）。0ならすぐロックイン開始。
     */
    public void notifyDelta(Player player, long amount, Sound doneSound, int holdTicks) {
        UUID uuid = player.getUniqueId();
        int totalDisplay = holdTicks + NOTIFY_DISPLAY_TICKS;
        activeDelta.put(uuid, new DeltaDisplay(amount, totalDisplay, true, holdTicks));

        // 非表示プレイヤーでも配当アニメーションが見えるように一時表示を有効化
        peekDisplay(uuid, totalDisplay);

        // カチカチ音: holdTicks後からスケジュール
        String amountText = (amount > 0 ? "+" : "") + ChipManager.formatAmount(amount);
        int digitCount = countDigits(amountText);
        int totalSteps = WIND_UP_STEPS + digitCount;
        int ticksPerStep = 2;

        for (int i = 0; i < totalSteps; i++) {
            final int step = i;
            long tickDelay = holdTicks + (long) i * ticksPerStep;

            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!player.isOnline()) return;

                boolean isLocking = step >= WIND_UP_STEPS;
                int locked = Math.max(0, step - WIND_UP_STEPS + 1);
                float pitch = isLocking
                        ? 1.4f + (locked * 0.06f)
                        : 0.8f + RNG.nextFloat() * 0.4f;
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT,
                        0.15f, Math.min(pitch, 2.0f));
            }, tickDelay);
        }

        // 全確定後 → 完了音
        long finalDelay = holdTicks + (long) totalSteps * ticksPerStep + 1L;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            if (doneSound != null) {
                player.playSound(player.getLocation(), doneSound, 0.8f, 1.2f);
            }
        }, finalDelay);
    }

    /**
     * カジノ・アリーナからの残高変動を音付きで通知する（holdTicks=0）。
     */
    public void notifyDelta(Player player, long amount, Sound doneSound) {
        notifyDelta(player, amount, doneSound, 0);
    }

    /**
     * カジノ・アリーナからの残高変動を通知する（デフォルト効果音、holdTicks=0）。
     */
    public void notifyDelta(Player player, long amount) {
        Sound sound = amount >= 0
                ? Sound.ENTITY_EXPERIENCE_ORB_PICKUP
                : Sound.BLOCK_NOTE_BLOCK_BASS;
        notifyDelta(player, amount, sound, 0);
    }

    /** 差額表示の一時データ */
    private static class DeltaDisplay {
        final long amount;
        final boolean animated;
        final int holdTicks; // ロックイン開始前のシャッフル維持期間
        int remaining;

        DeltaDisplay(long amount, int remaining, boolean animated) {
            this(amount, remaining, animated, 0);
        }

        DeltaDisplay(long amount, int remaining, boolean animated, int holdTicks) {
            this.amount = amount;
            this.remaining = remaining;
            this.animated = animated;
            this.holdTicks = holdTicks;
        }
    }

    /** アクションバーに一時表示するオーバーレイメッセージ。 */
    private static class OverlayMessage {
        final String message;
        int remaining;

        OverlayMessage(String message, int remaining) {
            this.message = message;
            this.remaining = remaining;
        }
    }
}
