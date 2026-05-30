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

        return survivingTeams.size() == 1 ? survivingTeams.get(0) : null;
    }
}
