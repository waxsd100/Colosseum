package io.wax100.arenaCore.manager;

import io.wax100.arenaCore.ArenaCore;
import io.wax100.arenaCore.model.ArenaSession;
import io.wax100.arenaCore.util.ArenaMessages;
import io.wax100.chipLib.PayoutAnimation;
import io.wax100.chipLib.ChipManager;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.hover.content.Text;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * ダブルアップシステム管理。
 *
 * <p>勝利ベッターが配当を「DOUBLE UP（続行）」して次の試合に
 * 自動ベットするか、「CASH OUT（確定）」してチップを受け取るかを選択できる。
 *
 * <p>続行するたびに連勝ストリークが上がり、ボーナス倍率が増加する。
 * 次の試合で負けると保留額は全没収される。
 */
public class DoubleUpManager {

    private final ArenaCore plugin;

    /** プレイヤーごとのダブルアップ状態 */
    private final Map<UUID, DoubleUpState> activeStreaks = new HashMap<>();

    /** 選択待ちタイムアウトタスク */
    private final Map<UUID, BukkitTask> pendingTimers = new HashMap<>();

    /** 選択待ち中の配当額（まだ確定していない） */
    private final Map<UUID, PendingChoice> pendingChoices = new HashMap<>();

    /** タイムアウト秒数 */
    private static final int TIMEOUT_SECONDS = 15;

    /** 連勝ボーナス倍率テーブル */
    private static final double[] STREAK_MULTIPLIERS = {
            1.0,   // 1連勝（初回、ボーナスなし）
            1.2,   // 2連勝
            1.5,   // 3連勝
            2.0,   // 4連勝
            3.0    // 5連勝以上
    };

    public DoubleUpManager(ArenaCore plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    // ── データクラス ──

    /**
     * ダブルアップ状態。
     *
     * @param heldAmount  保留中の配当額
     * @param streakCount 連勝数
     * @param lastTeam    前回ベットしたチーム名
     */
    public record DoubleUpState(long heldAmount, int streakCount, String lastTeam) {
        /** 連勝ボーナス倍率を返す。 */
        public double getMultiplier() {
            int index = Math.min(streakCount - 1, STREAK_MULTIPLIERS.length - 1);
            return STREAK_MULTIPLIERS[Math.max(0, index)];
        }

        /** ボーナス込みの実効額を返す。 */
        public long getEffectiveAmount() {
            return (long) Math.floor(heldAmount * getMultiplier());
        }
    }

    private record PendingChoice(long payoutAmount, long originalBet, String teamName) {}

    // ── 公開API ──

    /**
     * ダブルアップが有効かどうか（config設定）。
     */
    public boolean isEnabled() {
        return plugin.getConfig().getBoolean("double-up.enabled", true);
    }

    /**
     * 勝利ベッターにダブルアップ選択を提示する。
     *
     * @return true: 選択待ちに入った（配当配布を保留）
     */
    public boolean offerChoice(UUID playerId, long payoutAmount, long originalBet, String teamName) {
        if (!isEnabled()) return false;
        if (payoutAmount <= 0) return false;

        Player player = Bukkit.getPlayer(playerId);
        if (player == null || !player.isOnline()) return false;

        long minAmount = plugin.getConfig().getLong("double-up.min-amount", 100);
        if (payoutAmount < minAmount) return false;

        DoubleUpState existing = activeStreaks.get(playerId);
        int currentStreak = existing != null ? existing.streakCount() : 0;

        pendingChoices.put(playerId, new PendingChoice(payoutAmount, originalBet, teamName));
        sendChoiceUI(player, payoutAmount, originalBet, currentStreak + 1);

        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            handleCashOut(playerId);
        }, TIMEOUT_SECONDS * 20L);
        pendingTimers.put(playerId, task);
        return true;
    }

    /**
     * 「DOUBLE UP」を選択。
     */
    public void handleDoubleUp(UUID playerId) {
        PendingChoice choice = pendingChoices.remove(playerId);
        cancelTimer(playerId);
        if (choice == null) return;

        Player player = Bukkit.getPlayer(playerId);

        DoubleUpState existing = activeStreaks.get(playerId);
        int newStreak = existing != null ? existing.streakCount() + 1 : 1;
        long newHeld = choice.payoutAmount();

        if (existing != null) {
            newHeld = existing.getEffectiveAmount() + choice.payoutAmount();
        }

        DoubleUpState state = new DoubleUpState(newHeld, newStreak, choice.teamName());
        activeStreaks.put(playerId, state);

        if (player != null && player.isOnline()) {
            player.sendMessage("");
            player.sendMessage(ArenaMessages.PREFIX + ChatColor.GOLD + ChatColor.BOLD
                    + "▲ DOUBLE UP!");
            player.sendMessage(ArenaMessages.PREFIX + ChatColor.DARK_GRAY + "┃ "
                    + ChatColor.GRAY + "Held  " + ChatColor.YELLOW
                    + ChipManager.formatAmount(state.heldAmount()) + " E");
            player.sendMessage(ArenaMessages.PREFIX + ChatColor.DARK_GRAY + "┃ "
                    + ChatColor.GRAY + "Streak  " + ChatColor.AQUA + "×" + newStreak
                    + ChatColor.GRAY + "  Bonus  " + ChatColor.GREEN
                    + String.format("×%.1f", state.getMultiplier()));
            player.sendMessage(ArenaMessages.PREFIX + ChatColor.DARK_GRAY + "┗ "
                    + ChatColor.GRAY + "Next win → "
                    + ChatColor.GREEN + ChipManager.formatAmount(state.getEffectiveAmount()) + " E");
            player.sendMessage("");

            player.sendTitle(
                    ChatColor.GOLD.toString() + ChatColor.BOLD + "▲ DOUBLE UP",
                    ChatColor.AQUA + "×" + newStreak + " STREAK"
                            + ChatColor.DARK_GRAY + "  ┃  "
                            + ChatColor.YELLOW + ChipManager.formatAmount(state.heldAmount()) + " E",
                    5, 40, 10);
            player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.4f, 1.5f);
        }
    }

    /**
     * 「CASH OUT」を選択。
     */
    public void handleCashOut(UUID playerId) {
        PendingChoice choice = pendingChoices.remove(playerId);
        cancelTimer(playerId);
        if (choice == null) return;

        Player player = Bukkit.getPlayer(playerId);

        DoubleUpState existing = activeStreaks.remove(playerId);
        long totalPayout = choice.payoutAmount();
        if (existing != null) {
            totalPayout += existing.getEffectiveAmount();
        }

        plugin.getBettingManager().payoutToPlayer(playerId, totalPayout);

        if (player != null && player.isOnline()) {
            int streak = existing != null ? existing.streakCount() + 1 : 1;

            player.sendMessage("");
            player.sendMessage(ArenaMessages.PREFIX + ChatColor.GREEN + ChatColor.BOLD
                    + "▼ CASH OUT");
            if (streak > 1) {
                player.sendMessage(ArenaMessages.PREFIX + ChatColor.AQUA
                        + "×" + streak + " STREAK!  "
                        + ChatColor.GREEN + "Bonus ×"
                        + String.format("%.1f", existing.getMultiplier()));
            }
            player.sendMessage(ArenaMessages.PREFIX + ChatColor.GRAY + "Total  "
                    + ChatColor.YELLOW + ChatColor.BOLD
                    + ChipManager.formatAmount(totalPayout) + " E");
            player.sendMessage("");

            PayoutAnimation.playWinnerPayout(plugin, player, totalPayout, choice.originalBet(), 5L);
        }
    }

    /**
     * 敗北時 — ダブルアップ保留額を全没収する。
     */
    public void confiscateOnLoss(UUID playerId) {
        DoubleUpState state = activeStreaks.remove(playerId);
        cancelTimer(playerId);
        pendingChoices.remove(playerId);
        if (state == null) return;

        Player player = Bukkit.getPlayer(playerId);
        long lostAmount = state.getEffectiveAmount();

        JackpotManager jackpot = plugin.getJackpotManager();
        if (jackpot != null) {
            jackpot.deposit(lostAmount / 2);
        }

        if (player != null && player.isOnline()) {
            player.sendMessage("");
            player.sendMessage(ArenaMessages.PREFIX + ChatColor.DARK_RED + ChatColor.BOLD
                    + "✖ DOUBLE UP FAILED");
            player.sendMessage(ArenaMessages.PREFIX + ChatColor.RED
                    + "×" + state.streakCount() + " streak broken  —  "
                    + ChatColor.YELLOW + ChipManager.formatAmount(lostAmount) + " E"
                    + ChatColor.RED + " lost");
            player.sendMessage("");

            player.sendTitle(
                    ChatColor.DARK_RED.toString() + ChatColor.BOLD + "✖ DOUBLE UP FAILED",
                    ChatColor.RED + "-" + ChipManager.formatAmount(lostAmount) + " E"
                            + ChatColor.DARK_GRAY + "  ┃  "
                            + ChatColor.GRAY + "×" + state.streakCount() + " streak",
                    5, 60, 15);
            player.playSound(player.getLocation(), Sound.ENTITY_WITHER_DEATH, 0.3f, 0.5f);
        }
    }

    /**
     * BETTING開始時に自動ベットを処理する。
     */
    public void processAutoBets(ArenaSession session) {
        if (activeStreaks.isEmpty()) return;

        for (Map.Entry<UUID, DoubleUpState> entry : new HashMap<>(activeStreaks).entrySet()) {
            UUID playerId = entry.getKey();
            DoubleUpState state = entry.getValue();

            String targetTeam = state.lastTeam();
            if (!session.hasTeam(targetTeam)) {
                targetTeam = session.getTeamNames().isEmpty() ? null : session.getTeamNames().get(0);
            }
            if (targetTeam == null) {
                handleCashOut(playerId);
                continue;
            }

            long betAmount = state.getEffectiveAmount();
            session.addOrUpdateBet(playerId, targetTeam, betAmount);

            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.isOnline()) {
                org.bukkit.ChatColor teamColor = session.getTeamColor(targetTeam);
                player.sendMessage("");
                player.sendMessage(ArenaMessages.PREFIX + ChatColor.GOLD
                        + "▲ DOUBLE UP — Auto Bet:");
                player.sendMessage(ArenaMessages.PREFIX + ChatColor.DARK_GRAY + "  ┃ "
                        + teamColor + ChatColor.BOLD + targetTeam
                        + org.bukkit.ChatColor.RESET + ChatColor.GRAY + "  "
                        + ChatColor.YELLOW + ChipManager.formatAmount(betAmount) + " E"
                        + ChatColor.DARK_GRAY + "  ┃  "
                        + ChatColor.AQUA + "×" + state.streakCount() + " streak");
                player.sendMessage("");
            }
        }
    }

    /**
     * ダブルアップ保留中のプレイヤーかどうか。
     */
    public boolean hasActiveStreak(UUID playerId) {
        return activeStreaks.containsKey(playerId);
    }



    /**
     * 選択待ち中かどうか。
     */
    public boolean isPendingChoice(UUID playerId) {
        return pendingChoices.containsKey(playerId);
    }

    /**
     * 全タイマーをクリア（ストリークは維持）。
     */
    public void clearTimers() {
        for (BukkitTask task : pendingTimers.values()) {
            task.cancel();
        }
        pendingTimers.clear();
        pendingChoices.clear();
    }

    /**
     * プラグイン無効化時に全保留を強制確定。
     */
    public void shutdown() {
        for (UUID playerId : new HashMap<>(pendingChoices).keySet()) {
            handleCashOut(playerId);
        }
        for (Map.Entry<UUID, DoubleUpState> entry : new HashMap<>(activeStreaks).entrySet()) {
            UUID playerId = entry.getKey();
            DoubleUpState state = entry.getValue();
            plugin.getBettingManager().payoutToPlayer(playerId, state.getEffectiveAmount());
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.isOnline()) {
                player.sendMessage(ArenaMessages.PREFIX + ChatColor.YELLOW
                        + "ダブルアップ保留額 " + ChipManager.formatAmount(state.getEffectiveAmount())
                        + " E を強制確定しました。");
            }
        }
        clearTimers();
        activeStreaks.clear();
    }

    // ── 内部 ──

    private void cancelTimer(UUID playerId) {
        BukkitTask task = pendingTimers.remove(playerId);
        if (task != null) task.cancel();
    }

    /**
     * 選択UIを送信する。
     */
    private void sendChoiceUI(Player player, long payoutAmount, long originalBet, int nextStreak) {
        long profit = payoutAmount - originalBet;
        double nextMult = STREAK_MULTIPLIERS[
                Math.min(nextStreak - 1, STREAK_MULTIPLIERS.length - 1)];

        player.sendMessage("");
        player.sendMessage("  " + ChatColor.DARK_GRAY + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        player.sendMessage("");

        // 配当情報
        player.sendMessage("  " + ChatColor.WHITE + ChatColor.BOLD + "   "
                + ChipManager.formatAmount(payoutAmount) + " E"
                + ChatColor.RESET + ChatColor.DARK_GRAY + "   won");

        if (nextStreak > 1) {
            player.sendMessage("  " + ChatColor.AQUA + "   🔥 ×" + nextStreak
                    + " streak" + ChatColor.DARK_GRAY + "  —  "
                    + ChatColor.GREEN + "bonus ×" + String.format("%.1f", nextMult));
        }

        player.sendMessage("");

        // ── DOUBLE UP ボタン ──
        TextComponent doubleBtn = new TextComponent("      ");
        TextComponent doubleInner = new TextComponent("▲ DOUBLE UP");
        doubleInner.setColor(ChatColor.GOLD);
        doubleInner.setBold(true);
        doubleInner.setClickEvent(new ClickEvent(
                ClickEvent.Action.RUN_COMMAND, "/doubleup continue"));
        doubleInner.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                new Text(ChatColor.GOLD + "▲ DOUBLE UP\n\n"
                        + ChatColor.WHITE + ChipManager.formatAmount(payoutAmount) + " E"
                        + ChatColor.GRAY + " を次の試合にベット\n\n"
                        + ChatColor.RED + "⚠ 負けると全額没収")));

        TextComponent doubleHint = new TextComponent("  — risk it all");
        doubleHint.setColor(ChatColor.DARK_GRAY);
        doubleHint.setItalic(true);

        doubleBtn.addExtra(doubleInner);
        doubleBtn.addExtra(doubleHint);
        player.spigot().sendMessage(doubleBtn);

        player.sendMessage("");

        // ── CASH OUT ボタン ──
        TextComponent cashBtn = new TextComponent("      ");
        TextComponent cashInner = new TextComponent("▼ CASH OUT");
        cashInner.setColor(ChatColor.GREEN);
        cashInner.setBold(true);
        cashInner.setClickEvent(new ClickEvent(
                ClickEvent.Action.RUN_COMMAND, "/doubleup stop"));
        cashInner.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                new Text(ChatColor.GREEN + "▼ CASH OUT\n\n"
                        + ChatColor.WHITE + ChipManager.formatAmount(payoutAmount) + " E"
                        + ChatColor.GRAY + " をチップで受け取る")));

        TextComponent cashHint = new TextComponent("  — take the money");
        cashHint.setColor(ChatColor.DARK_GRAY);
        cashHint.setItalic(true);

        cashBtn.addExtra(cashInner);
        cashBtn.addExtra(cashHint);
        player.spigot().sendMessage(cashBtn);

        player.sendMessage("");
        player.sendMessage("  " + ChatColor.DARK_GRAY + "  " + TIMEOUT_SECONDS
                + "s auto cash out");
        player.sendMessage("  " + ChatColor.DARK_GRAY + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        player.sendMessage("");

        // タイトル
        player.sendTitle(
                ChatColor.GOLD.toString() + ChatColor.BOLD + "DOUBLE UP?",
                ChatColor.YELLOW + ChipManager.formatAmount(payoutAmount) + " E"
                        + ChatColor.DARK_GRAY + "  ┃  "
                        + ChatColor.GRAY + TIMEOUT_SECONDS + "s",
                5, 60, 5);
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 0.8f, 1.0f);
    }
}
