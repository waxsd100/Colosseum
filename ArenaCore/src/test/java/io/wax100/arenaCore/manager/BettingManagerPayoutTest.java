package io.wax100.arenaCore.manager;

import io.wax100.arenaCore.model.ArenaSession;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link BettingManager#calculatePariMutuelPayouts(ArenaSession, String, long, boolean)}
 * （パリミュチュエル配当計算 + 元返し保証）の単体テスト。
 *
 * <p>浮動小数点誤差を避けるため、オッズが2進数で正確に表現できる
 * 比率（0.5倍・1.5倍）を使用する。
 */
@DisplayName("BettingManager: パリミュチュエル配当計算（元返し保証）")
class BettingManagerPayoutTest {

    private static final UUID P1 = UUID.randomUUID();
    private static final UUID P2 = UUID.randomUUID();
    private static final UUID P3 = UUID.randomUUID();

    private ArenaSession newSession() {
        return new ArenaSession("test", List.of("A", "B"));
    }

    @Test
    @DisplayName("保証ON: オッズ1.0未満では配当がベット額まで引き上げられる")
    void 保証ON_オッズ1未満で元返しになる() {
        ArenaSession session = newSession();
        session.addOrUpdateBet(P1, "A", 80_000);
        // bettorPool 40,000 / winningPool 80,000 → オッズ 0.5倍
        Map<UUID, Long> payouts = BettingManager.calculatePariMutuelPayouts(session, "A", 40_000, true);

        assertEquals(80_000L, payouts.get(P1), "元返し保証によりベット額が下限になること");
    }

    @Test
    @DisplayName("保証OFF: オッズ1.0未満では従来どおり縮小配当")
    void 保証OFF_従来どおり縮小配当() {
        ArenaSession session = newSession();
        session.addOrUpdateBet(P1, "A", 80_000);
        Map<UUID, Long> payouts = BettingManager.calculatePariMutuelPayouts(session, "A", 40_000, false);

        assertEquals(40_000L, payouts.get(P1), "保証OFFでは floor(80000×0.5)=40000 のままであること");
    }

    @Test
    @DisplayName("保証ON: オッズ1.0以上では配当に影響しない")
    void 保証ON_オッズ1以上では影響しない() {
        ArenaSession session = newSession();
        session.addOrUpdateBet(P1, "A", 80_000);
        // bettorPool 120,000 / winningPool 80,000 → オッズ 1.5倍
        Map<UUID, Long> withGuarantee = BettingManager.calculatePariMutuelPayouts(session, "A", 120_000, true);
        Map<UUID, Long> withoutGuarantee = BettingManager.calculatePariMutuelPayouts(session, "A", 120_000, false);

        assertEquals(120_000L, withGuarantee.get(P1));
        assertEquals(withoutGuarantee.get(P1), withGuarantee.get(P1), "オッズ1.0以上では保証の有無で結果が変わらないこと");
    }

    @Test
    @DisplayName("保証ON: 複数ベッターにも個別にベット額が下限適用される")
    void 保証ON_複数ベッターに個別適用() {
        ArenaSession session = newSession();
        session.addOrUpdateBet(P1, "A", 60_000);
        session.addOrUpdateBet(P3, "A", 20_000);
        // bettorPool 40,000 / winningPool 80,000 → オッズ 0.5倍
        Map<UUID, Long> payouts = BettingManager.calculatePariMutuelPayouts(session, "A", 40_000, true);

        assertEquals(60_000L, payouts.get(P1), "P1 は floor(60000×0.5)=30000 → 元返しで 60000");
        assertEquals(20_000L, payouts.get(P3), "P3 は floor(20000×0.5)=10000 → 元返しで 20000");
    }

    @Test
    @DisplayName("保証ON: 配当プールが0でもベット額が返る")
    void 保証ON_プール0でも元返し() {
        ArenaSession session = newSession();
        session.addOrUpdateBet(P1, "A", 80_000);
        Map<UUID, Long> payouts = BettingManager.calculatePariMutuelPayouts(session, "A", 0, true);

        assertEquals(80_000L, payouts.get(P1), "プール0でも保証によりベット額が返ること");
    }

    @Test
    @DisplayName("保証OFF: 配当プールが0なら空マップ（従来挙動）")
    void 保証OFF_プール0は空マップ() {
        ArenaSession session = newSession();
        session.addOrUpdateBet(P1, "A", 80_000);
        Map<UUID, Long> payouts = BettingManager.calculatePariMutuelPayouts(session, "A", 0, false);

        assertTrue(payouts.isEmpty(), "保証OFF・プール0では従来どおり空マップであること");
    }

    @Test
    @DisplayName("勝利チームにベットがなければ保証ONでも空マップ")
    void 勝チームベットなしは空マップ() {
        ArenaSession session = newSession();
        session.addOrUpdateBet(P2, "B", 50_000);
        Map<UUID, Long> payouts = BettingManager.calculatePariMutuelPayouts(session, "A", 40_000, true);

        assertTrue(payouts.isEmpty(), "勝利チームへのベットが0なら配当対象がないこと");
    }

    @Test
    @DisplayName("負けチームへのベットは配当対象に含まれない")
    void 負けチームのベットは対象外() {
        ArenaSession session = newSession();
        session.addOrUpdateBet(P1, "A", 80_000);
        session.addOrUpdateBet(P2, "B", 80_000);
        Map<UUID, Long> payouts = BettingManager.calculatePariMutuelPayouts(session, "A", 40_000, true);

        assertEquals(1, payouts.size());
        assertTrue(payouts.containsKey(P1));
        assertFalse(payouts.containsKey(P2), "負けチームのベッターが配当マップに含まれないこと");
    }
}
