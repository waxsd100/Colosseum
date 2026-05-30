package io.wax100.arenaCore.model;

import org.bukkit.Material;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ArenaSession: 闘技場セッションのデータモデル")
class ArenaSessionTest {

    private static final String SESSION_NAME = "TestArena";
    private static final String TEAM_RED = "Red";
    private static final String TEAM_BLUE = "Blue";
    private static final List<String> TWO_TEAMS = List.of(TEAM_RED, TEAM_BLUE);

    private ArenaSession session;

    @BeforeEach
    void setUp() {
        session = new ArenaSession(SESSION_NAME, TWO_TEAMS);
    }

    // ========================================================================
    // コンストラクタ
    // ========================================================================

    @Nested
    @DisplayName("コンストラクタ")
    class ConstructorTest {

        @Test
        @DisplayName("セッション名が正しく設定される")
        void name_isSetCorrectly() {
            assertEquals(SESSION_NAME, session.getName());
        }

        @Test
        @DisplayName("初期状態がSETUPである")
        void state_isSetup() {
            assertEquals(ArenaState.SETUP, session.getState());
        }

        @Test
        @DisplayName("チーム名リストが正しく保持される")
        void teamNames_arePreserved() {
            assertEquals(TWO_TEAMS, session.getTeamNames());
        }

        @Test
        @DisplayName("チーム名リストは変更不可である")
        void teamNames_areUnmodifiable() {
            List<String> names = session.getTeamNames();
            assertThrows(UnsupportedOperationException.class, () -> names.add("Green"));
        }

        @Test
        @DisplayName("各チームのメンバーリストが空で初期化される")
        void teams_areInitializedEmpty() {
            for (String team : TWO_TEAMS) {
                assertTrue(session.getTeamMembers(team).isEmpty());
                assertEquals(0, session.getTeamSize(team));
            }
        }

        @Test
        @DisplayName("各チームのスコアが0で初期化される")
        void scores_areInitializedToZero() {
            for (String team : TWO_TEAMS) {
                assertEquals(0, session.getScore(team));
            }
        }

        @Test
        @DisplayName("参加費プールが0で初期化される")
        void entryFeePool_isZero() {
            assertEquals(0L, session.getEntryFeePool());
        }

        @Test
        @DisplayName("賭けマップが空で初期化される")
        void bets_areEmpty() {
            assertTrue(session.getBets().isEmpty());
        }

        @Test
        @DisplayName("勝利チームがnullで初期化される")
        void winningTeam_isNull() {
            assertNull(session.getWinningTeam());
        }

        @Test
        @DisplayName("空のチーム名リストでIllegalArgumentExceptionが発生する")
        void emptyTeamList_throwsIllegalArgumentException() {
            // 改善: コンストラクタのバリデーション強化に合わせてテストを更新
            assertThrows(IllegalArgumentException.class,
                    () -> new ArenaSession("Empty", Collections.emptyList()));
        }
    }

    // ========================================================================
    // チーム管理
    // ========================================================================

    @Nested
    @DisplayName("hasTeam - チーム存在確認")
    class HasTeamTest {

        @Test
        @DisplayName("登録済みのチーム名でtrueを返す")
        void existingTeam_returnsTrue() {
            assertTrue(session.hasTeam(TEAM_RED));
            assertTrue(session.hasTeam(TEAM_BLUE));
        }

        @Test
        @DisplayName("未登録のチーム名でfalseを返す")
        void unknownTeam_returnsFalse() {
            assertFalse(session.hasTeam("Green"));
        }

        @Test
        @DisplayName("大文字小文字が異なるとfalseを返す")
        void caseSensitive_returnsFalse() {
            assertFalse(session.hasTeam("red"));
            assertFalse(session.hasTeam("RED"));
        }
    }

    @Nested
    @DisplayName("addTeamMember - チームメンバー追加")
    class AddTeamMemberTest {

        @Test
        @DisplayName("存在するチームにメンバーを追加するとtrueを返す")
        void validTeam_returnsTrue() {
            UUID player = UUID.randomUUID();
            assertTrue(session.addTeamMember(TEAM_RED, player));
        }

        @Test
        @DisplayName("追加後にメンバーリストに含まれる")
        void memberIsInList_afterAdd() {
            UUID player = UUID.randomUUID();
            session.addTeamMember(TEAM_RED, player);
            assertTrue(session.getTeamMembers(TEAM_RED).contains(player));
        }

        @Test
        @DisplayName("存在しないチームに追加するとfalseを返す")
        void unknownTeam_returnsFalse() {
            UUID player = UUID.randomUUID();
            assertFalse(session.addTeamMember("Green", player));
        }

        @Test
        @DisplayName("既に別チームの戦闘員であるプレイヤーを追加するとfalseを返す")
        void playerAlreadyInOtherTeam_returnsFalse() {
            UUID player = UUID.randomUUID();
            session.addTeamMember(TEAM_RED, player);
            assertFalse(session.addTeamMember(TEAM_BLUE, player));
        }

        @Test
        @DisplayName("既に同じチームの戦闘員であるプレイヤーを追加するとfalseを返す")
        void playerAlreadyInSameTeam_returnsFalse() {
            UUID player = UUID.randomUUID();
            session.addTeamMember(TEAM_RED, player);
            assertFalse(session.addTeamMember(TEAM_RED, player));
        }

        @Test
        @DisplayName("複数プレイヤーを同一チームに追加できる")
        void multiplePlayersCanJoinSameTeam() {
            UUID p1 = UUID.randomUUID();
            UUID p2 = UUID.randomUUID();
            UUID p3 = UUID.randomUUID();
            assertTrue(session.addTeamMember(TEAM_RED, p1));
            assertTrue(session.addTeamMember(TEAM_RED, p2));
            assertTrue(session.addTeamMember(TEAM_RED, p3));
            assertEquals(3, session.getTeamSize(TEAM_RED));
        }
    }

    @Nested
    @DisplayName("getTeamMembers - チームメンバー取得")
    class GetTeamMembersTest {

        @Test
        @DisplayName("存在するチームのメンバーリストを返す")
        void existingTeam_returnsList() {
            UUID player = UUID.randomUUID();
            session.addTeamMember(TEAM_RED, player);
            List<UUID> members = session.getTeamMembers(TEAM_RED);
            assertEquals(1, members.size());
            assertEquals(player, members.get(0));
        }

        @Test
        @DisplayName("返されたリストは変更不可である")
        void returnedList_isUnmodifiable() {
            session.addTeamMember(TEAM_RED, UUID.randomUUID());
            List<UUID> members = session.getTeamMembers(TEAM_RED);
            assertThrows(UnsupportedOperationException.class, () -> members.add(UUID.randomUUID()));
        }

        @Test
        @DisplayName("存在しないチーム名で空リストを返す")
        void unknownTeam_returnsEmptyList() {
            List<UUID> members = session.getTeamMembers("Green");
            assertNotNull(members);
            assertTrue(members.isEmpty());
        }
    }

    @Nested
    @DisplayName("getTeamSize - チームサイズ取得")
    class GetTeamSizeTest {

        @Test
        @DisplayName("メンバーがいないチームは0を返す")
        void emptyTeam_returnsZero() {
            assertEquals(0, session.getTeamSize(TEAM_RED));
        }

        @Test
        @DisplayName("メンバー追加後に正しいサイズを返す")
        void afterAdds_returnsCorrectSize() {
            session.addTeamMember(TEAM_RED, UUID.randomUUID());
            session.addTeamMember(TEAM_RED, UUID.randomUUID());
            assertEquals(2, session.getTeamSize(TEAM_RED));
        }

        @Test
        @DisplayName("存在しないチームは0を返す")
        void unknownTeam_returnsZero() {
            assertEquals(0, session.getTeamSize("Green"));
        }
    }

    // ========================================================================
    // 戦闘員判定
    // ========================================================================

    @Nested
    @DisplayName("isFighter - 戦闘員判定")
    class IsFighterTest {

        @Test
        @DisplayName("どのチームにも属さないプレイヤーはfalseを返す")
        void nonMember_returnsFalse() {
            assertFalse(session.isFighter(UUID.randomUUID()));
        }

        @Test
        @DisplayName("いずれかのチームに属するプレイヤーはtrueを返す")
        void member_returnsTrue() {
            UUID player = UUID.randomUUID();
            session.addTeamMember(TEAM_BLUE, player);
            assertTrue(session.isFighter(player));
        }

        @Test
        @DisplayName("複数チームにメンバーがいても全チーム横断で検索する")
        void searchesAcrossAllTeams() {
            UUID redPlayer = UUID.randomUUID();
            UUID bluePlayer = UUID.randomUUID();
            session.addTeamMember(TEAM_RED, redPlayer);
            session.addTeamMember(TEAM_BLUE, bluePlayer);
            assertTrue(session.isFighter(redPlayer));
            assertTrue(session.isFighter(bluePlayer));
            assertFalse(session.isFighter(UUID.randomUUID()));
        }
    }

    @Nested
    @DisplayName("getPlayerTeam - プレイヤー所属チーム取得")
    class GetPlayerTeamTest {

        @Test
        @DisplayName("登録済みプレイヤーの所属チーム名を返す")
        void registeredPlayer_returnsTeamName() {
            UUID player = UUID.randomUUID();
            session.addTeamMember(TEAM_RED, player);
            assertEquals(TEAM_RED, session.getPlayerTeam(player));
        }

        @Test
        @DisplayName("未登録プレイヤーにはnullを返す")
        void unregisteredPlayer_returnsNull() {
            assertNull(session.getPlayerTeam(UUID.randomUUID()));
        }

        @Test
        @DisplayName("異なるチームのプレイヤーをそれぞれ正しく返す")
        void multipleTeams_returnsCorrectTeam() {
            UUID redPlayer = UUID.randomUUID();
            UUID bluePlayer = UUID.randomUUID();
            session.addTeamMember(TEAM_RED, redPlayer);
            session.addTeamMember(TEAM_BLUE, bluePlayer);
            assertEquals(TEAM_RED, session.getPlayerTeam(redPlayer));
            assertEquals(TEAM_BLUE, session.getPlayerTeam(bluePlayer));
        }
    }

    // ========================================================================
    // 賭け管理
    // ========================================================================

    @Nested
    @DisplayName("addOrUpdateBet - 賭け追加・更新")
    class AddOrUpdateBetTest {

        @Test
        @DisplayName("新規プレイヤーの賭けを作成する")
        void newPlayer_createsBet() {
            UUID player = UUID.randomUUID();
            session.addOrUpdateBet(player, TEAM_RED, 100L);

            Bet bet = session.getBet(player);
            assertNotNull(bet);
            assertEquals(player, bet.getPlayerId());
            assertEquals(TEAM_RED, bet.getTeamName());
            assertEquals(100L, bet.getAmount());
        }

        @Test
        @DisplayName("同一チームへの追加賭けは金額が加算される")
        void sameTeam_accumulatesAmount() {
            UUID player = UUID.randomUUID();
            session.addOrUpdateBet(player, TEAM_RED, 100L);
            session.addOrUpdateBet(player, TEAM_RED, 50L);

            Bet bet = session.getBet(player);
            assertEquals(150L, bet.getAmount());
            assertEquals(TEAM_RED, bet.getTeamName());
        }

        @Test
        @DisplayName("異なるチームへの賭けは新しい賭けに置き換わる")
        void differentTeam_replacesBet() {
            UUID player = UUID.randomUUID();
            session.addOrUpdateBet(player, TEAM_RED, 100L);
            session.addOrUpdateBet(player, TEAM_BLUE, 200L);

            Bet bet = session.getBet(player);
            assertEquals(TEAM_BLUE, bet.getTeamName());
            assertEquals(200L, bet.getAmount());
        }

        @Test
        @DisplayName("異なるチームへの置き換え後に再び同一チームへ加算できる")
        void replaceAndThenAccumulate() {
            UUID player = UUID.randomUUID();
            session.addOrUpdateBet(player, TEAM_RED, 100L);
            session.addOrUpdateBet(player, TEAM_BLUE, 200L);
            session.addOrUpdateBet(player, TEAM_BLUE, 50L);

            Bet bet = session.getBet(player);
            assertEquals(TEAM_BLUE, bet.getTeamName());
            assertEquals(250L, bet.getAmount());
        }

        @Test
        @DisplayName("複数プレイヤーが独立して賭けを持てる")
        void multiplePlayers_independentBets() {
            UUID p1 = UUID.randomUUID();
            UUID p2 = UUID.randomUUID();
            session.addOrUpdateBet(p1, TEAM_RED, 100L);
            session.addOrUpdateBet(p2, TEAM_BLUE, 200L);

            assertEquals(100L, session.getBet(p1).getAmount());
            assertEquals(200L, session.getBet(p2).getAmount());
        }
    }

    @Nested
    @DisplayName("getBets - 全賭け情報取得")
    class GetBetsTest {

        @Test
        @DisplayName("賭けがない場合は空マップを返す")
        void noBets_returnsEmptyMap() {
            assertTrue(session.getBets().isEmpty());
        }

        @Test
        @DisplayName("返されたマップは変更不可である")
        void returnedMap_isUnmodifiable() {
            session.addOrUpdateBet(UUID.randomUUID(), TEAM_RED, 100L);
            Map<UUID, Bet> bets = session.getBets();
            assertThrows(UnsupportedOperationException.class,
                    () -> bets.put(UUID.randomUUID(), new Bet(UUID.randomUUID(), TEAM_RED, 50L)));
        }
    }

    @Nested
    @DisplayName("getBet - プレイヤーの賭け取得")
    class GetBetTest {

        @Test
        @DisplayName("賭けが存在しないプレイヤーにはnullを返す")
        void noBet_returnsNull() {
            assertNull(session.getBet(UUID.randomUUID()));
        }

        @Test
        @DisplayName("賭けが存在するプレイヤーのBetオブジェクトを返す")
        void existingBet_returnsBet() {
            UUID player = UUID.randomUUID();
            session.addOrUpdateBet(player, TEAM_RED, 500L);
            Bet bet = session.getBet(player);
            assertNotNull(bet);
            assertEquals(500L, bet.getAmount());
        }
    }

    @Nested
    @DisplayName("getTeamPool - チーム別賭け金合計")
    class GetTeamPoolTest {

        @Test
        @DisplayName("賭けがないチームは0を返す")
        void noBets_returnsZero() {
            assertEquals(0L, session.getTeamPool(TEAM_RED));
        }

        @Test
        @DisplayName("1人の賭けがあるチームの合計を返す")
        void singleBet_returnsAmount() {
            session.addOrUpdateBet(UUID.randomUUID(), TEAM_RED, 300L);
            assertEquals(300L, session.getTeamPool(TEAM_RED));
        }

        @Test
        @DisplayName("複数人の賭けの合計を返す")
        void multipleBets_returnsSumForTeam() {
            session.addOrUpdateBet(UUID.randomUUID(), TEAM_RED, 100L);
            session.addOrUpdateBet(UUID.randomUUID(), TEAM_RED, 200L);
            session.addOrUpdateBet(UUID.randomUUID(), TEAM_BLUE, 500L);

            assertEquals(300L, session.getTeamPool(TEAM_RED));
            assertEquals(500L, session.getTeamPool(TEAM_BLUE));
        }

        @Test
        @DisplayName("存在しないチーム名は0を返す")
        void unknownTeam_returnsZero() {
            session.addOrUpdateBet(UUID.randomUUID(), TEAM_RED, 100L);
            assertEquals(0L, session.getTeamPool("Green"));
        }
    }

    @Nested
    @DisplayName("getTotalPool - 全賭け金合計")
    class GetTotalPoolTest {

        @Test
        @DisplayName("賭けがない場合は0を返す")
        void noBets_returnsZero() {
            assertEquals(0L, session.getTotalPool());
        }

        @Test
        @DisplayName("全チームの賭け金合計を返す")
        void multipleBets_returnsGrandTotal() {
            session.addOrUpdateBet(UUID.randomUUID(), TEAM_RED, 100L);
            session.addOrUpdateBet(UUID.randomUUID(), TEAM_RED, 200L);
            session.addOrUpdateBet(UUID.randomUUID(), TEAM_BLUE, 500L);

            assertEquals(800L, session.getTotalPool());
        }

        @Test
        @DisplayName("同一プレイヤーの加算も合計に反映される")
        void accumulatedBet_reflectedInTotal() {
            UUID player = UUID.randomUUID();
            session.addOrUpdateBet(player, TEAM_RED, 100L);
            session.addOrUpdateBet(player, TEAM_RED, 50L);

            assertEquals(150L, session.getTotalPool());
        }

        @Test
        @DisplayName("チームを切り替えた場合は新しい金額だけが反映される")
        void teamSwitch_onlyNewAmountCounted() {
            UUID player = UUID.randomUUID();
            session.addOrUpdateBet(player, TEAM_RED, 100L);
            session.addOrUpdateBet(player, TEAM_BLUE, 200L);

            assertEquals(200L, session.getTotalPool());
            assertEquals(0L, session.getTeamPool(TEAM_RED));
            assertEquals(200L, session.getTeamPool(TEAM_BLUE));
        }
    }

    // ========================================================================
    // 参加費
    // ========================================================================

    @Nested
    @DisplayName("参加費 (EntryFee)")
    class EntryFeeTest {

        @Test
        @DisplayName("参加費を加算できる")
        void addEntryFee_accumulates() {
            session.addEntryFee(1000L);
            assertEquals(1000L, session.getEntryFeePool());
        }

        @Test
        @DisplayName("複数回加算すると合計が返る")
        void multipleAdds_returnSum() {
            session.addEntryFee(500L);
            session.addEntryFee(300L);
            session.addEntryFee(200L);
            assertEquals(1000L, session.getEntryFeePool());
        }

        @Test
        @DisplayName("加算しなければ0のまま")
        void noAdd_remainsZero() {
            assertEquals(0L, session.getEntryFeePool());
        }
    }

    // ========================================================================
    // スコア管理
    // ========================================================================

    @Nested
    @DisplayName("スコア管理")
    class ScoreTest {

        @Test
        @DisplayName("初期スコアは0である")
        void initialScore_isZero() {
            assertEquals(0, session.getScore(TEAM_RED));
        }

        @Test
        @DisplayName("スコアを加算できる")
        void addScore_increments() {
            session.addScore(TEAM_RED, 3);
            assertEquals(3, session.getScore(TEAM_RED));
        }

        @Test
        @DisplayName("スコアを複数回加算すると合計になる")
        void multipleAdds_accumulate() {
            session.addScore(TEAM_RED, 2);
            session.addScore(TEAM_RED, 5);
            assertEquals(7, session.getScore(TEAM_RED));
        }

        @Test
        @DisplayName("チーム間でスコアが独立している")
        void teamsHaveIndependentScores() {
            session.addScore(TEAM_RED, 10);
            session.addScore(TEAM_BLUE, 3);
            assertEquals(10, session.getScore(TEAM_RED));
            assertEquals(3, session.getScore(TEAM_BLUE));
        }

        @Test
        @DisplayName("存在しないチームのスコアは0を返す")
        void unknownTeam_returnsZero() {
            assertEquals(0, session.getScore("Green"));
        }

        @Test
        @DisplayName("存在しないチームにスコア加算するとmergeで新規作成される")
        void unknownTeam_addScore_createsEntry() {
            session.addScore("Green", 5);
            assertEquals(5, session.getScore("Green"));
        }

        @Test
        @DisplayName("getScoresは変更不可マップを返す")
        void getScores_isUnmodifiable() {
            Map<String, Integer> scores = session.getScores();
            assertThrows(UnsupportedOperationException.class,
                    () -> scores.put(TEAM_RED, 999));
        }

        @Test
        @DisplayName("getScoresは全チームの現在スコアを含む")
        void getScores_containsAllTeams() {
            session.addScore(TEAM_RED, 4);
            session.addScore(TEAM_BLUE, 7);
            Map<String, Integer> scores = session.getScores();
            assertEquals(4, scores.get(TEAM_RED));
            assertEquals(7, scores.get(TEAM_BLUE));
        }
    }

    // ========================================================================
    // 状態管理
    // ========================================================================

    @Nested
    @DisplayName("状態管理 (ArenaState)")
    class StateManagementTest {

        @Test
        @DisplayName("初期状態はSETUPである")
        void initialState_isSetup() {
            assertEquals(ArenaState.SETUP, session.getState());
        }

        @Test
        @DisplayName("SETUP→BETTINGに遷移できる")
        void canTransitionToBetting() {
            session.setState(ArenaState.BETTING);
            assertEquals(ArenaState.BETTING, session.getState());
        }

        @Test
        @DisplayName("SETUP→BETTING→ACTIVEに遷移できる")
        void canTransitionToActive() {
            session.setState(ArenaState.BETTING);
            session.setState(ArenaState.ACTIVE);
            assertEquals(ArenaState.ACTIVE, session.getState());
        }

        @Test
        @DisplayName("SETUP→FINISHEDに遷移できる（キャンセル）")
        void canTransitionToFinished() {
            session.setState(ArenaState.FINISHED);
            assertEquals(ArenaState.FINISHED, session.getState());
        }

        @Test
        @DisplayName("不正な遷移はIllegalStateExceptionをスローする")
        void invalidTransition_throwsException() {
            assertThrows(IllegalStateException.class,
                    () -> session.setState(ArenaState.ACTIVE),
                    "SETUP → ACTIVE は不正遷移");
        }

        @Test
        @DisplayName("FINISHED状態からは遷移できない")
        void finishedState_cannotTransition() {
            session.setState(ArenaState.FINISHED);
            assertThrows(IllegalStateException.class,
                    () -> session.setState(ArenaState.SETUP));
        }

        @Test
        @DisplayName("状態遷移を順番に進めることができる")
        void fullLifecycleTransition() {
            assertEquals(ArenaState.SETUP, session.getState());

            session.setState(ArenaState.BETTING);
            assertEquals(ArenaState.BETTING, session.getState());

            session.setState(ArenaState.ACTIVE);
            assertEquals(ArenaState.ACTIVE, session.getState());

            session.setState(ArenaState.FINISHED);
            assertEquals(ArenaState.FINISHED, session.getState());
        }
    }

    // ========================================================================
    // 勝利チーム
    // ========================================================================

    @Nested
    @DisplayName("勝利チーム (WinningTeam)")
    class WinningTeamTest {

        @Test
        @DisplayName("初期値はnullである")
        void initialValue_isNull() {
            assertNull(session.getWinningTeam());
        }

        @Test
        @DisplayName("勝利チームを設定・取得できる")
        void setAndGet() {
            session.setWinningTeam(TEAM_RED);
            assertEquals(TEAM_RED, session.getWinningTeam());
        }

        @Test
        @DisplayName("勝利チームを上書きできる")
        void canOverwrite() {
            session.setWinningTeam(TEAM_RED);
            session.setWinningTeam(TEAM_BLUE);
            assertEquals(TEAM_BLUE, session.getWinningTeam());
        }
    }

    // ========================================================================
    // PlacedChipInfo 内部クラス
    // ========================================================================

    @Nested
    @DisplayName("PlacedChipInfo - 設置チップ情報")
    class PlacedChipInfoTest {

        @Test
        @DisplayName("コンストラクタで設定した値を正しく取得できる")
        void constructor_setsAllFields() {
            UUID player = UUID.randomUUID();
            ArenaSession.PlacedChipInfo info =
                    new ArenaSession.PlacedChipInfo(player, TEAM_RED, 500L, Material.AIR);

            assertEquals(player, info.getPlayerId());
            assertEquals(TEAM_RED, info.getTeamName());
            assertEquals(500L, info.getChipValue());
        }

        @Test
        @DisplayName("チップ額面が0でもインスタンスを生成できる")
        void zeroChipValue_isValid() {
            ArenaSession.PlacedChipInfo info =
                    new ArenaSession.PlacedChipInfo(UUID.randomUUID(), TEAM_BLUE, 0L, Material.AIR);
            assertEquals(0L, info.getChipValue());
        }

        @Test
        @DisplayName("大きな額面値を正しく保持する")
        void largeChipValue_isPreserved() {
            long largeValue = Long.MAX_VALUE;
            ArenaSession.PlacedChipInfo info =
                    new ArenaSession.PlacedChipInfo(UUID.randomUUID(), TEAM_RED, largeValue, Material.AIR);
            assertEquals(largeValue, info.getChipValue());
        }
    }

    // ========================================================================
    // 設置チップ管理（Locationはモック不要で直接テストできない部分もある）
    // ========================================================================

    @Nested
    @DisplayName("設置チップ管理 (PlacedChips)")
    class PlacedChipsManagementTest {

        @Test
        @DisplayName("初期状態では設置チップが空である")
        void initialState_isEmpty() {
            assertTrue(session.getPlacedChips().isEmpty());
        }

        @Test
        @DisplayName("getPlacedChipsは変更不可マップを返す")
        void getPlacedChips_isUnmodifiable() {
            assertThrows(UnsupportedOperationException.class,
                    () -> session.getPlacedChips().put(null, null));
        }
    }

    // ========================================================================
    // 複合シナリオ
    // ========================================================================

    @Nested
    @DisplayName("複合シナリオ")
    class IntegrationTest {

        @Test
        @DisplayName("チームメンバーと賭けが独立して管理される")
        void membersAndBets_areIndependent() {
            UUID fighter = UUID.randomUUID();
            UUID spectator = UUID.randomUUID();

            session.addTeamMember(TEAM_RED, fighter);
            session.addOrUpdateBet(spectator, TEAM_RED, 100L);

            assertTrue(session.isFighter(fighter));
            assertFalse(session.isFighter(spectator));
            assertNotNull(session.getBet(spectator));
            assertNull(session.getBet(fighter));
        }

        @Test
        @DisplayName("フルシナリオ: セットアップ→賭け→試合→終了")
        void fullScenario() {
            // Setup
            UUID redPlayer1 = UUID.randomUUID();
            UUID redPlayer2 = UUID.randomUUID();
            UUID bluePlayer1 = UUID.randomUUID();
            session.addTeamMember(TEAM_RED, redPlayer1);
            session.addTeamMember(TEAM_RED, redPlayer2);
            session.addTeamMember(TEAM_BLUE, bluePlayer1);
            session.addEntryFee(100L);
            session.addEntryFee(100L);
            session.addEntryFee(100L);

            // Transition to betting
            session.setState(ArenaState.BETTING);
            UUID spectator1 = UUID.randomUUID();
            UUID spectator2 = UUID.randomUUID();
            session.addOrUpdateBet(spectator1, TEAM_RED, 500L);
            session.addOrUpdateBet(spectator2, TEAM_BLUE, 300L);

            // Transition to active
            session.setState(ArenaState.ACTIVE);
            session.addScore(TEAM_RED, 2);
            session.addScore(TEAM_BLUE, 1);
            session.addScore(TEAM_RED, 1);

            // Finish
            session.setState(ArenaState.FINISHED);
            session.setWinningTeam(TEAM_RED);

            // Verify final state
            assertEquals(ArenaState.FINISHED, session.getState());
            assertEquals(TEAM_RED, session.getWinningTeam());
            assertEquals(2, session.getTeamSize(TEAM_RED));
            assertEquals(1, session.getTeamSize(TEAM_BLUE));
            assertEquals(300L, session.getEntryFeePool());
            assertEquals(500L, session.getTeamPool(TEAM_RED));
            assertEquals(300L, session.getTeamPool(TEAM_BLUE));
            assertEquals(800L, session.getTotalPool());
            assertEquals(3, session.getScore(TEAM_RED));
            assertEquals(1, session.getScore(TEAM_BLUE));
        }

        @Test
        @DisplayName("3チーム以上のセッションでも正しく動作する")
        void threeTeams_workCorrectly() {
            String teamGreen = "Green";
            ArenaSession threeTeamSession = new ArenaSession("ThreeTeam",
                    List.of(TEAM_RED, TEAM_BLUE, teamGreen));

            UUID p1 = UUID.randomUUID();
            UUID p2 = UUID.randomUUID();
            UUID p3 = UUID.randomUUID();
            threeTeamSession.addTeamMember(TEAM_RED, p1);
            threeTeamSession.addTeamMember(TEAM_BLUE, p2);
            threeTeamSession.addTeamMember(teamGreen, p3);

            assertEquals(3, threeTeamSession.getTeamNames().size());
            assertTrue(threeTeamSession.hasTeam(teamGreen));
            assertEquals(teamGreen, threeTeamSession.getPlayerTeam(p3));

            threeTeamSession.addOrUpdateBet(UUID.randomUUID(), teamGreen, 100L);
            assertEquals(100L, threeTeamSession.getTeamPool(teamGreen));
        }
    }
}
