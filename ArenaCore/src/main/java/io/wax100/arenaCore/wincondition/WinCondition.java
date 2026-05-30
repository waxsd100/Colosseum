package io.wax100.arenaCore.wincondition;

import io.wax100.arenaCore.model.ArenaSession;

import java.util.Set;
import java.util.UUID;

/**
 * 勝利条件のストラテジーインターフェース。
 *
 * <p>Config の {@code win-condition} で選択された方式に応じて実装が切り替わる。
 */
public interface WinCondition {

    /**
     * 戦闘員の死亡を処理し、勝利チームが確定したかを返す。
     *
     * @param session           セッション
     * @param deadPlayerId      死亡したプレイヤー
     * @param eliminatedPlayers 既に脱落したプレイヤーのセット
     * @return 勝利チーム名。未確定の場合 {@code null}
     */
    String checkWinOnDeath(ArenaSession session, UUID deadPlayerId, Set<UUID> eliminatedPlayers);

    /**
     * 手動で勝者を宣言できるかを返す。
     * どのモードでもオーバーライドとして常に {@code true}。
     *
     * @return 手動宣言可能な場合 {@code true}
     */
    default boolean allowsManualWin() {
        return true;
    }
}
