package io.wax100.arenaCore.payout;

import io.wax100.arenaCore.model.ArenaSession;

import java.util.Map;
import java.util.UUID;

/**
 * 配当計算のストラテジーインターフェース。
 *
 * <p>Config の {@code payout-method} で選択された方式に応じて実装が切り替わる。
 */
public interface PayoutStrategy {

    /**
     * 配当を計算する。
     *
     * @param session      セッション
     * @param winningTeam  勝利チーム名
     * @param houseEdge    手数料率 (0.0〜1.0)
     * @return 各プレイヤーの配当金額マップ (UUID → 配当額)
     */
    Map<UUID, Long> calculatePayouts(ArenaSession session, String winningTeam, double houseEdge);

    /**
     * 現在のオッズを計算する（表示用）。
     *
     * @param session   セッション
     * @param teamName  チーム名
     * @param houseEdge 手数料率
     * @return オッズ倍率
     */
    double calculateOdds(ArenaSession session, String teamName, double houseEdge);
}
