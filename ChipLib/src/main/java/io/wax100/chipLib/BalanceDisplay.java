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
 * 残高変動を検知すると差額のシャッフルアニメーションをタイトルに再生する。
 */
public class BalanceDisplay implements Runnable {

    private final ChipPlugin plugin;
    private BukkitTask task;

    /** 表示更新間隔（tick）— 10tick = 0.5秒 */
    private static final long INTERVAL_TICKS = 10L;

    /** 前回の残高を記録（変動検知用） */
    private final Map<UUID, Long> previousBalances = new ConcurrentHashMap<>();

    /** アニメーション中の差額表示テキスト（一時的にアクションバーに表示） */
    private final Map<UUID, DeltaDisplay> activeDelta = new ConcurrentHashMap<>();

    /** アニメーション表示の残りtick */
    private static final int DELTA_DISPLAY_TICKS = 40; // 2秒間

    /** タイトルアニメーションを再生する変動額の最小閾値 */
    private static final long DELTA_ANIMATION_THRESHOLD = 100L;

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

                // タイトルアニメーション（大きい変動のみ）
                if (Math.abs(delta) >= DELTA_ANIMATION_THRESHOLD) {
                    if (delta > 0) {
                        PayoutAnimation.playIncome(plugin, player, delta, 0L);
                    } else {
                        PayoutAnimation.playExpense(plugin, player, Math.abs(delta), 0L);
                    }
                }
            }

            // アクションバー構築
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

        sb.append(ChatColor.DARK_GRAY).append("┃ ");
        sb.append(ChatColor.GRAY).append("Balance ");
        sb.append(ChatColor.WHITE).append(ChatColor.BOLD)
          .append(ChipManager.formatAmount(balance));
        sb.append(ChatColor.RESET).append(ChatColor.YELLOW).append(" E");

        // 差額表示
        DeltaDisplay delta = activeDelta.get(uuid);
        if (delta != null) {
            if (delta.remaining > 0) {
                String sign = delta.amount > 0 ? "+" : "";
                ChatColor color = delta.amount > 0 ? ChatColor.GREEN : ChatColor.RED;
                sb.append(" ").append(color).append(ChatColor.BOLD)
                  .append(sign).append(ChipManager.formatAmount(delta.amount));
                delta.remaining -= INTERVAL_TICKS;
            } else {
                activeDelta.remove(uuid);
            }
        }

        sb.append(ChatColor.DARK_GRAY).append("  ┃  ");
        sb.append(ChatColor.GRAY).append("Chips ");
        sb.append(ChatColor.GREEN).append(ChipManager.formatAmount(chipValue));
        sb.append(ChatColor.YELLOW).append(" E");
        sb.append(ChatColor.DARK_GRAY).append(" ┃");

        return sb.toString();
    }

    /**
     * プレイヤーのキャッシュをクリアする（ログアウト時用）。
     */
    public void clearPlayer(UUID playerId) {
        previousBalances.remove(playerId);
        activeDelta.remove(playerId);
    }

    /** 差額表示の一時データ */
    private static class DeltaDisplay {
        final long amount;
        int remaining;

        DeltaDisplay(long amount, int remaining) {
            this.amount = amount;
            this.remaining = remaining;
        }
    }
}
