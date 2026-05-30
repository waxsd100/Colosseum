package io.wax100.arenaCore.payout;

import io.wax100.arenaCore.model.ArenaSession;
import io.wax100.arenaCore.model.Bet;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 単純再分配方式の配当計算。
 *
 * <p>負けチームの賭け金を勝ちチームに按分する。
 * 手数料は賭け金全体ではなく、再分配される利益にのみ適用する。
 *
 * <pre>
 * 勝者は自分の賭け金 + (負けチーム賭け金 × (1 - houseEdge)) の按分を受け取る
 * </pre>
 */
public class SimpleRedistributionPayout implements PayoutStrategy {

    @Override
    public Map<UUID, Long> calculatePayouts(ArenaSession session, String winningTeam, double houseEdge) {
        Map<UUID, Long> payouts = new HashMap<>();
        long totalPool = session.getTotalPool();
        long winningPool = session.getTeamPool(winningTeam);
        // losingPool が負にならないように防御（データ不整合ガード）
        long losingPool = Math.max(0L, totalPool - winningPool);

        if (winningPool <= 0) return payouts;

        // houseEdge を [0.0, 0.99] にクランプして再分配額が 0 になるのを防止
        double clampedEdge = Math.max(0.0, Math.min(houseEdge, 0.99));
        // 負けチームの賭け金から手数料を引いた分を再分配
        double redistributable = losingPool * (1.0 - clampedEdge);

        for (Bet bet : session.getBets().values()) {
            if (bet.getTeamName().equals(winningTeam)) {
                // 元金 + 再分配分の按分 (Math.floor で切り捨て)
                double share = (double) bet.getAmount() / winningPool;
                long bonus = (long) Math.floor(redistributable * share);
                // 最低でも元金を返す
                long payout = Math.max(bet.getAmount(), bet.getAmount() + bonus);
                payouts.put(bet.getPlayerId(), payout);
            }
        }

        return payouts;
    }

    @Override
    public double calculateOdds(ArenaSession session, String teamName, double houseEdge) {
        long totalPool = session.getTotalPool();
        long teamPool = session.getTeamPool(teamName);
        long losingPool = Math.max(0L, totalPool - teamPool);

        if (teamPool <= 0) return 0.0;

        double clampedEdge = Math.max(0.0, Math.min(houseEdge, 0.99));
        double redistributable = losingPool * (1.0 - clampedEdge);
        // 最低でも 1.0 (元金返却)
        return Math.max(1.0, 1.0 + (redistributable / teamPool));
    }
}
