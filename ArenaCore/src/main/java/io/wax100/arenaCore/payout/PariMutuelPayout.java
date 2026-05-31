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

        // 全員が勝利チームに賭けた場合は再分配不要 → 元金返却
        if (totalPool == winningPool) {
            for (Bet bet : session.getAllBets()) {
                if (bet.teamName().equals(winningTeam)) {
                    payouts.merge(bet.playerId(), bet.amount(), Long::sum);
                }
            }
            return payouts;
        }

        // houseEdge を [0.0, 0.99] にクランプして配当プールが 0 になるのを防止
        double clampedEdge = Math.max(0.0, Math.min(houseEdge, 0.99));
        double payoutPool = totalPool * (1.0 - clampedEdge);
        double odds = payoutPool / winningPool;

        for (Bet bet : session.getAllBets()) {
            if (bet.teamName().equals(winningTeam)) {
                // Math.floor で切り捨て: プールを超過しない保証
                long payout = Math.max(0L, (long) Math.floor(bet.amount() * odds));
                payouts.merge(bet.playerId(), payout, Long::sum);
            }
        }

        return payouts;
    }

    @Override
    public double calculateOdds(ArenaSession session, String teamName, double houseEdge) {
        long totalPool = session.getTotalPool();
        long teamPool = session.getTeamPool(teamName);

        if (totalPool <= 0 || teamPool <= 0) return 0.0;

        // 全員が同一チームに賭けた場合はオッズ 1.0（元金返却相当）
        if (totalPool == teamPool) return 1.0;

        double clampedEdge = Math.max(0.0, Math.min(houseEdge, 0.99));
        double payoutPool = totalPool * (1.0 - clampedEdge);
        return payoutPool / teamPool;
    }
}
