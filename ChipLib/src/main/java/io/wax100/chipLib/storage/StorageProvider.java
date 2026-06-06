package io.wax100.chipLib.storage;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * ストレージ抽象化インターフェース。
 *
 * <p>ランキング、プレイヤー統計、セッション購入、オフライン精算の
 * データ操作を定義する。実装は YAML（デフォルト）または Redis バックエンド
 * （Write-Through）を選択可能。
 *
 * @author wax100
 */
public interface StorageProvider {

    // ── ランキング ──

    /**
     * カテゴリ別ランキングを更新する。
     *
     * @param category  カテゴリ名（例: "casino", "arena"）
     * @param playerId  プレイヤーの UUID
     * @param netResult 今回の損益（正: 勝ち、負: 負け）
     */
    void updateRanking(@NotNull String category, @NotNull UUID playerId, long netResult);

    /**
     * カテゴリ別のランキングデータを取得する。
     *
     * @param category カテゴリ名
     * @return カテゴリのランキングデータ（UUID → 累計損益）。存在しない場合は空マップ
     */
    @NotNull
    Map<UUID, Long> getRankingData(@NotNull String category);

    /**
     * カテゴリ別ランキングを降順にソートして取得する。
     *
     * @param category カテゴリ名
     * @param limit    最大取得件数
     * @return 損益の降順でソートされたエントリリスト
     */
    @NotNull
    List<Map.Entry<UUID, Long>> getSortedRanking(@NotNull String category, int limit);

    /**
     * 総合ランキングを取得する（全カテゴリの合計を降順ソート）。
     *
     * @param limit 最大取得件数
     * @return 合計損益の降順でソートされたエントリリスト
     */
    @NotNull
    List<Map.Entry<UUID, Long>> getTotalRanking(int limit);

    /**
     * カテゴリ別ランキングをリセットする。
     *
     * @param category カテゴリ名
     */
    void resetRanking(@NotNull String category);

    /**
     * 全カテゴリのランキングをリセットする。
     */
    void resetAllRankings();

    // ── プレイヤー統計 ──

    /**
     * プレイヤーの統計データを読み込む。
     *
     * @param playerId プレイヤーの UUID
     * @return プレイヤーの統計スナップショット。データが存在しない場合は {@code null}
     */
    @Nullable
    PlayerStatsSnapshot loadPlayerStats(@NotNull UUID playerId);

    /**
     * プレイヤーの統計データを保存する。
     *
     * @param playerId プレイヤーの UUID
     * @param snapshot 保存するスナップショット
     */
    void savePlayerStats(@NotNull UUID playerId, @NotNull PlayerStatsSnapshot snapshot);

    /**
     * 全プレイヤーの統計データを読み込む。
     *
     * @return プレイヤー UUID をキーとした統計スナップショットのマップ
     */
    @NotNull
    Map<UUID, PlayerStatsSnapshot> loadAllPlayerStats();

    // ── セッション購入 ──

    /**
     * セッション中のチップ購入額を加算する。
     *
     * @param playerId プレイヤーの UUID
     * @param amount   購入額
     */
    void addPurchase(@NotNull UUID playerId, long amount);

    /**
     * セッション中のチップ購入総額を取得する。
     *
     * @param playerId プレイヤーの UUID
     * @return 購入総額。購入記録がない場合は 0
     */
    long getPurchase(@NotNull UUID playerId);

    /**
     * 全プレイヤーのセッション中購入データを取得する。
     *
     * @return プレイヤー UUID → 購入総額のマップ
     */
    @NotNull
    Map<UUID, Long> getAllPurchases();

    /**
     * 全プレイヤーのセッション購入データをクリアする。
     */
    void clearPurchases();

    // ── オフライン精算 ──

    /**
     * オフラインプレイヤーの精算結果を追加する。
     *
     * @param playerId プレイヤーの UUID
     * @param bet      ベット額
     * @param won      獲得額
     */
    void addOfflineResult(@NotNull UUID playerId, long bet, long won);

    /**
     * オフラインプレイヤーの精算結果を取得し、クリアする。
     *
     * @param playerId プレイヤーの UUID
     * @return {@code long[]{bet, won}} の配列。結果がない場合は {@code null}
     */
    @Nullable
    long[] getAndClearOfflineResult(@NotNull UUID playerId);

    // ── ライフサイクル ──

    /**
     * 未保存データを永続化ストレージにフラッシュする。
     */
    void flush();

    /**
     * ストレージプロバイダーをシャットダウンする。
     *
     * <p>未保存データのフラッシュとリソースの解放を行う。
     */
    void shutdown();
}
