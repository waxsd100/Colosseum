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
                // オッズが 1.0 以下の場合は最低限わずかな利益を保証（1%）
                if (odds <= 1.0) {
                    odds = 1.01;
                }
                // Math.floor で切り捨て: プールを超過しない保証
                long payout = Math.max(0L, (long) Math.floor(bet.getAmount() * odds));
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

        double clampedEdge = Math.max(0.0, Math.min(houseEdge, 0.99));
        double payoutPool = totalPool * (1.0 - clampedEdge);
        return payoutPool / teamPool;
    }
}
