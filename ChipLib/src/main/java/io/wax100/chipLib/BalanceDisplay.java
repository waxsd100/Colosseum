package io.wax100.chipLib;

import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

/**
 * アクションバーに所持金をリアルタイム表示するタスク。
 *
 * <p>全オンラインプレイヤーに対して定期的（20tick=1秒ごと）に
 * 現在の所持金をアクションバーへ表示する。
 */
public class BalanceDisplay implements Runnable {

    private final ChipPlugin plugin;
    private BukkitTask task;

    /** 表示更新間隔（tick） */
    private static final long INTERVAL_TICKS = 20L;

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
    }

    @Override
    public void run() {
        Economy economy = plugin.getEconomy();
        if (economy == null) return;

        for (Player player : Bukkit.getOnlinePlayers()) {
            long balance = (long) economy.getBalance(player);
            long chipValue = plugin.getChipManager().calculateTotalValue(player);

            String text = ChatColor.DARK_GRAY.toString() + "┃ "
                    + ChatColor.GRAY + "Balance "
                    + ChatColor.WHITE + ChatColor.BOLD + ChipManager.formatAmount(balance)
                    + ChatColor.RESET + ChatColor.YELLOW + " E"
                    + ChatColor.DARK_GRAY + "  ┃  "
                    + ChatColor.GRAY + "Chips "
                    + ChatColor.GREEN + ChipManager.formatAmount(chipValue)
                    + ChatColor.YELLOW + " E"
                    + ChatColor.DARK_GRAY + " ┃";

            player.spigot().sendMessage(
                    ChatMessageType.ACTION_BAR,
                    TextComponent.fromLegacyText(text));
        }
    }
}
