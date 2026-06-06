package io.wax100.arenaCore.storage;

import java.util.List;
import java.util.Set;

/**
 * 地形復元ストレージの抽象インタフェース。
 *
 * <p>試合中に破壊・設置されたブロックの復元情報を保持し、
 * tick ベースのスケジューリングでエントリを取り出す機能を提供する。
 *
 * <p>実装例:
 * <ul>
 *   <li>{@link MemoryTerrainStorage} — インメモリ（デフォルト）</li>
 *   <li>{@link RedisTerrainStorage} — Redis Sorted Set</li>
 * </ul>
 *
 * <p>ストレージは一時的なデータ（エフェメラル）であり、
 * 最終的な復元保証は Schematic ペーストで行う。
 */
public interface TerrainStorageProvider {

    /**
     * ブロック変更を記録する。
     *
     * @param sessionId     セッション識別子
     * @param worldName     ワールド名
     * @param x             X 座標
     * @param y             Y 座標
     * @param z             Z 座標
     * @param blockData     {@code BlockData#getAsString()} の結果
     * @param restoreAtTick 復元予定の tick（設置ブロックは {@link Long#MAX_VALUE}）
     */
    void recordBlockChange(String sessionId, String worldName,
                           int x, int y, int z,
                           String blockData, long restoreAtTick);

    /**
     * 指定 tick 以下の復元予定エントリを取り出して返す。
     *
     * <p>返されたエントリはストレージから削除される。
     *
     * @param sessionId   セッション識別子
     * @param currentTick 現在の tick
     * @return 復元対象のエントリリスト（空の場合もある）
     */
    List<BlockRestoreEntry> pollReadyEntries(String sessionId, long currentTick);

    /**
     * 先頭から最大 count 件のエントリを取り出して返す。
     *
     * <p>返されたエントリはストレージから削除される。
     * Stage 2（高速復元）で使用する。
     *
     * @param sessionId セッション識別子
     * @param count     取り出す最大件数
     * @return 取り出されたエントリリスト（空の場合もある）
     */
    List<BlockRestoreEntry> pollBatch(String sessionId, int count);

    /**
     * 指定セッションのエントリが空かどうかを返す。
     *
     * @param sessionId セッション識別子
     * @return エントリが存在しない場合 {@code true}
     */
    boolean isEmpty(String sessionId);

    /**
     * 指定セッションのデータを全て削除する。
     *
     * @param sessionId セッション識別子
     */
    void clearSession(String sessionId);

    /**
     * 未処理のセッション ID 一覧を返す。
     *
     * <p>Redis 実装ではクラッシュ復旧時に使用する。
     * インメモリ実装では同一プロセス内のセッションを返すが、
     * クラッシュ復旧には使えない。
     *
     * @return 保留中のセッション ID セット
     */
    Set<String> getPendingSessions();

    /**
     * ストレージをシャットダウンする。
     *
     * <p>インメモリ実装では no-op。Redis 実装では接続は ChipLib が管理するため同様に no-op。
     */
    void shutdown();
}
