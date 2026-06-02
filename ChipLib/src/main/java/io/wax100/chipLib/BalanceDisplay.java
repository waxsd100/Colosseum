package io.wax100.chipLib;

import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * アクションバーに所持金をリアルタイム表示するタスク。
 *
 * <p>全オンラインプレイヤーに対して定期的に現在の所持金をアクションバーへ表示する。
 * 残高変動を検知すると差額をアクションバー上に一定時間表示する。
 */
public class BalanceDisplay implements Runnable {

    private final ChipPlugin plugin;
    private BukkitTask task;

    /** 表示更新間隔（tick）— 10tick = 0.5秒 */
    private static final long INTERVAL_TICKS = 10L;

    /** 差額表示の持続時間（tick） */
    private static final int DELTA_DISPLAY_TICKS = 40; // 2秒間

    /** 前回の残高を記録（変動検知用） */
    private final Map<UUID, Long> previousBalances = new ConcurrentHashMap<>();

    /** アクションバーの差額表示データ */
    private final Map<UUID, DeltaDisplay> activeDelta = new ConcurrentHashMap<>();

    // ── カラーパレット ──
    private static final ChatColor C_SEPARATOR = ChatColor.DARK_GRAY;
    private static final ChatColor C_LABEL     = ChatColor.of("#AAAAAA");
    private static final ChatColor C_BALANCE   = ChatColor.of("#FFD700"); // ゴールド
    private static final ChatColor C_CHIPS     = ChatColor.of("#55FFAA"); // ミントグリーン
    private static final ChatColor C_UNIT      = ChatColor.of("#CCAA00"); // ダークゴールド
    private static final ChatColor C_PLUS      = ChatColor.of("#00FF88"); // 明るいグリーン
    private static final ChatColor C_MINUS     = ChatColor.of("#FF5555"); // 明るいレッド

    /**
     * @param plugin ChipPlugin インスタンス
     */
    public BalanceDisplay(ChipPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * 定期タスクを開始する。
     */
    public void start() {
        if (task != null) return;
        task = Bukkit.getScheduler().runTaskTimer(plugin, this, 0L, INTERVAL_TICKS);
    }

    /**
     * 定期タスクを停止する。
     */
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
        Economy economy = plugin.getEconomy();
        if (economy == null) return;

        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID uuid = player.getUniqueId();
            long balance = (long) economy.getBalance(player);
            long chipValue = plugin.getChipManager().calculateTotalValue(player);
            long totalWealth = balance + chipValue;

            // 差分検知
            Long prev = previousBalances.put(uuid, totalWealth);
            if (prev != null && prev != totalWealth) {
                long delta = totalWealth - prev;
                activeDelta.put(uuid, new DeltaDisplay(delta, DELTA_DISPLAY_TICKS));
            }

            // アクションバー送信
            String text = buildActionBar(balance, chipValue, uuid);
            player.spigot().sendMessage(
                    ChatMessageType.ACTION_BAR,
                    TextComponent.fromLegacyText(text));
        }

        // オフラインプレイヤーのキャッシュをクリーンアップ
        previousBalances.keySet().removeIf(uuid -> Bukkit.getPlayer(uuid) == null);
        activeDelta.keySet().removeIf(uuid -> Bukkit.getPlayer(uuid) == null);
    }

    /**
     * アクションバーのテキストを構築する。
     */
    private String buildActionBar(long balance, long chipValue, UUID uuid) {
        StringBuilder sb = new StringBuilder();

        // ┃ 💰 Balance  12,500 E ┃
        sb.append(C_SEPARATOR).append("┃ ");
        sb.append(C_LABEL).append("💰 ");
        sb.append(C_BALANCE).append(ChatColor.BOLD)
          .append(ChipManager.formatAmount(balance));
        sb.append(ChatColor.RESET).append(C_UNIT).append(" E");

        // 差額表示
        DeltaDisplay delta = activeDelta.get(uuid);
        if (delta != null) {
            if (delta.remaining > 0) {
                String sign = delta.amount > 0 ? "+" : "";
                ChatColor color = delta.amount > 0 ? C_PLUS : C_MINUS;
                sb.append(" ").append(color).append(ChatColor.BOLD)
                  .append(sign).append(ChipManager.formatAmount(delta.amount));
                delta.remaining -= INTERVAL_TICKS;
            } else {
                activeDelta.remove(uuid);
            }
        }

        // ┃ 🎰 Chips  3,000 E ┃（チップ所持時のみ）
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
     * プレイヤーのキャッシュをクリアする（ログアウト時用）。
     */
    public void clearPlayer(UUID playerId) {
        previousBalances.remove(playerId);
        activeDelta.remove(playerId);
    }

    // ══════════════════════════════════════
    //  外部通知 API（カジノ・アリーナ用）
    // ══════════════════════════════════════

    /** 音付き差額表示の持続時間（tick） */
    private static final int NOTIFY_DISPLAY_TICKS = 60; // 3秒間

    /**
     * カジノ・アリーナからの残高変動を音付きで通知する。
     *
     * <p>アクションバーに目立つ差額表示を出し、効果音を再生する。
     * 通常の自動検知より長く表示される。
     *
     * @param player 対象プレイヤー
     * @param amount 変動額（正: 収入, 負: 支出）
     * @param sound  再生する効果音
     */
    public void notifyDelta(Player player, long amount, org.bukkit.Sound sound) {
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
        org.bukkit.Sound sound = amount >= 0
                ? org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP
                : org.bukkit.Sound.BLOCK_NOTE_BLOCK_BASS;
        notifyDelta(player, amount, sound);
    }

    /** 差額表示の一時データ */
    private static class DeltaDisplay {
        final long amount;
        final boolean withSound;
        int remaining;

        DeltaDisplay(long amount, int remaining) {
            this(amount, remaining, false);
        }

        DeltaDisplay(long amount, int remaining, boolean withSound) {
            this.amount = amount;
            this.remaining = remaining;
            this.withSound = withSound;
        }
    }
}
