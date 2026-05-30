package io.wax100.casinoCore.manager;

import de.tr7zw.changeme.nbtapi.NBT;
import de.tr7zw.changeme.nbtapi.iface.ReadWriteNBTList;
import io.wax100.bindingCurseLib.BindingCurseManager;
import io.wax100.casinoCore.CasinoCore;
import io.wax100.chipLib.Chip;
import io.wax100.chipLib.ChipManager;
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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.stream.Collectors;

/**
 * カジノの状態管理・購入記録・換金処理・ランキングを管理するクラス。
 *
 * <p>
 * 主な責務:
 * <ul>
 * <li>カジノモードの ON/OFF 状態管理</li>
 * <li>セッション中のチップ購入記録の追跡</li>
 * <li>累計損益ランキングの更新・取得</li>
 * <li>アドベンチャーモードへの切り替えと復元</li>
 * <li>カジノシザースの配布・回収</li>
 * <li>チップの換金処理と結果通知</li>
 * <li>{@code data.yml} へのデータ永続化</li>
 * </ul>
 *
 * @see ChipManager
 * @see CasinoCore
 */
public class CasinoManager {

    /**
     * プラグインインスタンス
     */
    private final CasinoCore plugin;
    /**
     * カジノシザース識別用の {@link NamespacedKey}
     */
    private final NamespacedKey shearsKey;
    /**
     * 束縛の呪いアイテム所有者管理マネージャ
     */
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
     * プレイヤーごとの累計統計データ (UUID -> PlayerStats)
     */
    private final Map<UUID, PlayerStats> playerStats = new HashMap<>();
    /**
     * カジノ開始前のゲームモード保存 (UUID -> GameMode)
     */
    private final Map<UUID, GameMode> savedGameModes = new HashMap<>();

    /**
     * カジノモードに参加中のプレイヤー UUID セット
     */
    private final Set<UUID> casinoPlayers = new HashSet<>();
    /**
     * カジノ開始前の keepInventory ゲームルール値
     */
    private boolean savedKeepInventory;
    /**
     * カジノ開始時のワールド名（keepInventory 復元用）
     */
    private String savedWorldName;
    /**
     * 永続化データファイル ({@code data.yml})
     */
    private File dataFile;
    /**
     * 永続化データの設定オブジェクト
     */
    private FileConfiguration dataConfig;

    /**
     * コンストラクタ。
     *
     * <p>
     * データファイル ({@code data.yml}) から保存済みの状態を読み込む。
     *
     * @param plugin              CasinoCore プラグインインスタンス
     * @param bindingCurseManager 束縛の呪いアイテム管理マネージャ
     */
    public CasinoManager(CasinoCore plugin, BindingCurseManager bindingCurseManager) {
        this.plugin = plugin;
        this.shearsKey = new NamespacedKey(plugin, "casino_shears");
        this.bindingCurseManager = bindingCurseManager;
        loadData();
    }

    /**
     * 束縛の呪いアイテム所有者管理マネージャを取得する。
     *
     * @return {@link BindingCurseManager} インスタンス
     */
    public BindingCurseManager getBindingCurseManager() {
        return bindingCurseManager;
    }

    /**
     * カジノモードが稼働中かどうかを返す。
     *
     * @return 稼働中の場合 {@code true}
     */
    public boolean isCasinoActive() {
        return !casinoPlayers.isEmpty();
    }

    /**
     * カジノの稼働状態を設定する。
     *
     * <p>
     * {@code active} が {@code false} の場合、全プレイヤーをカジノから退出させる（全体シャットダウン用）。
     * {@code true} の場合はデータの保存のみ行う。
     *
     * @param active true: 稼働中, false: 停止中
     */
    public void setCasinoActive(boolean active) {
        if (!active) {
            casinoPlayers.clear();
        }
        saveData();
    }

    /**
     * 指定プレイヤーがカジノモードに参加中かどうかを返す。
     *
     * @param playerId プレイヤーの UUID
     * @return カジノモードに参加中の場合 {@code true}
     */
    public boolean isPlayerInCasino(UUID playerId) {
        return casinoPlayers.contains(playerId);
    }

    /**
     * プレイヤーをカジノモードに追加する。
     *
     * <p>
     * 最初のプレイヤー追加時に {@code keepInventory} ゲームルールを保存・有効化する。
     * ゲームモードをアドベンチャーに変更し、カジノシザースを配布する。
     *
     * @param player 追加するプレイヤー
     */
    public void addPlayerToCasino(Player player) {
        if (casinoPlayers.isEmpty() && savedWorldName == null) {
            World world = player.getWorld();
            savedWorldName = world.getName();
            Boolean current = world.getGameRuleValue(GameRule.KEEP_INVENTORY);
            savedKeepInventory = current != null && current;
            world.setGameRule(GameRule.KEEP_INVENTORY, true);
        }
        casinoPlayers.add(player.getUniqueId());
        getOrCreateStats(player.getUniqueId()).recordSessionJoin(player.getName());
        applyAdventureModeToPlayer(player);
        saveData();
    }

    /**
     * プレイヤーをカジノモードから退出させる。
     *
     * <p>
     * チップの換金、セッション購入記録の削除、ゲームモード復元、カジノシザース回収を行う。
     * 最後のプレイヤー退出時に {@code keepInventory} を元の値に復元する。
     *
     * @param player 退出するプレイヤー
     */
    public void removePlayerFromCasino(Player player) {
        cashoutPlayer(player, plugin.getChipManager(), plugin.getEconomy());
        sessionPurchases.remove(player.getUniqueId());

        GameMode savedMode = savedGameModes.remove(player.getUniqueId());
        if (savedMode != null) {
            player.setGameMode(savedMode);
        }
        removeCasinoShears(player);

        casinoPlayers.remove(player.getUniqueId());

        if (casinoPlayers.isEmpty()) {
            World world = savedWorldName != null ? Bukkit.getWorld(savedWorldName) : null;
            if (world != null) {
                world.setGameRule(GameRule.KEEP_INVENTORY, savedKeepInventory);
            }
            savedWorldName = null;
        }
        saveData();
    }

    /**
     * カジノ参加中のプレイヤーが切断した際の後処理。
     *
     * <p>
     * チップの換金は行わず、ゲームモード復元・シザース回収・カジノプレイヤーセットからの除外のみ行う。
     * 最後のプレイヤーが切断した場合は {@code keepInventory} を復元する。
     *
     * @param player 切断したプレイヤー
     */
    public void handlePlayerDisconnect(Player player) {
        GameMode savedMode = savedGameModes.remove(player.getUniqueId());
        if (savedMode != null) {
            player.setGameMode(savedMode);
        }
        removeCasinoShears(player);

        casinoPlayers.remove(player.getUniqueId());

        if (casinoPlayers.isEmpty()) {
            World world = savedWorldName != null ? Bukkit.getWorld(savedWorldName) : null;
            if (world != null) {
                world.setGameRule(GameRule.KEEP_INVENTORY, savedKeepInventory);
            }
            savedWorldName = null;
        }
        saveData();
    }

    /**
     * プレイヤーのチップ購入を記録する。
     *
     * <p>
     * 同一プレイヤーの複数回の購入は累算される。
     *
     * @param playerId プレイヤーの UUID
     * @param amount   購入額
     */
    public void recordPurchase(UUID playerId, long amount) {
        Long current = sessionPurchases.get(playerId);
        long currentVal = current != null ? current : 0L;
        sessionPurchases.put(playerId, currentVal + amount);
        getOrCreateStats(playerId).addPurchase(amount);
        saveData();
    }

    /**
     * プレイヤーのセッション中の購入総額を取得する。
     *
     * @param playerId プレイヤーの UUID
     * @return 購入総額。記録がない場合は {@code 0}
     */
    public long getSessionPurchases(UUID playerId) {
        return sessionPurchases.getOrDefault(playerId, 0L);
    }

    /**
     * 全プレイヤーのセッション購入データをクリアする。
     *
     * <p>
     * カジノモード終了時に呼び出される。
     */
    public void clearAllSessionData() {
        sessionPurchases.clear();
        casinoPlayers.clear();
        saveData();
    }

    /**
     * プレイヤーの累計損益ランキングを更新する。
     *
     * <p>
     * 換金時に呼び出され、損益結果を累算する。
     *
     * @param playerId  プレイヤーの UUID
     * @param netResult 今回の損益（正: 勝ち、負: 負け）
     */
    public void updateRanking(UUID playerId, long netResult) {
        Long current = ranking.get(playerId);
        long currentVal = current != null ? current : 0L;
        ranking.put(playerId, currentVal + netResult);
        saveData();
    }

    /**
     * 累計損益ランキングを降順にソートして取得する。
     *
     * @param limit 最大取得件数
     * @return 損益の降順でソートされたエントリリスト
     */
    public List<Map.Entry<UUID, Long>> getSortedRanking(int limit) {
        List<Map.Entry<UUID, Long>> sorted = new ArrayList<>(ranking.entrySet());

        sorted.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));
        return sorted.subList(0, Math.min(sorted.size(), limit));
    }

    /**
     * ランキングと全プレイヤー統計をリセットする。
     */
    public void resetRanking() {
        ranking.clear();
        playerStats.clear();
        saveData();
    }

    /**
     * 指定プレイヤーの統計データを取得する。
     *
     * @param playerId プレイヤーの UUID
     * @return 統計データ。存在しない場合は {@code null}
     */
    public PlayerStats getStatsForPlayer(UUID playerId) {
        return playerStats.get(playerId);
    }

    /**
     * 指定 UUID の PlayerStats を取得する。存在しない場合は新規作成する。
     *
     * @param playerId プレイヤーの UUID
     * @return PlayerStats インスタンス
     */
    private PlayerStats getOrCreateStats(UUID playerId) {
        return playerStats.computeIfAbsent(playerId, k -> new PlayerStats());
    }

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
            casinoPlayers.add(p.getUniqueId());
            savedGameModes.put(p.getUniqueId(), p.getGameMode());
            p.setGameMode(GameMode.ADVENTURE);
            giveCasinoShears(p);
        }
    }

    /**
     * 全プレイヤーのゲームモードを元に戻し、keepInventory を復元する。
     *
     * <p>カジノシザースの回収は {@link #cashoutAllPlayers()} で行われるため、
     * このメソッドではゲームモードとゲームルールの復元のみ行う。
     */
    public void restoreGameModes() {
        for (Map.Entry<UUID, GameMode> entry : savedGameModes.entrySet()) {
            Player p = Bukkit.getPlayer(entry.getKey());
            if (p != null && p.isOnline()) {
                p.setGameMode(entry.getValue());
            }
        }
        savedGameModes.clear();
        casinoPlayers.clear();

        World world = savedWorldName != null ? Bukkit.getWorld(savedWorldName) : Bukkit.getWorlds().get(0);
        if (world != null) {
            world.setGameRule(GameRule.KEEP_INVENTORY, savedKeepInventory);
        }
        savedWorldName = null;
    }

    /**
     * カジノ中に参加したプレイヤーのゲームモードを保存してアドベンチャーにする。
     *
     * <p>
     * カジノ稼働中にログインしたプレイヤーに対して呼び出される。
     * カジノシザースも同時に配布する。
     *
     * @param player 対象プレイヤー
     */
    public void applyAdventureModeToPlayer(Player player) {
        savedGameModes.put(player.getUniqueId(), player.getGameMode());
        player.setGameMode(GameMode.ADVENTURE);
        giveCasinoShears(player);
    }

    /**
     * カーペット破壊用のカジノシザースを配布する。
     *
     * <p>
     * CanDestroy NBT で全カーペット素材を指定し、アドベンチャーモードで破壊可能にする。
     * 束縛の呪いを付与し、{@link BindingCurseManager} で所有者を設定することで、
     * 配布先のプレイヤーのみが使用可能になる。
     *
     * @param player 配布対象プレイヤー
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
     * カジノシザースをインベントリから回収する。
     *
     * <p>
     * {@link PersistentDataType#BYTE} のカスタムキーを持つハサミを検索し、
     * カジノシザースを削除する。
     *
     * @param player 対象プレイヤー
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

    /**
     * 全オンラインプレイヤーのチップを換金する。
     *
     * <p>
     * カジノモード終了時 ({@code /casino off}) に呼び出される。
     */
    public void cashoutAllPlayers() {
        ChipManager chipManager = plugin.getChipManager();
        Economy economy = plugin.getEconomy();
        for (UUID uuid : new HashSet<>(casinoPlayers)) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                cashoutPlayer(player, chipManager, economy);
                removeCasinoShears(player);
            }
        }
    }

    /**
     * 単一プレイヤーのチップを換金する。
     *
     * <p>
     * {@code /chip cashout} コマンドから呼び出される。
     * 換金後、当該プレイヤーのセッション購入記録を削除する。
     *
     * @param player 換金対象プレイヤー
     */
    public void cashoutSinglePlayer(Player player) {
        cashoutPlayer(player, plugin.getChipManager(), plugin.getEconomy());
        removeCasinoShears(player);
        sessionPurchases.remove(player.getUniqueId());
        saveData();
    }

    /**
     * 個別プレイヤーのチップを換金し、結果を通知する。
     *
     * <p>
     * チップの合計額を Vault 経済に預入し、損益結果をランキングに記録する。
     * 購入額・換金額がともに 0 の場合は何もしない。
     *
     * @param player      対象プレイヤー
     * @param chipManager チップ管理マネージャ
     * @param economy     Vault 経済インスタンス
     */
    private void cashoutPlayer(Player player, ChipManager chipManager, Economy economy) {
        long totalValue = chipManager.calculateTotalValue(player);
        long purchased = getSessionPurchases(player.getUniqueId());

        if (totalValue == 0 && purchased == 0)
            return;

        Map<Chip, Integer> breakdown = chipManager.removeAllChips(player);
        if (totalValue > 0) {
            economy.depositPlayer(player, totalValue);
        }

        long netResult = totalValue - purchased;
        if (purchased > 0) {
            updateRanking(player.getUniqueId(), netResult);
        }
        getOrCreateStats(player.getUniqueId()).recordCashout(totalValue, purchased);

        sendCashoutMessage(player, totalValue, purchased, netResult, breakdown);
    }

    /**
     * 換金結果メッセージを送信する。
     *
     * <p>
     * 購入額・換金額・損益・勝敗結果・売却内訳をフォーマットしてプレイヤーに送信する。
     *
     * @param player     対象プレイヤー
     * @param totalValue 換金合計額
     * @param purchased  セッション中の購入総額
     * @param netResult  損益（換金額 − 購入額）
     * @param breakdown  チップの売却内訳 (額面 → 枚数)
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
            player.sendMessage(Messages.PREFIX + ChatColor.GRAY + "損 益: "
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

    /**
     * 売却内訳の1行分をフォーマットする。
     *
     * @param chip  チップ種別
     * @param count 売却枚数
     * @return フォーマット済みの内訳文字列
     */
    private String formatBreakdownEntry(Chip chip, int count) {
        return chip.getChatColor() + ChipManager.formatAmount(chip.getValue())
                + " E" + ChatColor.GRAY + "(" + chip.getChatColor() + chip.getColorName()
                + ChatColor.GRAY + "): " + ChatColor.WHITE + count + " 枚";
    }

    /**
     * {@code data.yml} からデータを読み込む。
     *
     * <p>
     * ファイルが存在しない場合は新規作成する。
     * カジノ状態・ランキング・購入記録・ゲームモード保存データを復元する。
     */

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

        // ランタイム状態の読み込み（runtime セクション優先、後方互換でルートも確認）
        ConfigurationSection runtime = dataConfig.getConfigurationSection("runtime");
        if (runtime != null) {
            List<String> playerUuids = runtime.getStringList("casino-players");
            for (String uuidStr : playerUuids) {
                try {
                    casinoPlayers.add(UUID.fromString(uuidStr));
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("無効なUUID (casino-players): " + uuidStr);
                }
            }
            loadUuidMap(runtime, "session-purchases", sessionPurchases);
            savedKeepInventory = runtime.getBoolean("saved-keep-inventory", false);
            savedWorldName = runtime.getString("saved-world-name", null);
            loadGameModes(runtime);
        } else {
            // 旧フォーマットからの読み込み（後方互換）
            List<String> playerUuids = dataConfig.getStringList("casino-players");
            for (String uuidStr : playerUuids) {
                try {
                    casinoPlayers.add(UUID.fromString(uuidStr));
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("無効なUUID (casino-players): " + uuidStr);
                }
            }
            loadUuidMap(dataConfig, "session-purchases", sessionPurchases);
            savedKeepInventory = dataConfig.getBoolean("saved-keep-inventory", false);
            savedWorldName = dataConfig.getString("saved-world-name", null);
            loadGameModes(dataConfig);
        }

        // ランキングの読み込み
        loadUuidMap(dataConfig, "ranking", ranking);

        // プレイヤー統計の読み込み
        ConfigurationSection playersSection = dataConfig.getConfigurationSection("players");
        if (playersSection != null) {
            for (String key : playersSection.getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(key);
                    ConfigurationSection playerSec = playersSection.getConfigurationSection(key);
                    if (playerSec != null) {
                        playerStats.put(uuid, PlayerStats.loadFrom(playerSec));
                    }
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("無効なUUID (players): " + key);
                }
            }
        }
    }

    /**
     * 現在の状態を {@code data.yml} に保存する（非同期）。
     * 各状態変更時に呼び出される。
     */
    public synchronized void saveData() {
        saveData(true);
    }

    /**
     * 現在の状態を {@code data.yml} に保存する。
     *
     * @param async true の場合非同期でファイルに保存し、false の場合同期で保存する。
     */
    public synchronized void saveData(boolean async) {
        YamlConfiguration copy = new YamlConfiguration();

        // ランタイム状態
        ConfigurationSection runtime = copy.createSection("runtime");
        runtime.set("casino-players", casinoPlayers.stream()
                .map(UUID::toString)
                .collect(Collectors.toList()));
        saveUuidMap(runtime, "session-purchases", sessionPurchases);
        runtime.set("saved-keep-inventory", savedKeepInventory);
        runtime.set("saved-world-name", savedWorldName);
        saveGameModes(runtime);

        // ランキング
        saveUuidMap(copy, "ranking", ranking);

        // プレイヤー統計
        for (Map.Entry<UUID, PlayerStats> entry : playerStats.entrySet()) {
            ConfigurationSection playerSec = copy.createSection("players." + entry.getKey().toString());
            entry.getValue().saveTo(playerSec);
        }

        if (async && Bukkit.getServer() != null && plugin.isEnabled()) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                synchronized (dataFile) {
                    try {
                        copy.save(dataFile);
                    } catch (IOException e) {
                        plugin.getLogger().log(Level.SEVERE, "data.ymlの非同期保存に失敗しました", e);
                    }
                }
            });
        } else {
            synchronized (dataFile) {
                try {
                    copy.save(dataFile);
                } catch (IOException e) {
                    plugin.getLogger().log(Level.SEVERE, "data.ymlの保存に失敗しました", e);
                }
            }
        }
    }

    /**
     * UUID をキーとする Long マップを設定ファイルから読み込む。
     *
     * <p>
     * 無効な UUID キーは警告ログを出力してスキップする。
     *
     * @param section 設定ファイルのセクション名
     * @param target  読み込み先のマップ
     */
    private void loadUuidMap(ConfigurationSection parent, String section, Map<UUID, Long> target) {
        ConfigurationSection sec = parent.getConfigurationSection(section);
        if (sec == null)
            return;
        for (String key : sec.getKeys(false)) {
            try {
                target.put(UUID.fromString(key), sec.getLong(key));
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("無効なUUID (" + section + "): " + key);
            }
        }
    }

    /**
     * UUID をキーとする Long マップを設定ファイルに書き出す。
     *
     * <p>
     * 既存のセクションをクリアしてから書き込む。
     *
     * @param config  書き出し先の設定オブジェクト
     * @param section 設定ファイルのセクション名
     * @param source  書き出すマップ
     */
    private void saveUuidMap(ConfigurationSection config, String section, Map<UUID, Long> source) {
        config.set(section, null);
        for (Map.Entry<UUID, Long> entry : source.entrySet()) {
            config.set(section + "." + entry.getKey().toString(), entry.getValue());
        }
    }

    /**
     * 保存済みゲームモードデータを設定ファイルから読み込む。
     *
     * <p>
     * 無効な UUID やゲームモード値は警告ログを出力してスキップする。
     */
    private void loadGameModes(ConfigurationSection parent) {
        ConfigurationSection sec = parent.getConfigurationSection("saved-game-modes");
        if (sec == null)
            return;
        for (String key : sec.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                GameMode mode = GameMode.valueOf(sec.getString(key, GameMode.SURVIVAL.name()));
                savedGameModes.put(uuid, mode);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("無効なゲームモードデータ (saved-game-modes): " + key);
            }
        }
    }

    /**
     * 現在のゲームモード保存データを設定ファイルに書き出す。
     *
     * @param config 書き出し先の設定オブジェクト
     */
    private void saveGameModes(ConfigurationSection config) {
        config.set("saved-game-modes", null);
        for (Map.Entry<UUID, GameMode> entry : savedGameModes.entrySet()) {
            config.set("saved-game-modes." + entry.getKey().toString(), entry.getValue().name());
        }
    }
}
