package io.wax100.arenaCore.util;

import io.wax100.arenaCore.model.ArenaSession;
import io.wax100.arenaCore.model.ArenaState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link ArenaMessages#getNextStepHint} のテスト。
 *
 * <p>フェーズごとに適切な次コマンドヒントが返されることを検証する。
 */
@DisplayName("getNextStepHint テスト")
class NextStepHintTest {

    @Test
    @DisplayName("セッションがnullの場合、create/loadのヒントを返す")
    void nullSession_returnsCreateHint() {
        String[] hints = ArenaMessages.getNextStepHint(null);
        assertNotNull(hints);
        assertTrue(hints.length > 0);
        assertTrue(hints[0].contains("/arena create") || hints[0].contains("/arena preset load"));
    }

    @Test
    @DisplayName("SETUP状態では open/field/area のヒントを返す")
    void setup_returnsSetupHints() {
        ArenaSession session = new ArenaSession("Test", List.of("A", "B"));
        String[] hints = ArenaMessages.getNextStepHint(session);
        assertTrue(hints.length >= 2);
        String joined = String.join(" ", hints);
        assertTrue(joined.contains("/arena open"));
    }

    @Test
    @DisplayName("RECRUITING状態では lock のヒントを返す")
    void recruiting_returnsLockHint() {
        ArenaSession session = new ArenaSession("Test", List.of("A", "B"));
        session.setState(ArenaState.RECRUITING);
        String[] hints = ArenaMessages.getNextStepHint(session);
        String joined = String.join(" ", hints);
        assertTrue(joined.contains("/arena lock"));
    }

    @Test
    @DisplayName("BETTING状態では close のヒントを返す")
    void betting_returnsCloseHint() {
        ArenaSession session = new ArenaSession("Test", List.of("A", "B"));
        session.setState(ArenaState.RECRUITING);
        session.setState(ArenaState.BETTING);
        String[] hints = ArenaMessages.getNextStepHint(session);
        String joined = String.join(" ", hints);
        assertTrue(joined.contains("/arena close"));
    }

    @Test
    @DisplayName("CLOSED状態では start のヒントを返す")
    void closed_returnsStartHint() {
        ArenaSession session = new ArenaSession("Test", List.of("A", "B"));
        session.setState(ArenaState.RECRUITING);
        session.setState(ArenaState.BETTING);
        session.setState(ArenaState.CLOSED);
        String[] hints = ArenaMessages.getNextStepHint(session);
        String joined = String.join(" ", hints);
        assertTrue(joined.contains("/arena start"));
    }

    @Test
    @DisplayName("ACTIVE状態では win/cancel のヒントを返す")
    void active_returnsWinCancelHint() {
        ArenaSession session = new ArenaSession("Test", List.of("A", "B"));
        session.setState(ArenaState.RECRUITING);
        session.setState(ArenaState.BETTING);
        session.setState(ArenaState.CLOSED);
        session.setState(ArenaState.ACTIVE);
        String[] hints = ArenaMessages.getNextStepHint(session);
        String joined = String.join(" ", hints);
        assertTrue(joined.contains("/arena win"));
        assertTrue(joined.contains("/arena cancel"));
    }

    @Test
    @DisplayName("FINISHED状態では create/preset load のヒントを返す")
    void finished_returnsNewSessionHint() {
        ArenaSession session = new ArenaSession("Test", List.of("A", "B"));
        session.setState(ArenaState.RECRUITING);
        session.setState(ArenaState.BETTING);
        session.setState(ArenaState.CLOSED);
        session.setState(ArenaState.ACTIVE);
        session.setState(ArenaState.FINISHED);
        String[] hints = ArenaMessages.getNextStepHint(session);
        String joined = String.join(" ", hints);
        assertTrue(joined.contains("/arena create") || joined.contains("/arena preset load"));
    }

    @Test
    @DisplayName("全フェーズでnullや空配列が返らない")
    void allStates_neverReturnNullOrEmpty() {
        assertNotNull(ArenaMessages.getNextStepHint(null));
        assertTrue(ArenaMessages.getNextStepHint(null).length > 0);

        ArenaSession session = new ArenaSession("Test", List.of("A", "B"));
        for (ArenaState state : ArenaState.values()) {
            try {
                session.setState(state);
            } catch (IllegalStateException ignored) {
                continue; // 不正な遷移はスキップ
            }
            String[] hints = ArenaMessages.getNextStepHint(session);
            assertNotNull(hints, "State " + state + " returned null");
            assertTrue(hints.length > 0, "State " + state + " returned empty array");
        }
    }
}
