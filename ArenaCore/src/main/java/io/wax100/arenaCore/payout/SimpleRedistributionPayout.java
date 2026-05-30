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
        long losingPool = totalPool - winningPool;

        if (winningPool <= 0) return payouts;

        // 負けチームの賭け金から手数料を引いた分を再分配
        double redistributable = losingPool * (1.0 - houseEdge);

        for (Bet bet : session.getBets().values()) {
            if (bet.getTeamName().equals(winningTeam)) {
                // 元金 + 再分配分の按分
                double share = (double) bet.getAmount() / winningPool;
                long payout = bet.getAmount() + (long) (redistributable * share);
                payouts.put(bet.getPlayerId(), payout);
            }
        }

        return payouts;
    }

    @Override
    public double calculateOdds(ArenaSession session, String teamName, double houseEdge) {
        long totalPool = session.getTotalPool();
        long teamPool = session.getTeamPool(teamName);
        long losingPool = totalPool - teamPool;

        if (teamPool <= 0) return 0.0;

        double redistributable = losingPool * (1.0 - houseEdge);
        return 1.0 + (redistributable / teamPool);
    }
}
