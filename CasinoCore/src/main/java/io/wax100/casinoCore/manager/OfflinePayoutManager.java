package io.wax100.casinoCore.manager;

import io.wax100.casinoCore.CasinoCore;
import io.wax100.chipLib.ChipManager;
import io.wax100.chipLib.storage.StorageProvider;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

/**
 * オフライン中に確定したベット結果を永続化し、次回ログイン時に清算するマネージャ。
 *
 * <p>プレイヤーがログアウト中に闘技場の勝敗が確定した場合、
 * 結果を {@code offline_payouts.yml} に保存し、再ログイン時に
 * 配当の付与または没収の通知を行う。
 *
 * <p>データ構造（YML）:
 * <pre>
 * &lt;UUID&gt;:
 *   bet: 総ベット額
 *   won: 総配当額（0 = 全敗）
 * </pre>
 */
public class OfflinePayoutManager implements Listener {

    private final CasinoCore plugin;
    private final File file;
    private FileConfiguration config;
    private boolean dirty;

    /**
     * ChipLib の StorageProvider。{@code null} の場合は YAML フォールバック。
     */
    private StorageProvider storageProvider;

    public OfflinePayoutManager(CasinoCore plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "offline_payouts.yml");
        load();
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    private void load() {
        if (!file.exists()) {
            file.getParentFile().mkdirs();
            try {
                file.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("Cannot create offline_payouts.yml: " + e.getMessage());
            }
        }
        config = YamlConfiguration.loadConfiguration(file);
        dirty = false;
    }

    private void save() {
        if (!dirty) return;
        try {
            config.save(file);
            dirty = false;
        } catch (IOException e) {
            plugin.getLogger().severe("Cannot save offline_payouts.yml: " + e.getMessage());
        }
    }

    /**
     * オフラインプレイヤーのベット結果を記録する。
     *
     * <p>同一プレイヤーに対して複数回呼ばれた場合（両チームベット等）、
     * bet と won はそれぞれ加算される。
     *
     * <p>このメソッドは dirty フラグを立てるのみで、ディスクへの書き込みは
     * {@link #flush()} を呼ぶまで行わない。
     *
     * @param playerId  プレイヤーの UUID
     * @param betAmount ベット額
     * @param wonAmount 配当額（負けた場合は 0）
     */
    public void addOfflineResult(UUID playerId, long betAmount, long wonAmount) {
        if (storageProvider != null) {
            storageProvider.addOfflineResult(playerId, betAmount, wonAmount);
            return;
        }
        String path = playerId.toString();
        long currentBet = config.getLong(path + ".bet", 0L);
        long currentWon = config.getLong(path + ".won", 0L);
        config.set(path + ".bet", currentBet + betAmount);
        config.set(path + ".won", currentWon + wonAmount);
        dirty = true;
    }

    /**
     * dirty フラグが立っている場合にディスクへ書き込む。
     *
     * <p>配当計算の一括処理完了後に呼び出すことで、
     * ベッター数分のファイル I/O を 1 回に削減する。
     */
    public void flush() {
        if (storageProvider != null) {
            storageProvider.flush();
            return;
        }
        save();
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        long betAmount;
        long wonAmount;

        if (storageProvider != null) {
            long[] result = storageProvider.getAndClearOfflineResult(player.getUniqueId());
            if (result == null) return;
            betAmount = result[0];
            wonAmount = result[1];
        } else {
            String path = player.getUniqueId().toString();
            if (!config.contains(path)) return;
            betAmount = config.getLong(path + ".bet", 0L);
            wonAmount = config.getLong(path + ".won", 0L);
            config.set(path, null);
            dirty = true;
            save();
        }

        // 配当は即時入金する（遅延中にログアウトすると消失するため）
        if (wonAmount > 0) {
            Economy eco = plugin.getEconomy();
            if (eco != null) {
                eco.depositPlayer(player, wonAmount);
                plugin.getLogger().info("オフライン配当入金: " + player.getName()
                        + " / " + ChipManager.formatAmount(wonAmount) + " E");
            } else {
                plugin.getLogger().severe("Vault経済APIが利用不可: " + player.getName()
                        + " への配当 " + ChipManager.formatAmount(wonAmount) + " E を入金できませんでした");
            }
            // 戦績に配当額のみを記録（購入額はログアウト時に既に計上済み）
            plugin.getCasinoManager().getOrCreateStats(player.getUniqueId())
                    .recordCashout(wonAmount, 0);
        }

        // ログイン処理完了後にメッセージを送信（3秒後）
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;

            player.sendMessage("");
            player.sendMessage(ChatColor.GOLD + "【闘技場】 " + ChatColor.WHITE + "オフライン中のベット結果があります！");

            if (wonAmount > 0 && betAmount > wonAmount) {
                // 両チームベット: 一部勝ち・一部負け
                long lostPortion = betAmount - wonAmount;
                player.sendMessage(ChatColor.GREEN + "  勝ちベット配当: "
                        + ChipManager.formatAmount(wonAmount) + " E");
                player.sendMessage(ChatColor.RED + "  負けベット没収: "
                        + ChipManager.formatAmount(lostPortion) + " E");
                long net = wonAmount - betAmount;
                player.sendMessage(ChatColor.GRAY + "  差引: "
                        + (net >= 0 ? ChatColor.GREEN + "+" : ChatColor.RED.toString())
                        + ChipManager.formatAmount(net) + " E");
            } else if (wonAmount > 0) {
                // 全勝ち
                long profit = wonAmount - betAmount;
                player.sendMessage(ChatColor.GREEN + "予想が的中し、"
                        + ChipManager.formatAmount(wonAmount) + " E の配当を獲得しました！"
                        + ChatColor.GRAY + " (利益: +" + ChipManager.formatAmount(profit) + " E)");
            } else {
                // 全負け
                player.sendMessage(ChatColor.RED + "残念ながら予想は外れました (ベット: "
                        + ChipManager.formatAmount(betAmount) + " E 没収)");
            }

            player.sendMessage("");
        }, 60L);
    }

    /**
     * StorageProvider を設定する。
     *
     * <p>{@code null} でない場合、オフライン配当データの読み書きは
     * StorageProvider に委譲される。{@code null} の場合は YAML フォールバック。
     *
     * @param storageProvider StorageProvider インスタンス（{@code null} 可）
     */
    public void setStorageProvider(StorageProvider storageProvider) {
        this.storageProvider = storageProvider;
    }
}
