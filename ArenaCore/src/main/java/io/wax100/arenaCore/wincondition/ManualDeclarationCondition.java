package io.wax100.arenaCore.wincondition;

import io.wax100.arenaCore.model.ArenaSession;

import java.util.Set;
import java.util.UUID;

/**
 * 管理者手動宣言の勝利条件。
 *
 * <p>死亡で自動判定は行わず、{@code /arena win <チーム>} で管理者が宣言する。
 */
public final class ManualDeclarationCondition implements WinCondition {

    @Override
    public String checkWinOnDeath(ArenaSession session, UUID deadPlayerId, Set<UUID> eliminatedPlayers) {
        // 手動モードでは自動判定しない
        return null;
    }
}
