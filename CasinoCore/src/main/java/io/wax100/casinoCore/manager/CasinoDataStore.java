package io.wax100.casinoCore.manager;

import io.wax100.casinoCore.CasinoCore;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.stream.Collectors;

/**
 * カジノデータの永続化を管理するクラス。
 *
 * <p>{@code data.yml} へのランタイム状態・ランキング・プレイヤー統計の
 * 読み込みと書き出しを担当する。ファイル I/O は専用ロックオブジェクトで
 * 同期され、非同期保存時のスレッドセーフティを保証する。
 *
 * @see CasinoManager
 */
public class CasinoDataStore {

    /**
     * プラグインインスタンス
     */
    private final CasinoCore plugin;
    /**
     * 永続化データファイル ({@code data.yml})
     */
    private final File dataFile;
    /**
     * 永続化データの設定オブジェクト
     */
    private FileConfiguration dataConfig;
    /**
     * ファイル I/O 用の専用ロックオブジェクト
     */
    private final Object fileLock = new Object();

    /**
     * コンストラクタ。
     *
     * <p>データファイル ({@code data.yml}) を初期化する。
     * ファイルが存在しない場合は新規作成する。
     *
     * @param plugin CasinoCore プラグインインスタンス
     */
    public CasinoDataStore(CasinoCore plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "data.yml");
        initDataFile();
    }

    /**
     * データファイルを初期化する。
     * ファイルが存在しない場合はディレクトリ作成後にファイルを新規作成する。
     */
    private void initDataFile() {
        if (!dataFile.exists()) {
            plugin.getDataFolder().mkdirs();
            try {
                if (!dataFile.createNewFile()) {
                    plugin.getLogger().warning("data.yml は既に存在します");
                }
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "data.ymlの作成に失敗しました", e);
            }
        }
        dataConfig = YamlConfiguration.loadConfiguration(dataFile);
    }

    /**
     * データ設定オブジェクトを取得する。
     *
     * @return {@link FileConfiguration} インスタンス
     */
    public FileConfiguration getDataConfig() {
        return dataConfig;
    }

    /**
     * UUID をキーとする Long マップを設定ファイルから読み込む。
     *
     * <p>無効な UUID キーは警告ログを出力してスキップする。
     *
     * @param parent  読み込み元のセクション
     * @param section セクション名
     * @param target  読み込み先のマップ
     */
    public void loadUuidLongMap(ConfigurationSection parent, String section, Map<UUID, Long> target) {
        ConfigurationSection sec = parent.getConfigurationSection(section);
        if (sec == null) return;
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
     * <p>既存のセクションをクリアしてから書き込む。
     *
     * @param config  書き出し先の設定オブジェクト
     * @param section セクション名
     * @param source  書き出すマップ
     */
    public void saveUuidLongMap(ConfigurationSection config, String section, Map<UUID, Long> source) {
        config.set(section, null);
        for (Map.Entry<UUID, Long> entry : source.entrySet()) {
            config.set(section + "." + entry.getKey().toString(), entry.getValue());
        }
    }

    /**
     * 保存済みゲームモードデータを設定ファイルから読み込む。
     *
     * <p>無効な UUID やゲームモード値は警告ログを出力してスキップする。
     *
     * @param parent         読み込み元のセクション
     * @param savedGameModes 読み込み先のマップ
     */
    public void loadGameModes(ConfigurationSection parent, Map<UUID, GameMode> savedGameModes) {
        ConfigurationSection sec = parent.getConfigurationSection("saved-game-modes");
        if (sec == null) return;
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
     * @param config         書き出し先の設定オブジェクト
     * @param savedGameModes 書き出すマップ
     */
    public void saveGameModes(ConfigurationSection config, Map<UUID, GameMode> savedGameModes) {
        config.set("saved-game-modes", null);
        for (Map.Entry<UUID, GameMode> entry : savedGameModes.entrySet()) {
            config.set("saved-game-modes." + entry.getKey().toString(), entry.getValue().name());
        }
    }

    /**
     * プレイヤー統計データを読み込む。
     *
     * <p>無効な UUID は警告ログを出力してスキップする。
     *
     * @param playerStats 読み込み先のマップ
     */
    public void loadPlayerStats(Map<UUID, PlayerStats> playerStats) {
        ConfigurationSection playersSection = dataConfig.getConfigurationSection("players");
        if (playersSection == null) return;
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

    /**
     * 全状態を {@code data.yml} に保存する。
     *
     * <p>{@code async} が {@code true} の場合、YamlConfiguration の構築はメインスレッドで行い、
     * ファイルへの書き込みのみを非同期で実行する。ファイル I/O は {@code fileLock} で同期される。
     *
     * @param async              true の場合非同期でファイルに保存
     * @param casinoPlayers      カジノ参加中プレイヤーセット
     * @param sessionPurchases   セッション購入記録
     * @param savedKeepInventory 保存済み keepInventory 値
     * @param savedWorldName     保存済みワールド名
     * @param savedGameModes     保存済みゲームモード
     * @param playerStats        プレイヤー統計データ
     */
    public void save(boolean async,
                     Set<UUID> casinoPlayers,
                     Map<UUID, Long> sessionPurchases,
                     boolean savedKeepInventory,
                     String savedWorldName,
                     Map<UUID, GameMode> savedGameModes,
                     Map<UUID, PlayerStats> playerStats) {
        YamlConfiguration copy = new YamlConfiguration();

        // ランタイム状態
        ConfigurationSection runtime = copy.createSection("runtime");
        runtime.set("casino-players", casinoPlayers.stream()
                .map(UUID::toString)
                .collect(Collectors.toList()));
        saveUuidLongMap(runtime, "session-purchases", sessionPurchases);
        runtime.set("saved-keep-inventory", savedKeepInventory);
        runtime.set("saved-world-name", savedWorldName);
        saveGameModes(runtime, savedGameModes);

        // プレイヤー統計
        for (Map.Entry<UUID, PlayerStats> entry : playerStats.entrySet()) {
            ConfigurationSection playerSec = copy.createSection("players." + entry.getKey().toString());
            entry.getValue().saveTo(playerSec);
        }

        if (async && Bukkit.getServer() != null && plugin.isEnabled()) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> saveToFileWithBackup(copy));
        } else {
            saveToFileWithBackup(copy);
        }
    }

    /**
     * バックアップ付きでファイルに保存する。
     *
     * <p>保存前に既存ファイルを {@code .bak} にリネームし、保存成功後に削除する。
     * 保存失敗時は {@code .bak} から復元を試みる。
     *
     * @param config 保存する設定オブジェクト
     */
    private void saveToFileWithBackup(YamlConfiguration config) {
        File backupFile = new File(dataFile.getParentFile(), dataFile.getName() + ".bak");
        synchronized (fileLock) {
            // 既存ファイルをバックアップ
            boolean backupCreated = false;
            if (dataFile.exists()) {
                // 古い .bak があれば削除
                if (backupFile.exists()) {
                    backupFile.delete();
                }
                backupCreated = dataFile.renameTo(backupFile);
                if (!backupCreated) {
                    plugin.getLogger().warning("data.yml のバックアップ作成に失敗しました。直接上書き保存します。");
                }
            }
            try {
                config.save(dataFile);
                // 保存成功 — バックアップを削除
                if (backupCreated && backupFile.exists()) {
                    backupFile.delete();
                }
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "data.ymlの保存に失敗しました", e);
                // 保存失敗 — バックアップから復元を試みる
                if (backupCreated && backupFile.exists()) {
                    if (backupFile.renameTo(dataFile)) {
                        plugin.getLogger().info("バックアップから data.yml を復元しました。");
                    } else {
                        plugin.getLogger().severe("data.yml の復元にも失敗しました。" +
                                "バックアップファイル: " + backupFile.getAbsolutePath());
                    }
                }
            }
        }
    }
}
