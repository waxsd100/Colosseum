package io.wax100.chipLib.ranking;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * カテゴリ別ランキングを管理するクラス。
 *
 * <p>カテゴリ（"casino", "arena" など）ごとにプレイヤーの累計損益を管理し、
 * カテゴリ別ランキングおよび全カテゴリを合算した総合ランキングを提供する。
 *
 * <p>データは {@code ranking_data.yml} に永続化される。
 *
 * @author wax100
 */
public class RankingManager {

    /**
     * カテゴリ別ランキングデータ (カテゴリ名 → (UUID → 累計損益))
     */
    private final Map<String, Map<UUID, Long>> rankings = new HashMap<>();

    /**
     * プラグインインスタンス
     */
    private final JavaPlugin plugin;

    /**
     * データ永続化ファイル ({@code ranking_data.yml})
     */
    private final File dataFile;

    /**
     * ファイル I/O 用の専用ロックオブジェクト
     */
    private final Object fileLock = new Object();

    /**
     * コンストラクタ。
     *
     * <p>データファイルの初期化と読み込みを行う。
     *
     * @param plugin プラグインインスタンス
     */
    public RankingManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "ranking_data.yml");
        loadData();
    }

    /**
     * カテゴリ別の損益を更新する。
     *
     * <p>指定カテゴリにおけるプレイヤーの累計損益に {@code netResult} を加算する。
     * 更新後、データファイルに自動保存する。
     *
     * @param category カテゴリ名（例: "casino", "arena"）
     * @param playerId プレイヤーの UUID
     * @param netResult 今回の損益（正: 勝ち、負: 負け）
     */
    public void updateRanking(String category, UUID playerId, long netResult) {
        rankings.computeIfAbsent(category, k -> new LinkedHashMap<>())
                .merge(playerId, netResult, Long::sum);
        saveData();
    }

    /**
     * カテゴリ別ランキングを降順にソートして取得する。
     *
     * @param category カテゴリ名
     * @param limit    最大取得件数
     * @return 損益の降順でソートされたエントリリスト。カテゴリが存在しない場合は空リスト
     */
    public List<Map.Entry<UUID, Long>> getSortedRanking(String category, int limit) {
        Map<UUID, Long> categoryRanking = rankings.get(category);
        if (categoryRanking == null || categoryRanking.isEmpty()) {
            return Collections.emptyList();
        }
        List<Map.Entry<UUID, Long>> sorted = new ArrayList<>(categoryRanking.entrySet());
        sorted.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));
        return sorted.subList(0, Math.min(sorted.size(), limit));
    }

    /**
     * 総合ランキングを取得する（全カテゴリの合計を降順ソート）。
     *
     * <p>全カテゴリのデータをプレイヤーごとに合算し、降順にソートして返す。
     *
     * @param limit 最大取得件数
     * @return 合計損益の降順でソートされたエントリリスト
     */
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

    /**
     * カテゴリ別のランキングデータを取得する。
     *
     * @param category カテゴリ名
     * @return カテゴリのランキングデータ（UUID → 累計損益）。存在しない場合は空マップ
     */
    public Map<UUID, Long> getRankingData(String category) {
        Map<UUID, Long> data = rankings.get(category);
        return data != null ? Collections.unmodifiableMap(data) : Collections.emptyMap();
    }

    /**
     * カテゴリ別ランキングをリセットする。
     *
     * @param category カテゴリ名
     */
    public void resetRanking(String category) {
        rankings.remove(category);
        saveData();
    }

    /**
     * 全カテゴリのランキングをリセットする。
     */
    public void resetAllRankings() {
        rankings.clear();
        saveData();
    }

    /**
     * ランキングデータを {@code ranking_data.yml} に保存する。
     *
     * <p>プラグインが有効な場合は非同期で、無効な場合は同期で保存する。
     */
    public void saveData() {
        YamlConfiguration config = new YamlConfiguration();
        for (Map.Entry<String, Map<UUID, Long>> categoryEntry : rankings.entrySet()) {
            String category = categoryEntry.getKey();
            for (Map.Entry<UUID, Long> playerEntry : categoryEntry.getValue().entrySet()) {
                config.set(category + "." + playerEntry.getKey().toString(), playerEntry.getValue());
            }
        }

        try {
            if (plugin.isEnabled()) {
                org.bukkit.Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> saveToFile(config));
            } else {
                saveToFile(config);
            }
        } catch (Exception e) {
            // テスト環境や Bukkit 未初期化時はフォールバックで同期保存
            saveToFile(config);
        }
    }

    /**
     * ランキングデータを {@code ranking_data.yml} から読み込む。
     *
     * <p>ファイルが存在しない場合は何もしない。
     */
    public void loadData() {
        if (!dataFile.exists()) return;

        YamlConfiguration config = YamlConfiguration.loadConfiguration(dataFile);
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
     * データをファイルに書き出す。
     *
     * @param config 書き出す設定オブジェクト
     */
    private void saveToFile(YamlConfiguration config) {
        synchronized (fileLock) {
            try {
                plugin.getDataFolder().mkdirs();
                config.save(dataFile);
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "ranking_data.yml の保存に失敗しました", e);
            }
        }
    }
}
