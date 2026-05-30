package io.wax100.arenaCore.payout;

import io.wax100.arenaCore.model.ArenaSession;
import io.wax100.arenaCore.model.Bet;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 固定オッズ方式の配当計算。
 *
 * <p>各賭けの時点で自動計算されたオッズが確定（ロック）される。
 * 最終的な配当はロックされたオッズに基づいて計算される。
 *
 * <p>オッズは賭け時点の比率から自動計算:
 * <pre>
 * オッズ = (配当プール / チーム賭け金) at 賭け時点
 * </pre>
 *
 * <p>運営リスクあり: ロックされたオッズの合計配当がプールを超える可能性がある。
 */
public class FixedOddsPayout implements PayoutStrategy {

    @Override
    public Map<UUID, Long> calculatePayouts(ArenaSession session, String winningTeam, double houseEdge) {
        Map<UUID, Long> payouts = new HashMap<>();

        for (Bet bet : session.getBets().values()) {
            if (bet.getTeamName().equals(winningTeam)) {
                double odds = bet.getLockedOdds();
                if (odds <= 0) {
                    // ロックされていない場合は現在のオッズを使用
                    odds = calculateOdds(session, winningTeam, houseEdge);
                }
                long payout = (long) (bet.getAmount() * odds);
                payouts.put(bet.getPlayerId(), payout);
            }
        }

        return payouts;
    }

    @Override
    public double calculateOdds(ArenaSession session, String teamName, double houseEdge) {
        long totalPool = session.getTotalPool();
        long teamPool = session.getTeamPool(teamName);

        if (totalPool <= 0 || teamPool <= 0) return 0.0;

        double payoutPool = totalPool * (1.0 - houseEdge);
        return payoutPool / teamPool;
    }
}
