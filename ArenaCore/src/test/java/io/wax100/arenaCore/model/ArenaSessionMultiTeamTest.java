package io.wax100.arenaCore.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ArenaSession の3チーム以上・Mobチーム・Mob-only シナリオに特化したテスト。
 */
@DisplayName("ArenaSession: マルチチーム・Mob テスト")
class ArenaSessionMultiTeamTest {

    private static final String TEAM_A = "Alpha";
    private static final String TEAM_B = "Bravo";
    private static final String TEAM_C = "Charlie";
    private static final String TEAM_D = "Delta";
    private static final String TEAM_E = "Echo";

    // ========================================================================
    // 3チーム以上の基本操作
    // ========================================================================

    @Nested
    @DisplayName("3チーム構成")
    class ThreeTeamBasicTest {

        private ArenaSession session;

        @BeforeEach
        void setUp() {
            session = new ArenaSession("3TeamArena", List.of(TEAM_A, TEAM_B, TEAM_C));
        }

        @Test
        @DisplayName("コンストラクタで3チームが正しく初期化される")
        void constructor_threeTeams() {
            assertEquals(3, session.getTeamNames().size());
            assertTrue(session.hasTeam(TEAM_A));
            assertTrue(session.hasTeam(TEAM_B));
            assertTrue(session.hasTeam(TEAM_C));
        }

        @Test
        @DisplayName("各チームにメンバーを追加できる")
        void addMembers_toThreeTeams() {
            UUID p1 = UUID.randomUUID(), p2 = UUID.randomUUID(), p3 = UUID.randomUUID();
            session.addTeamMember(TEAM_A, p1);
            session.addTeamMember(TEAM_B, p2);
            session.addTeamMember(TEAM_C, p3);

            assertTrue(session.isFighter(p1));
            assertTrue(session.isFighter(p2));
            assertTrue(session.isFighter(p3));
            assertEquals(TEAM_A, session.getPlayerTeam(p1));
            assertEquals(TEAM_B, session.getPlayerTeam(p2));
            assertEquals(TEAM_C, session.getPlayerTeam(p3));
        }

        @Test
        @DisplayName("3チームへのベットがそれぞれ独立に管理される")
        void bets_acrossThreeTeams() {
            UUID bettor1 = UUID.randomUUID();
            UUID bettor2 = UUID.randomUUID();
            UUID bettor3 = UUID.randomUUID();

            session.addOrUpdateBet(bettor1, TEAM_A, 1000);
            session.addOrUpdateBet(bettor2, TEAM_B, 2000);
            session.addOrUpdateBet(bettor3, TEAM_C, 3000);

            assertEquals(1000, session.getTeamPool(TEAM_A));
            assertEquals(2000, session.getTeamPool(TEAM_B));
            assertEquals(3000, session.getTeamPool(TEAM_C));
            assertEquals(6000, session.getTotalPool());
        }

        @Test
        @DisplayName("同一プレイヤーが3チームすべてにベットできる")
        void samePlayer_betsOnAllThreeTeams() {
            UUID bettor = UUID.randomUUID();

            session.addOrUpdateBet(bettor, TEAM_A, 1000);
            session.addOrUpdateBet(bettor, TEAM_B, 2000);
            session.addOrUpdateBet(bettor, TEAM_C, 3000);

            assertEquals(1000, session.getBet(bettor, TEAM_A).amount());
            assertEquals(2000, session.getBet(bettor, TEAM_B).amount());
            assertEquals(3000, session.getBet(bettor, TEAM_C).amount());
            assertEquals(6000, session.getTotalPool());
        }

        @Test
        @DisplayName("各チームのスコアが独立に管理される")
        void scores_independent_perTeam() {
            session.addScore(TEAM_A, 3);
            session.addScore(TEAM_B, 1);
            session.addScore(TEAM_C, 5);

            assertEquals(3, session.getScore(TEAM_A));
            assertEquals(1, session.getScore(TEAM_B));
            assertEquals(5, session.getScore(TEAM_C));
        }

        @Test
        @DisplayName("removeTeamでチームとそのベット・プールが削除される")
        void removeTeam_cleansUpBetsAndPools() {
            UUID bettor = UUID.randomUUID();
            session.addOrUpdateBet(bettor, TEAM_A, 1000);
            session.addOrUpdateBet(bettor, TEAM_B, 2000);
            session.addOrUpdateBet(bettor, TEAM_C, 3000);

            session.removeTeam(TEAM_B);

            assertFalse(session.hasTeam(TEAM_B));
            assertEquals(2, session.getTeamNames().size());
            assertEquals(0, session.getTeamPool(TEAM_B));
            assertNull(session.getBet(bettor, TEAM_B));
            // 他チームのベットは影響されない
            assertEquals(1000, session.getBet(bettor, TEAM_A).amount());
            assertEquals(3000, session.getBet(bettor, TEAM_C).amount());
        }

        @Test
        @DisplayName("3チームでの完全ライフサイクル")
        void fullLifecycle_threeTeams() {
            UUID p1 = UUID.randomUUID(), p2 = UUID.randomUUID(), p3 = UUID.randomUUID();
            UUID bettor = UUID.randomUUID();

            // SETUP
            session.addTeamMember(TEAM_A, p1);
            session.addTeamMember(TEAM_B, p2);
            session.addTeamMember(TEAM_C, p3);

            // SETUP → RECRUITING → BETTING → CLOSED → ACTIVE
            session.setState(ArenaState.RECRUITING);
            session.setState(ArenaState.BETTING);

            session.addOrUpdateBet(bettor, TEAM_A, 5000);
            session.addOrUpdateBet(bettor, TEAM_C, 3000);

            session.setState(ArenaState.CLOSED);
            session.setState(ArenaState.ACTIVE);

            // スコア更新
            session.addScore(TEAM_A, 2);
            session.addScore(TEAM_C, 1);

            // FINISHED
            session.setState(ArenaState.FINISHED);
            session.setWinningTeam(TEAM_A);

            assertEquals(ArenaState.FINISHED, session.getState());
            assertEquals(TEAM_A, session.getWinningTeam());
            assertEquals(8000, session.getTotalPool());
        }
    }

    // ========================================================================
    // 4-5チーム
    // ========================================================================

    @Nested
    @DisplayName("4-5チーム構成")
    class FourFiveTeamTest {

        @Test
        @DisplayName("4チームでコンストラクタとベット管理")
        void fourTeams_constructAndBet() {
            ArenaSession session = new ArenaSession("4TeamArena",
                    List.of(TEAM_A, TEAM_B, TEAM_C, TEAM_D));

            assertEquals(4, session.getTeamNames().size());

            UUID b1 = UUID.randomUUID(), b2 = UUID.randomUUID();
            session.addOrUpdateBet(b1, TEAM_A, 1000);
            session.addOrUpdateBet(b1, TEAM_D, 4000);
            session.addOrUpdateBet(b2, TEAM_B, 2000);
            session.addOrUpdateBet(b2, TEAM_C, 3000);

            assertEquals(10000, session.getTotalPool());
        }

        @Test
        @DisplayName("5チームで全チームにメンバーとベット")
        void fiveTeams_fullSetup() {
            ArenaSession session = new ArenaSession("5TeamArena",
                    List.of(TEAM_A, TEAM_B, TEAM_C, TEAM_D, TEAM_E));

            assertEquals(5, session.getTeamNames().size());

            for (String team : session.getTeamNames()) {
                session.addTeamMember(team, UUID.randomUUID());
                session.addOrUpdateBet(UUID.randomUUID(), team, 1000);
            }

            assertEquals(5000, session.getTotalPool());
            for (String team : session.getTeamNames()) {
                assertEquals(1, session.getTeamSize(team));
                assertEquals(1000, session.getTeamPool(team));
            }
        }

        @Test
        @DisplayName("動的にチームを追加して5チームにする")
        void dynamicAddTeam_toFive() {
            ArenaSession session = new ArenaSession("DynArena", List.of(TEAM_A, TEAM_B));

            session.addTeam(TEAM_C);
            session.addTeam(TEAM_D);
            session.addTeam(TEAM_E);

            assertEquals(5, session.getTeamNames().size());
            assertTrue(session.hasTeam(TEAM_E));
        }
    }

    // ========================================================================
    // Mobチーム基本操作
    // ========================================================================

    @Nested
    @DisplayName("Mobチーム基本操作")
    class MobTeamBasicTest {

        private ArenaSession session;

        @BeforeEach
        void setUp() {
            session = new ArenaSession("MobArena", List.of(TEAM_A, TEAM_B));
        }

        @Test
        @DisplayName("markAsMobTeamでMobチームとしてマークされる")
        void markAsMobTeam_marksCorrectly() {
            session.markAsMobTeam(TEAM_B);

            assertFalse(session.isMobTeam(TEAM_A));
            assertTrue(session.isMobTeam(TEAM_B));
        }

        @Test
        @DisplayName("複数チームをMobチームにマークできる")
        void multipleMobTeams() {
            session.markAsMobTeam(TEAM_A);
            session.markAsMobTeam(TEAM_B);

            assertTrue(session.isMobTeam(TEAM_A));
            assertTrue(session.isMobTeam(TEAM_B));
        }

        @Test
        @DisplayName("trackMob/getMobTeamでMobを追跡できる")
        void trackMob_andGetMobTeam() {
            UUID mob1 = UUID.randomUUID();
            UUID mob2 = UUID.randomUUID();

            session.markAsMobTeam(TEAM_B);
            session.trackMob(mob1, TEAM_B);
            session.trackMob(mob2, TEAM_B);

            assertEquals(TEAM_B, session.getMobTeam(mob1));
            assertEquals(TEAM_B, session.getMobTeam(mob2));
        }

        @Test
        @DisplayName("removeMobでMobの追跡が解除される")
        void removeMob_untracks() {
            UUID mob = UUID.randomUUID();
            session.markAsMobTeam(TEAM_B);
            session.trackMob(mob, TEAM_B);

            session.removeMob(mob);

            assertNull(session.getMobTeam(mob));
        }

        @Test
        @DisplayName("hasAliveMobsで生存Mob判定")
        void hasAliveMobs_returnsCorrectly() {
            UUID mob1 = UUID.randomUUID();
            UUID mob2 = UUID.randomUUID();

            session.markAsMobTeam(TEAM_B);
            assertFalse(session.hasAliveMobs(TEAM_B));

            session.trackMob(mob1, TEAM_B);
            assertTrue(session.hasAliveMobs(TEAM_B));

            session.trackMob(mob2, TEAM_B);
            assertTrue(session.hasAliveMobs(TEAM_B));

            session.removeMob(mob1);
            assertTrue(session.hasAliveMobs(TEAM_B)); // mob2 still alive

            session.removeMob(mob2);
            assertFalse(session.hasAliveMobs(TEAM_B));
        }

        @Test
        @DisplayName("hasAliveMobs(excludeId)で特定Mobを除外して判定")
        void hasAliveMobs_withExclude() {
            UUID mob1 = UUID.randomUUID();
            UUID mob2 = UUID.randomUUID();

            session.markAsMobTeam(TEAM_B);
            session.trackMob(mob1, TEAM_B);
            session.trackMob(mob2, TEAM_B);

            // mob1を除外してもmob2が生きている
            assertTrue(session.hasAliveMobs(TEAM_B, mob1));
            // mob2を除外してもmob1が生きている
            assertTrue(session.hasAliveMobs(TEAM_B, mob2));

            // mob1を削除してからmob2を除外 → 生存Mobなし
            session.removeMob(mob1);
            assertFalse(session.hasAliveMobs(TEAM_B, mob2));
        }

        @Test
        @DisplayName("getAliveMobCountで正確なカウント")
        void getAliveMobCount_accurate() {
            session.markAsMobTeam(TEAM_B);
            assertEquals(0, session.getAliveMobCount(TEAM_B));

            UUID mob1 = UUID.randomUUID();
            UUID mob2 = UUID.randomUUID();
            UUID mob3 = UUID.randomUUID();

            session.trackMob(mob1, TEAM_B);
            session.trackMob(mob2, TEAM_B);
            session.trackMob(mob3, TEAM_B);
            assertEquals(3, session.getAliveMobCount(TEAM_B));

            session.removeMob(mob2);
            assertEquals(2, session.getAliveMobCount(TEAM_B));
        }

        @Test
        @DisplayName("markTeamEliminated/isTeamEliminatedでチーム脱落管理")
        void teamElimination_tracking() {
            assertFalse(session.isTeamEliminated(TEAM_A));
            assertFalse(session.isTeamEliminated(TEAM_B));

            session.markTeamEliminated(TEAM_A);
            assertTrue(session.isTeamEliminated(TEAM_A));
            assertFalse(session.isTeamEliminated(TEAM_B));
        }

        @Test
        @DisplayName("removeTeamでMobチームの追跡データもクリーンアップされる")
        void removeTeam_cleansMobData() {
            UUID mob = UUID.randomUUID();
            session.markAsMobTeam(TEAM_B);
            session.trackMob(mob, TEAM_B);

            session.removeTeam(TEAM_B);

            assertFalse(session.hasTeam(TEAM_B));
            assertFalse(session.isMobTeam(TEAM_B));
            assertNull(session.getMobTeam(mob));
        }

        @Test
        @DisplayName("getMobCountはMobチームでないチームに0を返す")
        void getMobCount_nonMobTeam_returnsZero() {
            assertEquals(0, session.getMobCount(TEAM_A));
        }

        @Test
        @DisplayName("getMobCountはACTIVE時にtrackedMobsから生存数を返す")
        void getMobCount_active_returnTrackedCount() {
            session.markAsMobTeam(TEAM_B);
            session.trackMob(UUID.randomUUID(), TEAM_B);
            session.trackMob(UUID.randomUUID(), TEAM_B);
            session.trackMob(UUID.randomUUID(), TEAM_A); // 別チーム

            session.setState(ArenaState.RECRUITING);
            session.setState(ArenaState.BETTING);
            session.setState(ArenaState.CLOSED);
            session.setState(ArenaState.ACTIVE);

            assertEquals(2, session.getMobCount(TEAM_B));
            assertEquals(0, session.getMobCount(TEAM_A)); // MobチームでないのでMob追跡なし
        }

        @Test
        @DisplayName("getMobCountとgetTeamSizeは独立してカウントされる")
        void getMobCount_independentOfPlayerCount() {
            session.markAsMobTeam(TEAM_B);
            session.addTeamMember(TEAM_B, UUID.randomUUID()); // プレイヤー追加
            session.trackMob(UUID.randomUUID(), TEAM_B);      // Mob追加
            session.trackMob(UUID.randomUUID(), TEAM_B);      // Mob追加

            session.setState(ArenaState.RECRUITING);
            session.setState(ArenaState.BETTING);
            session.setState(ArenaState.CLOSED);
            session.setState(ArenaState.ACTIVE);

            assertEquals(1, session.getTeamSize(TEAM_B));  // プレイヤー数
            assertEquals(2, session.getMobCount(TEAM_B));  // Mob数
            assertEquals(3, session.getEffectiveTeamSize(TEAM_B)); // 合計
        }
    }

    // ========================================================================
    // Mob-only シナリオ（全チームがMob）
    // ========================================================================

    @Nested
    @DisplayName("Mob-onlyシナリオ")
    class MobOnlyTest {

        private ArenaSession session;

        @BeforeEach
        void setUp() {
            session = new ArenaSession("MobOnlyArena", List.of(TEAM_A, TEAM_B));
            session.markAsMobTeam(TEAM_A);
            session.markAsMobTeam(TEAM_B);
        }

        @Test
        @DisplayName("全チームMobでも観客がベット可能")
        void allMobTeams_spectatorsCanBet() {
            UUID spectator1 = UUID.randomUUID();
            UUID spectator2 = UUID.randomUUID();

            session.addOrUpdateBet(spectator1, TEAM_A, 5000);
            session.addOrUpdateBet(spectator2, TEAM_B, 3000);

            assertEquals(5000, session.getTeamPool(TEAM_A));
            assertEquals(3000, session.getTeamPool(TEAM_B));
            assertEquals(8000, session.getTotalPool());
        }

        @Test
        @DisplayName("全チームMobの場合isFighterは全員false（プレイヤー闘技者なし）")
        void allMobTeams_noPlayerFighters() {
            UUID spectator = UUID.randomUUID();

            // Mobチームにプレイヤーメンバーは追加されない
            assertFalse(session.isFighter(spectator));
        }

        @Test
        @DisplayName("Mob-onlyでも勝利チーム設定とベットプールは有効")
        void mobOnly_winnerAndPoolsValid() {
            UUID spectator = UUID.randomUUID();
            UUID mob1 = UUID.randomUUID();
            UUID mob2 = UUID.randomUUID();

            session.trackMob(mob1, TEAM_A);
            session.trackMob(mob2, TEAM_B);
            session.addOrUpdateBet(spectator, TEAM_A, 10000);

            session.setState(ArenaState.RECRUITING);
            session.setState(ArenaState.BETTING);
            session.setState(ArenaState.CLOSED);
            session.setState(ArenaState.ACTIVE);

            // TeamBの全Mob死亡 → TeamA勝利
            session.removeMob(mob2);
            assertFalse(session.hasAliveMobs(TEAM_B));
            assertTrue(session.hasAliveMobs(TEAM_A));

            session.setState(ArenaState.FINISHED);
            session.setWinningTeam(TEAM_A);

            assertEquals(TEAM_A, session.getWinningTeam());
            assertEquals(10000, session.getTotalPool());
        }

        @Test
        @DisplayName("Mob-onlyで3チーム構成")
        void mobOnly_threeTeams() {
            ArenaSession s = new ArenaSession("3MobArena", List.of(TEAM_A, TEAM_B, TEAM_C));
            s.markAsMobTeam(TEAM_A);
            s.markAsMobTeam(TEAM_B);
            s.markAsMobTeam(TEAM_C);

            UUID mob1 = UUID.randomUUID(), mob2 = UUID.randomUUID(), mob3 = UUID.randomUUID();
            s.trackMob(mob1, TEAM_A);
            s.trackMob(mob2, TEAM_B);
            s.trackMob(mob3, TEAM_C);

            UUID spectator = UUID.randomUUID();
            s.addOrUpdateBet(spectator, TEAM_A, 1000);
            s.addOrUpdateBet(spectator, TEAM_B, 2000);
            s.addOrUpdateBet(spectator, TEAM_C, 3000);

            assertEquals(6000, s.getTotalPool());
            assertTrue(s.isMobTeam(TEAM_A));
            assertTrue(s.isMobTeam(TEAM_B));
            assertTrue(s.isMobTeam(TEAM_C));
        }
    }

    // ========================================================================
    // 3チーム以上 + Mob 混在
    // ========================================================================

    @Nested
    @DisplayName("マルチチーム + Mob混在")
    class MultiTeamMobMixTest {

        @Test
        @DisplayName("3チーム: プレイヤー2 + Mob1でベットと勝敗")
        void threeTeams_twoPlayerOneMob() {
            ArenaSession session = new ArenaSession("MixArena",
                    List.of(TEAM_A, TEAM_B, TEAM_C));

            UUID playerA = UUID.randomUUID();
            UUID playerB = UUID.randomUUID();
            UUID mob1 = UUID.randomUUID();

            session.addTeamMember(TEAM_A, playerA);
            session.addTeamMember(TEAM_B, playerB);
            session.markAsMobTeam(TEAM_C);
            session.trackMob(mob1, TEAM_C);

            // 観客がベット
            UUID spectator = UUID.randomUUID();
            session.addOrUpdateBet(spectator, TEAM_A, 3000);
            session.addOrUpdateBet(spectator, TEAM_B, 2000);
            session.addOrUpdateBet(spectator, TEAM_C, 5000);

            assertEquals(10000, session.getTotalPool());
            assertTrue(session.isFighter(playerA));
            assertTrue(session.isFighter(playerB));
            assertFalse(session.isFighter(spectator));
            assertTrue(session.isMobTeam(TEAM_C));
            assertFalse(session.isMobTeam(TEAM_A));

            // Mobチーム勝利
            session.setWinningTeam(TEAM_C);
            assertEquals(TEAM_C, session.getWinningTeam());
            assertEquals(5000, session.getTeamPool(TEAM_C));
        }

        @Test
        @DisplayName("4チーム: プレイヤー2 + Mob2で脱落追跡")
        void fourTeams_twoPlayerTwoMob_elimination() {
            ArenaSession session = new ArenaSession("4MixArena",
                    List.of(TEAM_A, TEAM_B, TEAM_C, TEAM_D));

            session.addTeamMember(TEAM_A, UUID.randomUUID());
            session.addTeamMember(TEAM_B, UUID.randomUUID());
            session.markAsMobTeam(TEAM_C);
            session.markAsMobTeam(TEAM_D);

            UUID mobC = UUID.randomUUID();
            UUID mobD = UUID.randomUUID();
            session.trackMob(mobC, TEAM_C);
            session.trackMob(mobD, TEAM_D);

            // TeamC脱落
            session.removeMob(mobC);
            session.markTeamEliminated(TEAM_C);

            assertTrue(session.isTeamEliminated(TEAM_C));
            assertFalse(session.isTeamEliminated(TEAM_A));
            assertFalse(session.isTeamEliminated(TEAM_B));
            assertFalse(session.isTeamEliminated(TEAM_D));

            // TeamD脱落
            session.removeMob(mobD);
            session.markTeamEliminated(TEAM_D);

            assertTrue(session.isTeamEliminated(TEAM_D));
            // プレイヤーチームはまだ生存
            assertFalse(session.isTeamEliminated(TEAM_A));
        }

        @Test
        @DisplayName("5チーム混在: プレイヤー3 + Mob2")
        void fiveTeams_threePlayerTwoMob() {
            ArenaSession session = new ArenaSession("5MixArena",
                    List.of(TEAM_A, TEAM_B, TEAM_C, TEAM_D, TEAM_E));

            session.addTeamMember(TEAM_A, UUID.randomUUID());
            session.addTeamMember(TEAM_B, UUID.randomUUID());
            session.addTeamMember(TEAM_C, UUID.randomUUID());
            session.markAsMobTeam(TEAM_D);
            session.markAsMobTeam(TEAM_E);

            session.trackMob(UUID.randomUUID(), TEAM_D);
            session.trackMob(UUID.randomUUID(), TEAM_D);
            session.trackMob(UUID.randomUUID(), TEAM_E);

            assertEquals(5, session.getTeamNames().size());
            assertEquals(2, session.getAliveMobCount(TEAM_D));
            assertEquals(1, session.getAliveMobCount(TEAM_E));
            assertFalse(session.isMobTeam(TEAM_A));
            assertTrue(session.isMobTeam(TEAM_D));
        }
    }

    // ========================================================================
    // リセット
    // ========================================================================

    @Nested
    @DisplayName("resetSession — マルチチーム・Mobデータのクリア")
    class ResetWithMultiTeamMobTest {

        @Test
        @DisplayName("3チーム+MobのリセットでMob・ベット・スコアが全クリアされる")
        void resetSession_clearsAllMultiTeamMobData() {
            ArenaSession session = new ArenaSession("ResetArena",
                    List.of(TEAM_A, TEAM_B, TEAM_C));

            UUID player = UUID.randomUUID();
            UUID mob = UUID.randomUUID();

            session.addTeamMember(TEAM_A, player);
            session.markAsMobTeam(TEAM_C);
            session.trackMob(mob, TEAM_C);
            session.addOrUpdateBet(UUID.randomUUID(), TEAM_A, 5000);
            session.addOrUpdateBet(UUID.randomUUID(), TEAM_B, 3000);
            session.addScore(TEAM_A, 5);
            session.markTeamEliminated(TEAM_B);
            session.setWinningTeam(TEAM_A);

            // FINISHED状態にしてリセット
            session.setState(ArenaState.RECRUITING);
            session.setState(ArenaState.BETTING);
            session.setState(ArenaState.CLOSED);
            session.setState(ArenaState.ACTIVE);
            session.setState(ArenaState.FINISHED);
            session.resetSession();

            // 全データがクリアされている（stateはFINISHEDのまま）
            assertEquals(ArenaState.FINISHED, session.getState());
            assertEquals(0, session.getTotalPool());
            assertTrue(session.getAllBets().isEmpty());
            assertNull(session.getWinningTeam());
            assertEquals(0, session.getScore(TEAM_A));
            assertFalse(session.isTeamEliminated(TEAM_B));
            assertFalse(session.isMobTeam(TEAM_C));
            assertTrue(session.getTrackedMobs().isEmpty());
        }
    }

    // ========================================================================
    // 3チーム以上でのベットプール計算の整合性
    // ========================================================================

    @Nested
    @DisplayName("マルチチームプール計算")
    class MultiTeamPoolCalculationTest {

        @Test
        @DisplayName("3チームのプール合計がtotalPoolに一致する")
        void poolSum_matchesTotalPool_threeTeams() {
            ArenaSession session = new ArenaSession("PoolArena",
                    List.of(TEAM_A, TEAM_B, TEAM_C));

            session.addOrUpdateBet(UUID.randomUUID(), TEAM_A, 1234);
            session.addOrUpdateBet(UUID.randomUUID(), TEAM_B, 5678);
            session.addOrUpdateBet(UUID.randomUUID(), TEAM_C, 9012);
            session.addOrUpdateBet(UUID.randomUUID(), TEAM_A, 3456);

            long sumOfPools = session.getTeamPool(TEAM_A)
                    + session.getTeamPool(TEAM_B)
                    + session.getTeamPool(TEAM_C);

            assertEquals(sumOfPools, session.getTotalPool());
            assertEquals(1234 + 3456, session.getTeamPool(TEAM_A));
            assertEquals(5678, session.getTeamPool(TEAM_B));
            assertEquals(9012, session.getTeamPool(TEAM_C));
        }

        @Test
        @DisplayName("5チームのプール合計がtotalPoolに一致する")
        void poolSum_matchesTotalPool_fiveTeams() {
            ArenaSession session = new ArenaSession("Pool5Arena",
                    List.of(TEAM_A, TEAM_B, TEAM_C, TEAM_D, TEAM_E));

            long[] amounts = {1000, 2000, 3000, 4000, 5000};
            String[] teams = {TEAM_A, TEAM_B, TEAM_C, TEAM_D, TEAM_E};
            for (int i = 0; i < 5; i++) {
                session.addOrUpdateBet(UUID.randomUUID(), teams[i], amounts[i]);
            }

            long sumOfPools = 0;
            for (String team : teams) {
                sumOfPools += session.getTeamPool(team);
            }

            assertEquals(15000, session.getTotalPool());
            assertEquals(sumOfPools, session.getTotalPool());
        }

        @Test
        @DisplayName("removeTeam後のtotalPoolが正しく再計算される")
        void removeTeam_recalculatesTotal() {
            ArenaSession session = new ArenaSession("RemovePoolArena",
                    List.of(TEAM_A, TEAM_B, TEAM_C));

            session.addOrUpdateBet(UUID.randomUUID(), TEAM_A, 1000);
            session.addOrUpdateBet(UUID.randomUUID(), TEAM_B, 2000);
            session.addOrUpdateBet(UUID.randomUUID(), TEAM_C, 3000);

            assertEquals(6000, session.getTotalPool());

            session.removeTeam(TEAM_B);

            assertEquals(4000, session.getTotalPool());
        }
    }
}
