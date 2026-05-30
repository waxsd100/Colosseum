package io.wax100.arenaCore.wincondition;

import io.wax100.arenaCore.model.ArenaSession;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 全滅方式の勝利条件。
 *
 * <p>チームのメンバーが全員脱落すると、そのチームは敗北。
 * 残り1チームになったら自動的に勝利が確定する。
 */
public class LastTeamStandingCondition implements WinCondition {

    @Override
    public String checkWinOnDeath(ArenaSession session, UUID deadPlayerId, Set<UUID> eliminatedPlayers) {
        List<String> survivingTeams = new ArrayList<>();

        for (String team : session.getTeamNames()) {
            // チーム全体が全滅マークされている場合はスキップ（Mobチーム用）
            if (session.isTeamEliminated(team)) continue;

            // Mobチームの場合、生存Mobがいればまだ生き残り
            if (session.isMobTeam(team)) {
                if (session.hasAliveMobs(team)) {
                    survivingTeams.add(team);
                }
                continue;
            }

            // プレイヤーチーム: 1人でも脱落していなければ生存
            List<UUID> members = session.getTeamMembers(team);
            boolean hasAlive = false;
            for (UUID member : members) {
                if (!eliminatedPlayers.contains(member)) {
                    hasAlive = true;
                    break;
                }
            }
            if (hasAlive) survivingTeams.add(team);
        }

        // 残り1チームなら自動勝利。0チームの場合は引き分け（手動判定に委ねる）
        if (survivingTeams.size() == 1) {
            return survivingTeams.get(0);
        }
        return null;
    }
}
