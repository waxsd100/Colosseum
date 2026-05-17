package io.wax100.casinoCore.manager;

import de.tr7zw.changeme.nbtapi.NBT;
import de.tr7zw.changeme.nbtapi.iface.ReadWriteNBTList;
import io.wax100.bindingCurseLib.BindingCurseManager;
import io.wax100.casinoCore.CasinoCore;
import io.wax100.casinoCore.manager.ChipManager.Chip;
import io.wax100.casinoCore.util.Messages;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.GameRule;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.logging.Level;

/**
 * カジノの状態管理・購入記録・換金処理・ランキングを管理するクラス
 */
public class CasinoManager {

    private final CasinoCore plugin;
    private final NamespacedKey shearsKey;
    private final BindingCurseManager bindingCurseManager;

    /**
     * セッション中の購入記録 (UUID -> 購入総額)
     */
    private final Map<UUID, Long> sessionPurchases = new HashMap<>();
    /**
     * 累計損益ランキング (UUID -> 累計損益)
     */
    private final Map<UUID, Long> ranking = new LinkedHashMap<>();
    /**
     * カジノ開始前のゲームモード保存 (UUID -> GameMode)
     */
    private final Map<UUID, GameMode> savedGameModes = new HashMap<>();

    private boolean casinoActive;
    private boolean savedKeepInventory;
    private String savedWorldName;
    private File dataFile;
    private FileConfiguration dataConfig;

    public CasinoManager(CasinoCore plugin, BindingCurseManager bindingCurseManager) {
        this.plugin = plugin;
        this.shearsKey = new NamespacedKey(plugin, "casino_shears");
        this.bindingCurseManager = bindingCurseManager;
        loadData();
    }

    public BindingCurseManager getBindingCurseManager() {
        return bindingCurseManager;
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

    // ── ゲームモード管理 ──

    /**
     * 全プレイヤーのゲームモードを保存し、アドベンチャーに変更する。
     * 管理者も含めて全員が対象となる。keepInventory を ON にする。
     *
     * @param executor casino on を実行したプレイヤー（ワールド判定に使用）
     */
    public void applyAdventureMode(Player executor) {
        World world = executor.getWorld();
        savedWorldName = world.getName();
        Boolean current = world.getGameRuleValue(GameRule.KEEP_INVENTORY);
        savedKeepInventory = current != null && current;
        world.setGameRule(GameRule.KEEP_INVENTORY, true);

        for (Player p : Bukkit.getOnlinePlayers()) {
            savedGameModes.put(p.getUniqueId(), p.getGameMode());
            p.setGameMode(GameMode.ADVENTURE);
            giveCasinoShears(p);
        }
    }

    /**
     * 全プレイヤーのゲームモードを元に戻し、keepInventory を復元する。
     * カジノシザースを回収する。
     */
    public void restoreGameModes() {
        for (Map.Entry<UUID, GameMode> entry : savedGameModes.entrySet()) {
            Player p = Bukkit.getPlayer(entry.getKey());
            if (p != null && p.isOnline()) {
                p.setGameMode(entry.getValue());
                removeCasinoShears(p);
            }
        }
        savedGameModes.clear();

        World world = savedWorldName != null ? Bukkit.getWorld(savedWorldName) : Bukkit.getWorlds().get(0);
        if (world != null) {
            world.setGameRule(GameRule.KEEP_INVENTORY, savedKeepInventory);
        }
        savedWorldName = null;
    }

    /**
     * カジノ中に参加したプレイヤーのゲームモードを保存してアドベンチャーにする。
     */
    public void applyAdventureModeToPlayer(Player player) {
        savedGameModes.put(player.getUniqueId(), player.getGameMode());
        player.setGameMode(GameMode.ADVENTURE);
        giveCasinoShears(player);
    }

    // ── シザース管理 ──

    /**
     * カーペット破壊用のカジノシザースを配布する。
     * CanDestroy NBT で全カーペット素材を指定し、アドベンチャーモードで破壊可能にする。
     */
    private void giveCasinoShears(Player player) {
        ItemStack shears = new ItemStack(Material.SHEARS);
        ItemMeta meta = shears.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.GOLD.toString() + ChatColor.BOLD + "カジノシザース");
            meta.setLore(Arrays.asList(
                    ChatColor.GRAY + "カジノチップ（カーペット）を",
                    ChatColor.GRAY + "回収するためのハサミです。"));
            meta.getPersistentDataContainer().set(shearsKey, PersistentDataType.BYTE, (byte) 1);
            meta.setUnbreakable(true);
            // 束縛の呪いを付与（BindingCurseLib の所有者判定に必要）
            meta.addEnchant(Enchantment.BINDING_CURSE, 1, true);
            meta.addItemFlags(
                    ItemFlag.HIDE_DESTROYS,
                    ItemFlag.HIDE_UNBREAKABLE,
                    ItemFlag.HIDE_ENCHANTS);
            shears.setItemMeta(meta);
        }
        NBT.modify(shears, nbt -> {
            ReadWriteNBTList<String> canDestroy = nbt.getStringList("CanDestroy");
            for (Chip chip : Chip.values()) {
                canDestroy.add("minecraft:" + chip.getMaterial().getKey().getKey());
            }
        });
        // BindingCurseLib で所有者を設定（このプレイヤーのみ使用可能）
        bindingCurseManager.setItemOwner(shears, player);
        player.getInventory().addItem(shears);
    }

    /**
     * カジノシザースをインベントリから回収する
     */
    private void removeCasinoShears(Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() == Material.SHEARS && item.hasItemMeta()) {
                if (Objects.requireNonNull(item.getItemMeta())
                        .getPersistentDataContainer().has(shearsKey, PersistentDataType.BYTE)) {
                    player.getInventory().remove(item);
                }
            }
        }
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
     * 単一プレイヤーのチップを換金する（/chip cashout 用）
     */
    public void cashoutSinglePlayer(Player player) {
        cashoutPlayer(player, plugin.getChipManager(), plugin.getEconomy());
        sessionPurchases.remove(player.getUniqueId());
        saveData();
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

        if (purchased > 0) {
            player.sendMessage(Messages.PREFIX + ChatColor.GRAY + "購入額: "
                    + ChatColor.WHITE + ChipManager.formatAmount(purchased) + " E");
        }
        if (totalValue > 0) {
            player.sendMessage(Messages.PREFIX + ChatColor.GRAY + "換金額: "
                    + ChatColor.WHITE + ChipManager.formatAmount(totalValue) + " E");
        }

        if (purchased > 0) {
            ChatColor color;
            String sign;
            if (netResult > 0) {
                sign = "+";
                color = ChatColor.GREEN;
            } else if (netResult < 0) {
                sign = "";
                color = ChatColor.RED;
            } else {
                sign = "±";
                color = ChatColor.YELLOW;
            }
            player.sendMessage(Messages.PREFIX + ChatColor.GRAY + "損　益: "
                    + color + sign + ChipManager.formatAmount(netResult) + " E");
            player.sendMessage("");

            String resultMsg;
            if (netResult > 0) {
                resultMsg = ChatColor.GREEN + "今回の結果: "
                        + ChatColor.GOLD + ChatColor.BOLD + "勝ち "
                        + ChatColor.RESET + ChatColor.GREEN + "(+"
                        + ChipManager.formatAmount(netResult) + " E)";
            } else if (netResult < 0) {
                resultMsg = ChatColor.RED + "今回の結果: "
                        + ChatColor.DARK_RED + ChatColor.BOLD + "負け "
                        + ChatColor.RESET + ChatColor.RED + "("
                        + ChipManager.formatAmount(netResult) + " E)";
            } else {
                resultMsg = ChatColor.YELLOW + "今回の結果: "
                        + ChatColor.GRAY + "引き分け (±0 E)";
            }
            player.sendMessage(Messages.PREFIX + resultMsg);
        } else if (totalValue > 0) {
            player.sendMessage(Messages.PREFIX + ChatColor.GREEN + "チップを換金しました。");
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
        savedKeepInventory = dataConfig.getBoolean("saved-keep-inventory", false);
        savedWorldName = dataConfig.getString("saved-world-name", null);
        loadGameModes();
    }

    public void saveData() {
        dataConfig.set("casino-active", casinoActive);
        saveUuidMap("ranking", ranking);
        saveUuidMap("session-purchases", sessionPurchases);
        dataConfig.set("saved-keep-inventory", savedKeepInventory);
        dataConfig.set("saved-world-name", savedWorldName);
        saveGameModes();
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

    private void loadGameModes() {
        ConfigurationSection sec = dataConfig.getConfigurationSection("saved-game-modes");
        if (sec == null) return;
        for (String key : sec.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                GameMode mode = GameMode.valueOf(sec.getString(key, "SURVIVAL"));
                savedGameModes.put(uuid, mode);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("無効なゲームモードデータ (saved-game-modes): " + key);
            }
        }
    }

    private void saveGameModes() {
        dataConfig.set("saved-game-modes", null);
        for (Map.Entry<UUID, GameMode> entry : savedGameModes.entrySet()) {
            dataConfig.set("saved-game-modes." + entry.getKey().toString(), entry.getValue().name());
        }
    }
}
