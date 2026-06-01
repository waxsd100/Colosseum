package io.wax100.arenaCore.payout;

/**
 * 天引き分配計算ロジック。
 *
 * <p>ベッタープール（観客の総ベット額）を以下のように分配する:
 * <ol>
 *   <li>敗者闘技者還元（loserShare）</li>
 *   <li>勝者闘技者還元（winnerShare）</li>
 *   <li>運営手数料（houseFeeRate）→ ジャックポット積立</li>
 *   <li>残り → 観客配当プール（勝利ベッターにオッズ按分）</li>
 * </ol>
 *
 * <p>各分配額は {@link Math#floor} で切り捨て、端数は観客配当プールに合流する。
 */
public final class PayoutDistributor {

    /**
     * 分配結果を保持するレコード。
     *
     * @param loserFighterTotal  敗者闘技者への総額
     * @param winnerFighterTotal 勝者闘技者への総額
     * @param houseFee           運営手数料（→ジャックポット）
     * @param bettorPayoutPool   観客配当プール
     */
    public record DistributionResult(
            long loserFighterTotal,
            long winnerFighterTotal,
            long houseFee,
            long bettorPayoutPool
    ) {}

    /**
     * ベッタープールを天引き分配する。
     *
     * <p>各分配額は {@code Math.floor} で切り捨て、端数は観客配当プールに合流する。
     * totalPool が 0 の場合は全て 0 のゼロガード結果を返す。
     *
     * @param totalPool    ベッタープール総額（観客の総ベット額）
     * @param loserShare   敗者闘技者還元率（例: 0.01 = 1%）
     * @param winnerShare  勝者闘技者還元率（例: 0.10 = 10%）
     * @param houseFeeRate 運営手数料率（例: 0.05 = 5%）
     * @return 分配結果
     */
    public static DistributionResult calculate(long totalPool,
                                         double loserShare,
                                         double winnerShare,
                                         double houseFeeRate) {
        if (totalPool <= 0) {
            return new DistributionResult(0, 0, 0, 0);  // ゼロガード
        }
        // すべて Math.floor で切り捨て
        long loser  = (long) Math.floor(totalPool * loserShare);
        long winner = (long) Math.floor(totalPool * winnerShare);
        long house  = (long) Math.floor(totalPool * houseFeeRate);
        // 端数は観客配当プールに合流
        long bettor = Math.max(0, totalPool - loser - winner - house);
        return new DistributionResult(loser, winner, house, bettor);
    }
}
