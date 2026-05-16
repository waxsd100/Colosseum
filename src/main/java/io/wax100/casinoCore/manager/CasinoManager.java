package io.wax100.casinoCore.manager;

import io.wax100.casinoCore.CasinoCore;
import io.wax100.casinoCore.manager.ChipManager.Chip;
import io.wax100.casinoCore.util.Messages;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * カジノの状態管理・購入記録・換金処理・ランキングを管理するクラス
 */
public class CasinoManager {

    private final CasinoCore plugin;
    /**
     * セッション中の購入記録 (UUID -> 購入総額)
     */
    private final Map<UUID, Long> sessionPurchases = new HashMap<>();
    /**
     * 累計損益ランキング (UUID -> 累計損益)
     */
    private final Map<UUID, Long> ranking = new LinkedHashMap<>();
    private boolean casinoActive;
    private File dataFile;
    private FileConfiguration dataConfig;

    public CasinoManager(CasinoCore plugin) {
        this.plugin = plugin;
        loadData();
    }

    // ── 状態管理 ──

    public boolean isCasinoActive() {
        return casinoActive;
    }

    public void setCasinoActive(boolean active) {
        this.casinoActive = active;
        saveData();
    }

    // ── 購入記録 ──

    public void recordPurchase(UUID playerId, long amount) {
        sessionPurchases.merge(playerId, amount, Long::sum);
        saveData();
    }

    public long getSessionPurchases(UUID playerId) {
        return sessionPurchases.getOrDefault(playerId, 0L);
    }

    public void clearAllSessionData() {
        sessionPurchases.clear();
        saveData();
    }

    // ── ランキング ──

    public void updateRanking(UUID playerId, long netResult) {
        ranking.merge(playerId, netResult, Long::sum);
        saveData();
    }

    public List<Map.Entry<UUID, Long>> getSortedRanking(int limit) {
        List<Map.Entry<UUID, Long>> sorted = new ArrayList<>(ranking.entrySet());
        sorted.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));
        return sorted.subList(0, Math.min(sorted.size(), limit));
    }

    // ── 換金処理 ──

    /**
     * 全オンラインプレイヤーのチップを換金する
     */
    public void cashoutAllPlayers() {
        ChipManager chipManager = plugin.getChipManager();
        Economy economy = plugin.getEconomy();
        for (Player player : Bukkit.getOnlinePlayers()) {
            cashoutPlayer(player, chipManager, economy);
        }
    }

    /**
     * 個別プレイヤーのチップを換金し、結果を通知する
     */
    private void cashoutPlayer(Player player, ChipManager chipManager, Economy economy) {
        long totalValue = chipManager.calculateTotalValue(player);
        long purchased = getSessionPurchases(player.getUniqueId());

        if (totalValue == 0 && purchased == 0) return;

        Map<Chip, Integer> breakdown = chipManager.removeAllChips(player);
        if (totalValue > 0) {
            economy.depositPlayer(player, totalValue);
        }

        long netResult = totalValue - purchased;
        if (purchased > 0) {
            updateRanking(player.getUniqueId(), netResult);
        }

        sendCashoutMessage(player, totalValue, purchased, netResult, breakdown);
    }

    /**
     * 換金結果メッセージを送信する
     */
    private void sendCashoutMessage(Player player, long totalValue, long purchased,
                                    long netResult, Map<Chip, Integer> breakdown) {
        player.sendMessage(Messages.SEPARATOR);

        if (totalValue > 0) {
            player.sendMessage(Messages.PREFIX + ChatColor.GREEN + "チップを換金しました。"
                    + ChatColor.YELLOW + ChipManager.formatAmount(totalValue) + " E "
                    + ChatColor.GREEN + "が返金されました。");
        }

        if (purchased > 0) {
            String resultMsg;
            if (netResult > 0) {
                resultMsg = ChatColor.GREEN + "今回の結果: "
                        + ChatColor.YELLOW + ChipManager.formatAmount(netResult) + " E "
                        + ChatColor.GREEN + "の " + ChatColor.GOLD + ChatColor.BOLD + "勝ち "
                        + ChatColor.RESET + ChatColor.GREEN + "です！";
            } else if (netResult < 0) {
                resultMsg = ChatColor.RED + "今回の結果: "
                        + ChatColor.YELLOW + ChipManager.formatAmount(Math.abs(netResult)) + " E "
                        + ChatColor.RED + "の " + ChatColor.DARK_RED + ChatColor.BOLD + "負け "
                        + ChatColor.RESET + ChatColor.RED + "です...";
            } else {
                resultMsg = ChatColor.YELLOW + "今回の結果: "
                        + ChatColor.GRAY + "±0 E " + ChatColor.YELLOW + "（引き分け）";
            }
            player.sendMessage(Messages.PREFIX + resultMsg);
        }

        player.sendMessage(Messages.SEPARATOR);
        player.sendMessage(Messages.PREFIX + ChatColor.GRAY + "[ 売却内訳 ]");

        Chip[] chips = Chip.values();
        for (int i = 0; i < chips.length; i += 2) {
            StringBuilder line = new StringBuilder(Messages.PREFIX);
            line.append(formatBreakdownEntry(chips[i], breakdown.getOrDefault(chips[i], 0)));
            if (i + 1 < chips.length) {
                line.append("    ");
                line.append(formatBreakdownEntry(chips[i + 1], breakdown.getOrDefault(chips[i + 1], 0)));
            }
            player.sendMessage(line.toString());
        }

        player.sendMessage(Messages.SEPARATOR);
    }

    private String formatBreakdownEntry(Chip chip, int count) {
        return chip.getChatColor() + ChipManager.formatAmount(chip.getValue())
                + " E" + ChatColor.GRAY + "(" + chip.getChatColor() + chip.getColorName()
                + ChatColor.GRAY + "): " + ChatColor.WHITE + count + " 枚";
    }


    // ── 永続化 ──

    private void loadData() {
        dataFile = new File(plugin.getDataFolder(), "data.yml");
        if (!dataFile.exists()) {
            plugin.getDataFolder().mkdirs();
            try {
                dataFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "data.ymlの作成に失敗しました", e);
            }
        }
        dataConfig = YamlConfiguration.loadConfiguration(dataFile);
        casinoActive = dataConfig.getBoolean("casino-active", false);
        loadUuidMap("ranking", ranking);
        loadUuidMap("session-purchases", sessionPurchases);
    }

    public void saveData() {
        dataConfig.set("casino-active", casinoActive);
        saveUuidMap("ranking", ranking);
        saveUuidMap("session-purchases", sessionPurchases);
        try {
            dataConfig.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "data.ymlの保存に失敗しました", e);
        }
    }

    private void loadUuidMap(String section, Map<UUID, Long> target) {
        ConfigurationSection sec = dataConfig.getConfigurationSection(section);
        if (sec == null) return;
        for (String key : sec.getKeys(false)) {
            try {
                target.put(UUID.fromString(key), sec.getLong(key));
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("無効なUUID (" + section + "): " + key);
            }
        }
    }

    private void saveUuidMap(String section, Map<UUID, Long> source) {
        dataConfig.set(section, null);
        for (Map.Entry<UUID, Long> entry : source.entrySet()) {
            dataConfig.set(section + "." + entry.getKey().toString(), entry.getValue());
        }
    }
}
