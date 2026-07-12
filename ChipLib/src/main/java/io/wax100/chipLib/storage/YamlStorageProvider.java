package io.wax100.chipLib.storage;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.logging.Level;

/**
 * YAML ファイルベースの {@link StorageProvider} 実装。
 *
 * <p>デフォルトのストレージプロバイダーとして、ランキング・プレイヤー統計・
 * セッション購入・オフライン精算データを YAML ファイルに永続化する。
 *
 * <p>ファイル I/O はスレッドセーフなロックオブジェクトで保護される。
 *
 * <h2>データファイル</h2>
 * <ul>
 *   <li>{@code ranking_data.yml} — ランキングデータ</li>
 *   <li>{@code data.yml} — プレイヤー統計・セッション購入・オフライン精算</li>
 * </ul>
 *
 * @author wax100
 * @see StorageProvider
 */
public class YamlStorageProvider implements StorageProvider {

    /**
     * プラグインインスタンス
     */
    private final JavaPlugin plugin;

    /**
     * データフォルダ
     */
    private final File dataFolder;

    /**
     * ランキングデータファイル
     */
    private final File rankingFile;

    /**
     * 汎用データファイル
     */
    private final File dataFile;

    /**
     * カテゴリ別ランキングデータ (カテゴリ名 → (UUID → 累計損益))
     */
    private final Map<String, Map<UUID, Long>> rankings = new HashMap<>();

    /**
     * プレイヤー統計データ (UUID → スナップショット)
     */
    private final Map<UUID, PlayerStatsSnapshot> playerStats = new HashMap<>();

    /**
     * セッション中購入データ (UUID → 累計購入額)
     */
    private final Map<UUID, Long> purchases = new HashMap<>();

    /**
     * オフライン精算データ (UUID → {bet, won})
     */
    private final Map<UUID, long[]> offlineResults = new HashMap<>();

    /**
     * ランキングファイル I/O 用ロック
     */
    private final Object rankingFileLock = new Object();

    /**
     * データファイル I/O 用ロック
     */
    private final Object dataFileLock = new Object();

    /**
     * ランキングデータのダーティフラグ
     */
    private volatile boolean rankingDirty = false;

    /**
     * データファイルのダーティフラグ
     */
    private volatile boolean dataDirty = false;

    /**
     * コンストラクタ。
     *
     * <p>データフォルダの作成と既存データの読み込みを行う。
     *
     * @param plugin     プラグインインスタンス
     * @param dataFolder データフォルダ
     * @throws NullPointerException いずれかの引数が {@code null} の場合
     */
    public YamlStorageProvider(@NotNull JavaPlugin plugin, @NotNull File dataFolder) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.dataFolder = Objects.requireNonNull(dataFolder, "dataFolder");
        this.rankingFile = new File(dataFolder, "ranking_data.yml");
        this.dataFile = new File(dataFolder, "data.yml");
        dataFolder.mkdirs();
        loadRankingData();
        loadDataFile();
    }

    // ── ランキング ──

    @Override
    public void updateRanking(@NotNull String category, @NotNull UUID playerId, long netResult) {
        rankings.computeIfAbsent(category, k -> new LinkedHashMap<>())
                .merge(playerId, netResult, Long::sum);
        rankingDirty = true;
    }

    @Override
    @NotNull
    public Map<UUID, Long> getRankingData(@NotNull String category) {
        Map<UUID, Long> data = rankings.get(category);
        return data != null ? Collections.unmodifiableMap(data) : Collections.emptyMap();
    }

    @Override
    @NotNull
    public List<Map.Entry<UUID, Long>> getSortedRanking(@NotNull String category, int limit) {
        Map<UUID, Long> categoryRanking = rankings.get(category);
        if (categoryRanking == null || categoryRanking.isEmpty()) {
            return Collections.emptyList();
        }
        List<Map.Entry<UUID, Long>> sorted = new ArrayList<>(categoryRanking.entrySet());
        sorted.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));
        return sorted.subList(0, Math.min(sorted.size(), limit));
    }

    @Override
    @NotNull
    public List<Map.Entry<UUID, Long>> getTotalRanking(int limit) {
        Map<UUID, Long> totals = new LinkedHashMap<>();
        for (Map<UUID, Long> categoryRanking : rankings.values()) {
            for (Map.Entry<UUID, Long> entry : categoryRanking.entrySet()) {
                totals.merge(entry.getKey(), entry.getValue(), Long::sum);
            }
        }
        if (totals.isEmpty()) {
            return Collections.emptyList();
        }
        List<Map.Entry<UUID, Long>> sorted = new ArrayList<>(totals.entrySet());
        sorted.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));
        return sorted.subList(0, Math.min(sorted.size(), limit));
    }

    @Override
    public void resetRanking(@NotNull String category) {
        rankings.remove(category);
        rankingDirty = true;
        saveRankingFile();
    }

    @Override
    public void resetAllRankings() {
        rankings.clear();
        rankingDirty = true;
        saveRankingFile();
    }

    // ── プレイヤー統計 ──

    @Override
    @Nullable
    public PlayerStatsSnapshot loadPlayerStats(@NotNull UUID playerId) {
        return playerStats.get(playerId);
    }

    @Override
    public void savePlayerStats(@NotNull UUID playerId, @NotNull PlayerStatsSnapshot snapshot) {
        playerStats.put(playerId, snapshot);
        dataDirty = true;
    }

    @Override
    @NotNull
    public Map<UUID, PlayerStatsSnapshot> loadAllPlayerStats() {
        return Collections.unmodifiableMap(new HashMap<>(playerStats));
    }

    // ── セッション購入 ──

    @Override
    public void addPurchase(@NotNull UUID playerId, long amount) {
        purchases.merge(playerId, amount, Long::sum);
        dataDirty = true;
    }

    @Override
    public long getPurchase(@NotNull UUID playerId) {
        return purchases.getOrDefault(playerId, 0L);
    }

    @Override
    @NotNull
    public Map<UUID, Long> getAllPurchases() {
        return Collections.unmodifiableMap(new HashMap<>(purchases));
    }

    @Override
    public void clearPurchases() {
        purchases.clear();
        dataDirty = true;
    }

    // ── オフライン精算 ──

    @Override
    public void addOfflineResult(@NotNull UUID playerId, long bet, long won) {
        offlineResults.merge(playerId, new long[]{bet, won},
                (existing, incoming) -> new long[]{existing[0] + incoming[0], existing[1] + incoming[1]});
        dataDirty = true;
    }

    @Override
    @Nullable
    public long[] getAndClearOfflineResult(@NotNull UUID playerId) {
        long[] result = offlineResults.remove(playerId);
        if (result != null) {
            dataDirty = true;
        }
        return result;
    }

    // ── ライフサイクル ──

    @Override
    public void flush() {
        if (rankingDirty) {
            rankingDirty = false;
            saveRankingFile();
        }
        if (dataDirty) {
            dataDirty = false;
            saveDataFile();
        }
    }

    @Override
    public void shutdown() {
        flush();
    }



    // ── ランキングファイル I/O ──

    /**
     * ランキングデータを {@code ranking_data.yml} から読み込む。
     */
    private void loadRankingData() {
        if (!rankingFile.exists()) return;

        YamlConfiguration config = YamlConfiguration.loadConfiguration(rankingFile);
        rankings.clear();

        for (String category : config.getKeys(false)) {
            ConfigurationSection section = config.getConfigurationSection(category);
            if (section == null) continue;

            Map<UUID, Long> categoryRanking = new LinkedHashMap<>();
            for (String uuidStr : section.getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(uuidStr);
                    long value = section.getLong(uuidStr);
                    categoryRanking.put(uuid, value);
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("無効なUUID (ranking " + category + "): " + uuidStr);
                }
            }
            if (!categoryRanking.isEmpty()) {
                rankings.put(category, categoryRanking);
            }
        }
    }

    /**
     * ランキングデータを {@code ranking_data.yml} に保存する。
     *
     * <p>プラグインが有効な場合は非同期で、無効な場合は同期で保存する。
     */
    private void saveRankingFile() {
        YamlConfiguration config = new YamlConfiguration();
        for (Map.Entry<String, Map<UUID, Long>> categoryEntry : rankings.entrySet()) {
            String category = categoryEntry.getKey();
            for (Map.Entry<UUID, Long> playerEntry : categoryEntry.getValue().entrySet()) {
                config.set(category + "." + playerEntry.getKey().toString(), playerEntry.getValue());
            }
        }
        writeFile(rankingFile, config, rankingFileLock, "ranking_data.yml");
    }

    // ── データファイル I/O ──

    /**
     * 汎用データを {@code data.yml} から読み込む。
     */
    @SuppressWarnings("DataFlowIssue")
    private void loadDataFile() {
        if (!dataFile.exists()) return;

        YamlConfiguration config = YamlConfiguration.loadConfiguration(dataFile);

        // プレイヤー統計
        ConfigurationSection statsSection = config.getConfigurationSection("stats");
        if (statsSection != null) {
            for (String uuidStr : statsSection.getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(uuidStr);
                    ConfigurationSection ps = statsSection.getConfigurationSection(uuidStr);
                    if (ps == null) continue;
                    PlayerStatsSnapshot snapshot = new PlayerStatsSnapshot(
                            ps.getString("name", ""),
                            ps.getInt("totalSessions"),
                            ps.getLong("totalPurchases"),
                            ps.getLong("totalCashouts"),
                            ps.getLong("netProfit"),
                            ps.getInt("wins"),
                            ps.getInt("losses"),
                            ps.getInt("draws"),
                            ps.getLong("biggestWin"),
                            ps.getLong("biggestLoss"),
                            ps.getString("firstPlayed"),
                            ps.getString("lastPlayed")
                    );
                    playerStats.put(uuid, snapshot);
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("無効なUUID (stats): " + uuidStr);
                }
            }
        }

        // セッション購入
        ConfigurationSection purchasesSection = config.getConfigurationSection("purchases");
        if (purchasesSection != null) {
            for (String uuidStr : purchasesSection.getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(uuidStr);
                    long amount = purchasesSection.getLong(uuidStr);
                    purchases.put(uuid, amount);
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("無効なUUID (purchases): " + uuidStr);
                }
            }
        }

        // オフライン精算
        ConfigurationSection offlineSection = config.getConfigurationSection("offline");
        if (offlineSection != null) {
            for (String uuidStr : offlineSection.getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(uuidStr);
                    ConfigurationSection entry = offlineSection.getConfigurationSection(uuidStr);
                    if (entry == null) continue;
                    long bet = entry.getLong("bet");
                    long won = entry.getLong("won");
                    offlineResults.put(uuid, new long[]{bet, won});
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("無効なUUID (offline): " + uuidStr);
                }
            }
        }
    }

    /**
     * 汎用データを {@code data.yml} に保存する。
     */
    private void saveDataFile() {
        YamlConfiguration config = new YamlConfiguration();

        // プレイヤー統計
        for (Map.Entry<UUID, PlayerStatsSnapshot> entry : playerStats.entrySet()) {
            String prefix = "stats." + entry.getKey().toString();
            PlayerStatsSnapshot s = entry.getValue();
            config.set(prefix + ".name", s.name() != null ? s.name() : "");
            config.set(prefix + ".totalSessions", s.totalSessions());
            config.set(prefix + ".totalPurchases", s.totalPurchases());
            config.set(prefix + ".totalCashouts", s.totalCashouts());
            config.set(prefix + ".netProfit", s.netProfit());
            config.set(prefix + ".wins", s.wins());
            config.set(prefix + ".losses", s.losses());
            config.set(prefix + ".draws", s.draws());
            config.set(prefix + ".biggestWin", s.biggestWin());
            config.set(prefix + ".biggestLoss", s.biggestLoss());
            if (s.firstPlayed() != null) config.set(prefix + ".firstPlayed", s.firstPlayed());
            if (s.lastPlayed() != null) config.set(prefix + ".lastPlayed", s.lastPlayed());
        }

        // セッション購入
        for (Map.Entry<UUID, Long> entry : purchases.entrySet()) {
            config.set("purchases." + entry.getKey().toString(), entry.getValue());
        }

        // オフライン精算
        for (Map.Entry<UUID, long[]> entry : offlineResults.entrySet()) {
            String prefix = "offline." + entry.getKey().toString();
            config.set(prefix + ".bet", entry.getValue()[0]);
            config.set(prefix + ".won", entry.getValue()[1]);
        }

        writeFile(dataFile, config, dataFileLock, "data.yml");
    }

    // ── 共通ファイル書き込み ──

    /**
     * YAML 設定をファイルに書き出す。
     *
     * <p>プラグインが有効な場合は非同期で、無効な場合は同期で保存する。
     *
     * @param file     対象ファイル
     * @param config   書き出す設定
     * @param lock     ファイル I/O 用ロックオブジェクト
     * @param fileName エラーメッセージ用ファイル名
     */
    private void writeFile(File file, YamlConfiguration config, Object lock, String fileName) {
        Runnable task = () -> {
            synchronized (lock) {
                try {
                    dataFolder.mkdirs();
                    config.save(file);
                } catch (IOException e) {
                    plugin.getLogger().log(Level.SEVERE, fileName + " の保存に失敗しました", e);
                }
            }
        };

        try {
            if (plugin.isEnabled()) {
                org.bukkit.Bukkit.getScheduler().runTaskAsynchronously(plugin, task);
            } else {
                task.run();
            }
        } catch (Exception e) {
            // テスト環境や Bukkit 未初期化時はフォールバックで同期保存
            task.run();
        }
    }
}
