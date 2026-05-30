package io.wax100.arenaCore.payout;

import io.wax100.arenaCore.model.ArenaSession;
import io.wax100.arenaCore.model.Bet;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * パリミュチュエル方式（競馬方式）の配当計算。
 *
 * <p>全賭け金をプールし、手数料を差し引いた残りを
 * 勝利チームに賭けた人で按分する。運営リスクなし。
 *
 * <pre>
 * 配当プール = 総プール × (1 - houseEdge)
 * オッズ     = 配当プール / 勝利チーム賭け金
 * 個人配当   = 個人賭け額 × オッズ
 * </pre>
 */
public class PariMutuelPayout implements PayoutStrategy {

    @Override
    public Map<UUID, Long> calculatePayouts(ArenaSession session, String winningTeam, double houseEdge) {
        Map<UUID, Long> payouts = new HashMap<>();
        long totalPool = session.getTotalPool();
        long winningPool = session.getTeamPool(winningTeam);

        if (totalPool <= 0 || winningPool <= 0) return payouts;

        double payoutPool = totalPool * (1.0 - houseEdge);
        double odds = payoutPool / winningPool;

        for (Bet bet : session.getBets().values()) {
            if (bet.getTeamName().equals(winningTeam)) {
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
