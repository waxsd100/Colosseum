package io.wax100.casinoCore.manager;

import io.wax100.bindingCurseLib.BindingCurseManager;
import io.wax100.casinoCore.CasinoCore;
import io.wax100.chipLib.Chip;
import io.wax100.chipLib.ChipManager;
import io.wax100.chipLib.ChipPlugin;
import io.wax100.chipLib.ranking.RankingManager;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.GameRule;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;


import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

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
 * <li>チップの換金処理と結果通知</li>
 * </ul>
 *
 * <p>シザースの管理は {@link CasinoShearsHelper}、
 * 換金メッセージの生成は {@link CashoutMessageFormatter}、
 * データ永続化は {@link CasinoDataStore} にそれぞれ委譲される。
 *
 * @see ChipManager
 * @see CasinoCore
 * @see CasinoShearsHelper
 * @see CashoutMessageFormatter
 * @see CasinoDataStore
 */
public class CasinoManager {

    /**
     * プラグインインスタンス
     */
    private final CasinoCore plugin;
    /**
     * カジノシザース管理ヘルパー
     */
    private final CasinoShearsHelper shearsHelper;
    /**
     * 束縛の呪いアイテム所有者管理マネージャ
     */
    private final BindingCurseManager bindingCurseManager;
    /**
     * データ永続化ストア
     */
    private final CasinoDataStore dataStore;

    /**
     * セッション中の購入記録 (UUID -> 購入総額)
     * <p>非同期保存時のイテレーション安全性のため {@link ConcurrentHashMap} を使用。
     */
    private final Map<UUID, Long> sessionPurchases = new ConcurrentHashMap<>();

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
     * <p>非同期保存時のイテレーション安全性のため {@link ConcurrentHashMap} ベースのセットを使用。
     */
    private final Set<UUID> casinoPlayers = ConcurrentHashMap.newKeySet();
    /**
     * カジノ開始前の keepInventory ゲームルール値
     */
    private boolean savedKeepInventory;
    /**
     * カジノ開始時のワールド名（keepInventory 復元用）
     */
    private String savedWorldName;

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
        this.bindingCurseManager = bindingCurseManager;
        this.shearsHelper = new CasinoShearsHelper(plugin, bindingCurseManager);
        this.dataStore = new CasinoDataStore(plugin);
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
     * カジノシザース管理ヘルパーを取得する。
     *
     * @return {@link CasinoShearsHelper} インスタンス
     */
    public CasinoShearsHelper getShearsHelper() {
        return shearsHelper;
    }

    // ──────────────────────────────────────────────────────
    // カジノ状態管理
    // ──────────────────────────────────────────────────────

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

    // ──────────────────────────────────────────────────────
    // プレイヤー参加・退出
    // ──────────────────────────────────────────────────────

    /**
     * プレイヤーをカジノモードに追加する。
     *
     * <p>
     * 最初のプレイヤー追加時に {@code keepInventory} ゲームルールを保存・有効化する。
     * ゲームモードをアドベンチャーに変更し、カジノシザースを配布する。
     *
     * @param player 追加するプレイヤー
     * @throws IllegalArgumentException プレイヤーがオフラインの場合
     */
    public void addPlayerToCasino(Player player) {
        if (!player.isOnline()) {
            throw new IllegalArgumentException("オフラインプレイヤーをカジノに追加することはできません: " + player.getName());
        }
        saveKeepInventoryIfNeeded(player.getWorld());
        casinoPlayers.add(player.getUniqueId());
        getOrCreateStats(player.getUniqueId()).recordSessionJoin(player.getName());
        applyAdventureModeToPlayer(player);
        // ChipLib にチップ使用を許可
        ChipPlugin chipPlugin = getChipPlugin();
        if (chipPlugin != null) chipPlugin.allowPlayer(player.getUniqueId());
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
        restorePlayerState(player);
        casinoPlayers.remove(player.getUniqueId());
        // ChipLib のチップ使用許可を解除
        ChipPlugin chipPlugin = getChipPlugin();
        if (chipPlugin != null) chipPlugin.disallowPlayer(player.getUniqueId());
        restoreKeepInventoryIfEmpty();
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
        restorePlayerState(player);
        casinoPlayers.remove(player.getUniqueId());
        ChipPlugin chipPlugin = getChipPlugin();
        if (chipPlugin != null) chipPlugin.disallowPlayer(player.getUniqueId());
        restoreKeepInventoryIfEmpty();
        saveData();
    }

    /**
     * プレイヤーのゲームモードを復元し、カジノシザースを回収する。
     *
     * <p>{@link #removePlayerFromCasino(Player)} と {@link #handlePlayerDisconnect(Player)} の
     * 共通処理を統合したプライベートメソッド。
     *
     * @param player 対象プレイヤー
     */
    private void restorePlayerState(Player player) {
        GameMode savedMode = savedGameModes.remove(player.getUniqueId());
        if (savedMode != null) {
            player.setGameMode(savedMode);
        }
        shearsHelper.removeCasinoShears(player);
    }

    /**
     * 必要に応じて keepInventory の現在値を保存し、ON に設定する。
     *
     * <p>最初のプレイヤーがカジノに参加する際、まだワールドが保存されていない場合のみ実行する。
     *
     * @param world 対象ワールド
     */
    private void saveKeepInventoryIfNeeded(World world) {
        if (casinoPlayers.isEmpty() && savedWorldName == null) {
            savedWorldName = world.getName();
            Boolean current = world.getGameRuleValue(GameRule.KEEP_INVENTORY);
            savedKeepInventory = current != null && current;
            world.setGameRule(GameRule.KEEP_INVENTORY, true);
        }
    }

    /**
     * カジノプレイヤーが空になった場合に keepInventory を復元する。
     *
     * <p>最後のプレイヤーが退出または切断した際に呼び出される。
     */
    private void restoreKeepInventoryIfEmpty() {
        if (casinoPlayers.isEmpty() && savedWorldName != null) {
            World world = Bukkit.getWorld(savedWorldName);
            if (world != null) {
                world.setGameRule(GameRule.KEEP_INVENTORY, savedKeepInventory);
            }
            savedWorldName = null;
        }
    }

    // ──────────────────────────────────────────────────────
    // セッション購入記録
    // ──────────────────────────────────────────────────────

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
        sessionPurchases.merge(playerId, amount, Long::sum);
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

    // ──────────────────────────────────────────────────────
    // ランキング
    // ──────────────────────────────────────────────────────

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
        RankingManager rm = getRankingManager();
        if (rm != null) {
            rm.updateRanking("casino", playerId, netResult);
        }
    }

    /**
     * 累計損益ランキングを降順にソートして取得する。
     *
     * @param limit 最大取得件数
     * @return 損益の降順でソートされたエントリリスト
     */
    public List<Map.Entry<UUID, Long>> getSortedRanking(int limit) {
        RankingManager rm = getRankingManager();
        if (rm != null) {
            return rm.getSortedRanking("casino", limit);
        }
        return Collections.emptyList();
    }

    /**
     * ランキングと全プレイヤー統計をリセットする。
     */
    public void resetRanking() {
        RankingManager rm = getRankingManager();
        if (rm != null) {
            rm.resetRanking("casino");
        }
        playerStats.clear();
        saveData();
    }

    // ──────────────────────────────────────────────────────
    // プレイヤー統計
    // ──────────────────────────────────────────────────────

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

    // ──────────────────────────────────────────────────────
    // ゲームモード管理
    // ──────────────────────────────────────────────────────

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
            shearsHelper.giveCasinoShears(p);
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

        if (savedWorldName != null) {
            World world = Bukkit.getWorld(savedWorldName);
            if (world != null) {
                world.setGameRule(GameRule.KEEP_INVENTORY, savedKeepInventory);
            }
            savedWorldName = null;
        }
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
        shearsHelper.giveCasinoShears(player);
    }

    // ──────────────────────────────────────────────────────
    // 換金処理
    // ──────────────────────────────────────────────────────

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
                shearsHelper.removeCasinoShears(player);
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
        shearsHelper.removeCasinoShears(player);
        saveData();
    }

    /**
     * 個別プレイヤーのチップを換金し、結果を通知する。
     *
     * <p>
     * チップの合計額を Vault 経済に預入し、損益結果をランキングに記録する。
     * 購入額・換金額がともに 0 の場合は何もしない。
     *
     * <p>二重換金防止のため、購入額の取得は {@code sessionPurchases} からの
     * アトミックな {@code remove} で行い、一度換金したセッションデータは即座に削除される。
     *
     * @param player      対象プレイヤー
     * @param chipManager チップ管理マネージャ
     * @param economy     Vault 経済インスタンス
     */
    private void cashoutPlayer(Player player, ChipManager chipManager, Economy economy) {
        long totalValue = chipManager.calculateTotalValue(player);
        // アトミックに購入額を取得 & 削除 — 二重換金を防止
        Long purchasedObj = sessionPurchases.remove(player.getUniqueId());
        long purchased = purchasedObj != null ? purchasedObj : 0L;

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

        CashoutMessageFormatter.sendCashoutMessage(player, totalValue, purchased, netResult, breakdown);
    }

    // ──────────────────────────────────────────────────────
    // データ永続化
    // ──────────────────────────────────────────────────────

    /**
     * {@code data.yml} からデータを読み込む。
     *
     * <p>
     * ファイルが存在しない場合は新規作成する。
     * カジノ状態・ランキング・購入記録・ゲームモード保存データを復元する。
     */
    private void loadData() {
        FileConfiguration dataConfig = dataStore.getDataConfig();

        // ランタイム状態の読み込み（runtime セクション優先、後方互換でルートも確認）
        ConfigurationSection runtime = dataConfig.getConfigurationSection("runtime");
        ConfigurationSection source = runtime != null ? runtime : dataConfig;

        List<String> playerUuids = source.getStringList("casino-players");
        for (String uuidStr : playerUuids) {
            try {
                casinoPlayers.add(UUID.fromString(uuidStr));
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("無効なUUID (casino-players): " + uuidStr);
            }
        }
        dataStore.loadUuidLongMap(source, "session-purchases", sessionPurchases);
        savedKeepInventory = source.getBoolean("saved-keep-inventory", false);
        savedWorldName = source.getString("saved-world-name", null);
        dataStore.loadGameModes(source, savedGameModes);

        // ランキングの読み込み（RankingManager に移行済み。旧データの互換読み込み）
        migrateOldRankingData(dataConfig);

        // プレイヤー統計の読み込み
        dataStore.loadPlayerStats(playerStats);
    }

    /**
     * 現在の状態を {@code data.yml} に保存する（非同期）。
     * 各状態変更時に呼び出される。
     */
    public void saveData() {
        saveData(true);
    }

    /**
     * 現在の状態を {@code data.yml} に保存する。
     *
     * <p>YamlConfiguration の構築はメインスレッドで行い、
     * ファイルへの書き込みのみを非同期で実行する。
     *
     * @param async true の場合非同期でファイルに保存し、false の場合同期で保存する。
     */
    public synchronized void saveData(boolean async) {
        dataStore.save(async, casinoPlayers, sessionPurchases, savedKeepInventory,
                savedWorldName, savedGameModes, playerStats);
    }

    /**
     * ChipLib プラグインインスタンスを取得するヘルパー。
     *
     * @return ChipPlugin インスタンス。未ロードの場合は null
     */
    private ChipPlugin getChipPlugin() {
        try {
            org.bukkit.plugin.Plugin p = Bukkit.getPluginManager().getPlugin("ChipLib");
            return (p instanceof ChipPlugin) ? (ChipPlugin) p : null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * RankingManager を ChipLib から取得するヘルパー。
     *
     * @return RankingManager インスタンス。未ロードの場合は null
     */
    private RankingManager getRankingManager() {
        ChipPlugin chipPlugin = getChipPlugin();
        return chipPlugin != null ? chipPlugin.getRankingManager() : null;
    }

    /**
     * 旧 data.yml のランキングデータを RankingManager に移行する。
     *
     * <p>旧形式で保存されたランキングデータが存在する場合、
     * RankingManager に一括登録して旧データを削除する。
     *
     * @param dataConfig データ設定ファイル
     */
    private void migrateOldRankingData(FileConfiguration dataConfig) {
        ConfigurationSection rankingSection = dataConfig.getConfigurationSection("ranking");
        if (rankingSection == null) return;

        RankingManager rm = getRankingManager();
        if (rm == null) return;

        // 旧データが空でない場合のみ移行
        boolean hasMigrated = false;
        for (String key : rankingSection.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                long value = rankingSection.getLong(key);
                // RankingManager に既存データがなければ移行
                if (rm.getRankingData("casino").getOrDefault(uuid, 0L) == 0L && value != 0) {
                    rm.updateRanking("casino", uuid, value);
                    hasMigrated = true;
                }
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("無効なUUID (ranking migration): " + key);
            }
        }
        if (hasMigrated) {
            plugin.getLogger().info("旧ランキングデータを RankingManager に移行しました。");
        }
    }
}
