package io.wax100.arenaCore.util;

import io.wax100.arenaCore.model.ArenaSession;
import io.wax100.arenaCore.model.ArenaState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link ArenaMessages#formatTeamLabel} のテスト。
 *
 * <p>プレイヤー数とMob数が独立して表示されることを検証する。
 */
@DisplayName("formatTeamLabel テスト")
class FormatTeamLabelTest {

    private ArenaSession session;

    @BeforeEach
    void setUp() {
        session = new ArenaSession("LabelArena", List.of("Warriors", "Monsters"));
    }

    @Test
    @DisplayName("非Mobチームは 'X人' 形式で表示される")
    void nonMobTeam_showsPlayerCount() {
        assertEquals("0人", ArenaMessages.formatTeamLabel(session, "Warriors"));

        session.addTeamMember("Warriors", UUID.randomUUID());
        assertEquals("1人", ArenaMessages.formatTeamLabel(session, "Warriors"));

        session.addTeamMember("Warriors", UUID.randomUUID());
        assertEquals("2人", ArenaMessages.formatTeamLabel(session, "Warriors"));
    }

    @Test
    @DisplayName("Mobチームは 'X人 / [MOB] Y体' 形式で表示される")
    void mobTeam_showsBothCounts() {
        session.markAsMobTeam("Monsters");

        // Mob追跡してACTIVE状態にする
        session.trackMob(UUID.randomUUID(), "Monsters");
        session.trackMob(UUID.randomUUID(), "Monsters");
        session.setState(ArenaState.RECRUITING);
        session.setState(ArenaState.BETTING);
        session.setState(ArenaState.CLOSED);
        session.setState(ArenaState.ACTIVE);

        String label = ArenaMessages.formatTeamLabel(session, "Monsters");
        assertEquals("0人 / [MOB] 2体", label);
    }

    @Test
    @DisplayName("Mobチームにプレイヤーが加わると両方カウントされる")
    void mobTeam_withPlayers_showsBoth() {
        session.markAsMobTeam("Monsters");
        session.addTeamMember("Monsters", UUID.randomUUID());

        // Mob追跡してACTIVE状態にする
        session.trackMob(UUID.randomUUID(), "Monsters");
        session.trackMob(UUID.randomUUID(), "Monsters");
        session.trackMob(UUID.randomUUID(), "Monsters");
        session.setState(ArenaState.RECRUITING);
        session.setState(ArenaState.BETTING);
        session.setState(ArenaState.CLOSED);
        session.setState(ArenaState.ACTIVE);

        String label = ArenaMessages.formatTeamLabel(session, "Monsters");
        assertEquals("1人 / [MOB] 3体", label);
    }

    @Test
    @DisplayName("プレイヤー追加後にラベルが即座に更新される")
    void label_updatesOnPlayerJoin() {
        assertEquals("0人", ArenaMessages.formatTeamLabel(session, "Warriors"));

        session.addTeamMember("Warriors", UUID.randomUUID());
        assertEquals("1人", ArenaMessages.formatTeamLabel(session, "Warriors"));

        session.addTeamMember("Warriors", UUID.randomUUID());
        session.addTeamMember("Warriors", UUID.randomUUID());
        assertEquals("3人", ArenaMessages.formatTeamLabel(session, "Warriors"));
    }
}
