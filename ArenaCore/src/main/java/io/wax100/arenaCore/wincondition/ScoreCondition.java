package io.wax100.arenaCore.wincondition;

import io.wax100.arenaCore.model.ArenaSession;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * スコア制の勝利条件。
 *
 * <p>キルごとにチームのスコアが加算され、
 * 目標スコアに到達したチームが勝利する。
 * 目標スコアが 0 の場合は自動判定しない（{@code /arena end} で手動集計）。
 */
public class ScoreCondition implements WinCondition {

    private final int targetScore;

    /**
     * @param targetScore 勝利に必要なスコア（0の場合は手動集計）
     */
    public ScoreCondition(int targetScore) {
        this.targetScore = targetScore;
    }

    @Override
    public String checkWinOnDeath(ArenaSession session, UUID deadPlayerId, Set<UUID> eliminatedPlayers) {
        // 死亡したプレイヤーのチームを特定
        String deadTeam = session.getPlayerTeam(deadPlayerId);
        if (deadTeam == null) return null;

        // 他のチームにスコアを加算（先に全チーム加算してから判定）
        for (String team : session.getTeamNames()) {
            if (!team.equals(deadTeam)) {
                session.addScore(team, 1);
            }
        }

        // targetScore が 0 の場合は手動集計モード
        if (targetScore <= 0) return null;

        // 勝利判定: 複数チームが同時に到達した場合
        String winner = null;
        int highestScore = 0;
        int highestCount = 0;
        for (String team : session.getTeamNames()) {
            int score = session.getScore(team);
            if (score >= targetScore) {
                if (score > highestScore) {
                    highestScore = score;
                    winner = team;
                    highestCount = 1;
                } else if (score == highestScore) {
                    highestCount++;
                }
            }
        }

        // 同点で複数チームが最高スコアに到達した場合は手動判定に委ねる
        return highestCount == 1 ? winner : null;
    }
}
