package io.wax100.chipLib.ranking;

import io.wax100.chipLib.storage.StorageProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * カテゴリ別ランキングを管理するクラス。
 *
 * <p>カテゴリ（"casino", "arena" など）ごとにプレイヤーの累計損益を管理し、
 * カテゴリ別ランキングおよび全カテゴリを合算した総合ランキングを提供する。
 *
 * <p>実際のデータ操作は {@link StorageProvider} に委譲される。
 *
 * @author wax100
 */
public class RankingManager {

    /**
     * ストレージプロバイダー
     */
    private final StorageProvider storageProvider;

    /**
     * データが変更されたがまだフラッシュされていないことを示すダーティフラグ
     */
    private volatile boolean dirty = false;

    /**
     * 自動保存タスク
     */
    private BukkitTask autoSaveTask;

    /**
     * コンストラクタ。
     *
     * @param storageProvider ストレージプロバイダー
     * @throws NullPointerException {@code storageProvider} が {@code null} の場合
     */
    public RankingManager(StorageProvider storageProvider) {
        this.storageProvider = Objects.requireNonNull(storageProvider, "storageProvider");
    }

    /**
     * カテゴリ別の損益を更新する。
     *
     * <p>指定カテゴリにおけるプレイヤーの累計損益に {@code netResult} を加算する。
     * 更新後、ダーティフラグを立て、次回の自動保存タイマーで永続化する。
     *
     * @param category カテゴリ名（例: "casino", "arena"）
     * @param playerId プレイヤーの UUID
     * @param netResult 今回の損益（正: 勝ち、負: 負け）
     */
    public void updateRanking(String category, UUID playerId, long netResult) {
        storageProvider.updateRanking(category, playerId, netResult);
        dirty = true;
    }

    /**
     * 自動保存タイマーを開始する（5秒間隔）。
     *
     * <p>ダーティフラグが立っている場合のみ {@link StorageProvider#flush()} を呼び出す。
     *
     * @param plugin プラグインインスタンス
     */
    public void startAutoSave(JavaPlugin plugin) {
        // 5秒 = 100 tick
        autoSaveTask = plugin.getServer().getScheduler()
                .runTaskTimer(plugin, () -> {
                    if (dirty) {
                        dirty = false;
                        storageProvider.flush();
                    }
                }, 100L, 100L);
    }

    /**
     * 自動保存タイマーを停止する。
     *
     * <p>ダーティフラグが立っている場合は最終フラッシュを行う。
     */
    public void stopAutoSave() {
        if (autoSaveTask != null) {
            autoSaveTask.cancel();
            autoSaveTask = null;
        }
        if (dirty) {
            dirty = false;
            storageProvider.flush();
        }
    }

    /**
     * カテゴリ別ランキングを降順にソートして取得する。
     *
     * @param category カテゴリ名
     * @param limit    最大取得件数
     * @return 損益の降順でソートされたエントリリスト。カテゴリが存在しない場合は空リスト
     */
    public List<Map.Entry<UUID, Long>> getSortedRanking(String category, int limit) {
        return storageProvider.getSortedRanking(category, limit);
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
        return storageProvider.getTotalRanking(limit);
    }

    /**
     * カテゴリ別のランキングデータを取得する。
     *
     * @param category カテゴリ名
     * @return カテゴリのランキングデータ（UUID → 累計損益）。存在しない場合は空マップ
     */
    public Map<UUID, Long> getRankingData(String category) {
        return storageProvider.getRankingData(category);
    }

    /**
     * カテゴリ別ランキングをリセットする。
     *
     * @param category カテゴリ名
     */
    public void resetRanking(String category) {
        storageProvider.resetRanking(category);
    }

    /**
     * 全カテゴリのランキングをリセットする。
     */
    public void resetAllRankings() {
        storageProvider.resetAllRankings();
    }
}
