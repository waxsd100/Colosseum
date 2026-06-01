package io.wax100.arenaCore.model;

import io.wax100.arenaCore.model.DeathmatchChallenge.VoteResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("DeathmatchChallenge: デスマッチ投票モデル")
class DeathmatchChallengeTest {

    private static final String TEAM_A = "Alpha";
    private static final String TEAM_B = "Beta";

    private UUID proposer;

    @BeforeEach
    void setUp() {
        proposer = UUID.randomUUID();
    }

    /** 2チーム各N人の標準チームサイズマップを生成 */
    private Map<String, Integer> twoTeams(int sizeA, int sizeB) {
        Map<String, Integer> sizes = new HashMap<>();
        sizes.put(TEAM_A, sizeA);
        sizes.put(TEAM_B, sizeB);
        return sizes;
    }

    /** 1チームのチームサイズマップを生成 */
    private Map<String, Integer> oneTeam(String team, int size) {
        Map<String, Integer> sizes = new HashMap<>();
        sizes.put(team, size);
        return sizes;
    }

    // ========================================================================
    // コンストラクタ
    // ========================================================================

    @Nested
    @DisplayName("コンストラクタ")
    class ConstructorTest {

        @Test
        @DisplayName("提案者UUIDが正しく保持される")
        void proposer_isStored() {
            var challenge = new DeathmatchChallenge(proposer, TEAM_A, 100, 600, twoTeams(3, 3));
            assertEquals(proposer, challenge.getProposer());
        }

        @Test
        @DisplayName("提案者チーム名が正しく保持される")
        void proposerTeam_isStored() {
            var challenge = new DeathmatchChallenge(proposer, TEAM_A, 100, 600, twoTeams(3, 3));
            assertEquals(TEAM_A, challenge.getProposerTeam());
        }

        @Test
        @DisplayName("参加費とプール額が正しく保持される")
        void fees_areStored() {
            var challenge = new DeathmatchChallenge(proposer, TEAM_A, 100, 600, twoTeams(3, 3));
            assertEquals(100, challenge.getPerPersonFee());
            assertEquals(600, challenge.getTotalPool());
        }

        @Test
        @DisplayName("チームサイズが防御的にコピーされる")
        void teamSizes_areDefensivelyCopied() {
            Map<String, Integer> sizes = twoTeams(3, 3);
            var challenge = new DeathmatchChallenge(proposer, TEAM_A, 100, 600, sizes);
            sizes.put(TEAM_A, 999); // 外部から変更
            assertEquals(3, challenge.getTeamSizes().get(TEAM_A)); // 影響を受けない
        }

        @Test
        @DisplayName("提案者は自動賛成票が記録される")
        void proposer_autoVoted() {
            var challenge = new DeathmatchChallenge(proposer, TEAM_A, 100, 600, twoTeams(3, 3));
            assertTrue(challenge.hasVoted(proposer));
        }

        @Test
        @DisplayName("nullの提案者でNPEが発生する")
        void nullProposer_throwsNPE() {
            assertThrows(NullPointerException.class,
                    () -> new DeathmatchChallenge(null, TEAM_A, 100, 600, twoTeams(3, 3)));
        }

        @Test
        @DisplayName("nullの提案者チームでNPEが発生する")
        void nullProposerTeam_throwsNPE() {
            assertThrows(NullPointerException.class,
                    () -> new DeathmatchChallenge(proposer, null, 100, 600, twoTeams(3, 3)));
        }
    }

    // ========================================================================
    // 投票
    // ========================================================================

    @Nested
    @DisplayName("vote - 投票")
    class VoteTest {

        @Test
        @DisplayName("賛成票でPENDINGを返す（チームに未投票者がいる場合）")
        void yesVote_returnsPending_whenTeamNotFull() {
            var challenge = new DeathmatchChallenge(proposer, TEAM_A, 100, 600, twoTeams(3, 3));
            UUID voter = UUID.randomUUID();
            VoteResult result = challenge.vote(voter, TEAM_A, true);
            assertEquals(VoteResult.PENDING, result);
        }

        @Test
        @DisplayName("反対票で即REJECTEDを返す（5人以下チーム）")
        void noVote_returnsRejected_smallTeam() {
            var challenge = new DeathmatchChallenge(proposer, TEAM_A, 100, 600, twoTeams(3, 3));
            UUID voter = UUID.randomUUID();
            VoteResult result = challenge.vote(voter, TEAM_A, false);
            assertEquals(VoteResult.REJECTED, result);
        }

        @Test
        @DisplayName("二重投票は無視される")
        void doubleVote_isIgnored() {
            var challenge = new DeathmatchChallenge(proposer, TEAM_A, 100, 600, twoTeams(2, 2));
            UUID voter = UUID.randomUUID();
            // 最初は賛成
            challenge.vote(voter, TEAM_A, true);
            // 2回目は反対 → 無視されるはず
            VoteResult result = challenge.vote(voter, TEAM_A, false);
            // 提案者(auto-yes) + voter(yes) = 2/2 → TEAM_A 承認
            // TEAM_B はまだ0票 → PENDING
            assertEquals(VoteResult.PENDING, result);
        }

        @Test
        @DisplayName("提案者の自動投票は変更できない")
        void proposerAutoVote_cannotBeChanged() {
            // 提案者が1人チームなら、auto-yes で即 ACCEPTED（TEAM_BがPENDINGなので全体はPENDING）
            var challenge = new DeathmatchChallenge(proposer, TEAM_A, 100, 600, twoTeams(1, 1));
            // 提案者が反対に変更しようとしても無視
            VoteResult result = challenge.vote(proposer, TEAM_A, false);
            // TEAM_A: proposer=yes(auto, 変更不可) → 1/1 承認
            // TEAM_B: 0/1 → PENDING
            assertEquals(VoteResult.PENDING, result);
        }
    }

    // ========================================================================
    // 5人以下 全員一致
    // ========================================================================

    @Nested
    @DisplayName("evaluateResult - 5人以下 全員一致")
    class UnanimousTest {

        @Test
        @DisplayName("1人チーム×2: 両方proposer/voter全員yes → ACCEPTED")
        void oneVsOne_allYes_accepted() {
            var challenge = new DeathmatchChallenge(proposer, TEAM_A, 100, 200, twoTeams(1, 1));
            // TEAM_A: proposer auto-yes → 1/1 OK
            UUID voterB = UUID.randomUUID();
            VoteResult result = challenge.vote(voterB, TEAM_B, true);
            assertEquals(VoteResult.ACCEPTED, result);
        }

        @Test
        @DisplayName("2人チーム: 両方yes → ACCEPTED")
        void twoVsTwo_allYes_accepted() {
            var challenge = new DeathmatchChallenge(proposer, TEAM_A, 100, 400, twoTeams(2, 2));
            UUID voterA = UUID.randomUUID();
            UUID voterB1 = UUID.randomUUID();
            UUID voterB2 = UUID.randomUUID();

            challenge.vote(voterA, TEAM_A, true);  // TEAM_A: 2/2 OK
            challenge.vote(voterB1, TEAM_B, true);
            VoteResult result = challenge.vote(voterB2, TEAM_B, true); // TEAM_B: 2/2 OK
            assertEquals(VoteResult.ACCEPTED, result);
        }

        @Test
        @DisplayName("2人チーム: 1人反対 → REJECTED")
        void twoVsTwo_oneNo_rejected() {
            var challenge = new DeathmatchChallenge(proposer, TEAM_A, 100, 400, twoTeams(2, 2));
            UUID voterB = UUID.randomUUID();
            VoteResult result = challenge.vote(voterB, TEAM_B, false);
            assertEquals(VoteResult.REJECTED, result);
        }

        @Test
        @DisplayName("3人チーム: 2人yes + 1人未投票 → PENDING")
        void threeVsThree_twoYes_pending() {
            var challenge = new DeathmatchChallenge(proposer, TEAM_A, 100, 600, twoTeams(3, 3));
            UUID voterA = UUID.randomUUID();
            VoteResult result = challenge.vote(voterA, TEAM_A, true);
            // TEAM_A: 2/3, TEAM_B: 0/3
            assertEquals(VoteResult.PENDING, result);
        }

        @Test
        @DisplayName("5人チーム: 4yes + 1no → REJECTED")
        void fiveTeam_fourYes_oneNo_rejected() {
            Map<String, Integer> sizes = oneTeam(TEAM_A, 5);
            var challenge = new DeathmatchChallenge(proposer, TEAM_A, 100, 500, sizes);

            UUID v1 = UUID.randomUUID();
            UUID v2 = UUID.randomUUID();
            UUID v3 = UUID.randomUUID();
            UUID v4 = UUID.randomUUID();

            challenge.vote(v1, TEAM_A, true);
            challenge.vote(v2, TEAM_A, true);
            challenge.vote(v3, TEAM_A, true);
            VoteResult result = challenge.vote(v4, TEAM_A, false);
            assertEquals(VoteResult.REJECTED, result);
        }

        @Test
        @DisplayName("5人チーム: 全員yes → ACCEPTED")
        void fiveTeam_allYes_accepted() {
            Map<String, Integer> sizes = oneTeam(TEAM_A, 5);
            var challenge = new DeathmatchChallenge(proposer, TEAM_A, 100, 500, sizes);

            for (int i = 0; i < 4; i++) {
                challenge.vote(UUID.randomUUID(), TEAM_A, true);
            }
            assertEquals(VoteResult.ACCEPTED, challenge.evaluateResult());
        }
    }

    // ========================================================================
    // 6人以上 過半数
    // ========================================================================

    @Nested
    @DisplayName("evaluateResult - 6人以上 過半数")
    class MajorityTest {

        @Test
        @DisplayName("6人チーム: ceil(6/2)=3 賛成3票 → ACCEPTED")
        void sixTeam_threeYes_accepted() {
            Map<String, Integer> sizes = oneTeam(TEAM_A, 6);
            var challenge = new DeathmatchChallenge(proposer, TEAM_A, 100, 600, sizes);
            // proposer auto-yes = 1
            challenge.vote(UUID.randomUUID(), TEAM_A, true); // 2
            VoteResult result = challenge.vote(UUID.randomUUID(), TEAM_A, true); // 3
            assertEquals(VoteResult.ACCEPTED, result);
        }

        @Test
        @DisplayName("6人チーム: 反対3票 → REJECTED")
        void sixTeam_threeNo_rejected() {
            Map<String, Integer> sizes = oneTeam(TEAM_A, 6);
            var challenge = new DeathmatchChallenge(proposer, TEAM_A, 100, 600, sizes);
            challenge.vote(UUID.randomUUID(), TEAM_A, false); // no 1
            challenge.vote(UUID.randomUUID(), TEAM_A, false); // no 2
            VoteResult result = challenge.vote(UUID.randomUUID(), TEAM_A, false); // no 3
            assertEquals(VoteResult.REJECTED, result);
        }

        @Test
        @DisplayName("7人チーム: ceil(7/2)=4 賛成3票 → PENDING")
        void sevenTeam_threeYes_pending() {
            Map<String, Integer> sizes = oneTeam(TEAM_A, 7);
            var challenge = new DeathmatchChallenge(proposer, TEAM_A, 100, 700, sizes);
            challenge.vote(UUID.randomUUID(), TEAM_A, true); // 2
            VoteResult result = challenge.vote(UUID.randomUUID(), TEAM_A, true); // 3
            assertEquals(VoteResult.PENDING, result);
        }

        @Test
        @DisplayName("7人チーム: ceil(7/2)=4 賛成4票 → ACCEPTED")
        void sevenTeam_fourYes_accepted() {
            Map<String, Integer> sizes = oneTeam(TEAM_A, 7);
            var challenge = new DeathmatchChallenge(proposer, TEAM_A, 100, 700, sizes);
            challenge.vote(UUID.randomUUID(), TEAM_A, true); // 2
            challenge.vote(UUID.randomUUID(), TEAM_A, true); // 3
            VoteResult result = challenge.vote(UUID.randomUUID(), TEAM_A, true); // 4
            assertEquals(VoteResult.ACCEPTED, result);
        }

        @Test
        @DisplayName("10人チーム: ceil(10/2)=5 賛成5票 → ACCEPTED")
        void tenTeam_fiveYes_accepted() {
            Map<String, Integer> sizes = oneTeam(TEAM_A, 10);
            var challenge = new DeathmatchChallenge(proposer, TEAM_A, 100, 1000, sizes);
            // proposer = 1
            for (int i = 0; i < 4; i++) {
                challenge.vote(UUID.randomUUID(), TEAM_A, true);
            }
            assertEquals(VoteResult.ACCEPTED, challenge.evaluateResult());
        }
    }

    // ========================================================================
    // マルチチーム
    // ========================================================================

    @Nested
    @DisplayName("evaluateResult - マルチチーム")
    class MultiTeamTest {

        @Test
        @DisplayName("3vs3: チームA全員yes + チームB未投票 → PENDING")
        void threeVsThree_teamAComplete_teamBPending() {
            var challenge = new DeathmatchChallenge(proposer, TEAM_A, 100, 600, twoTeams(3, 3));
            challenge.vote(UUID.randomUUID(), TEAM_A, true);
            challenge.vote(UUID.randomUUID(), TEAM_A, true);
            // TEAM_A: 3/3 OK, TEAM_B: 0/3
            assertEquals(VoteResult.PENDING, challenge.evaluateResult());
        }

        @Test
        @DisplayName("3vs3: 両チーム全員yes → ACCEPTED")
        void threeVsThree_bothComplete_accepted() {
            var challenge = new DeathmatchChallenge(proposer, TEAM_A, 100, 600, twoTeams(3, 3));
            challenge.vote(UUID.randomUUID(), TEAM_A, true);
            challenge.vote(UUID.randomUUID(), TEAM_A, true);

            challenge.vote(UUID.randomUUID(), TEAM_B, true);
            challenge.vote(UUID.randomUUID(), TEAM_B, true);
            VoteResult result = challenge.vote(UUID.randomUUID(), TEAM_B, true);
            assertEquals(VoteResult.ACCEPTED, result);
        }

        @Test
        @DisplayName("3vs3: チームA全員yes + チームB 1人no → REJECTED")
        void threeVsThree_teamBOneNo_rejected() {
            var challenge = new DeathmatchChallenge(proposer, TEAM_A, 100, 600, twoTeams(3, 3));
            challenge.vote(UUID.randomUUID(), TEAM_A, true);
            challenge.vote(UUID.randomUUID(), TEAM_A, true);
            VoteResult result = challenge.vote(UUID.randomUUID(), TEAM_B, false);
            assertEquals(VoteResult.REJECTED, result);
        }

        @Test
        @DisplayName("混合サイズ: 2人チーム(全員一致) + 7人チーム(過半数)")
        void mixedSizes_unanimousAndMajority() {
            var challenge = new DeathmatchChallenge(proposer, TEAM_A, 100, 900, twoTeams(2, 7));

            // TEAM_A (2人): proposer auto-yes + 1人yes → 承認
            challenge.vote(UUID.randomUUID(), TEAM_A, true);

            // TEAM_B (7人): ceil(7/2)=4 必要
            challenge.vote(UUID.randomUUID(), TEAM_B, true);
            challenge.vote(UUID.randomUUID(), TEAM_B, true);
            challenge.vote(UUID.randomUUID(), TEAM_B, true);
            // 3票 → まだPENDING
            assertEquals(VoteResult.PENDING, challenge.evaluateResult());

            VoteResult result = challenge.vote(UUID.randomUUID(), TEAM_B, true); // 4票 → OK
            assertEquals(VoteResult.ACCEPTED, result);
        }
    }

    // ========================================================================
    // hasVoted
    // ========================================================================

    @Nested
    @DisplayName("hasVoted - 投票済み確認")
    class HasVotedTest {

        @Test
        @DisplayName("提案者は構築直後にtrue")
        void proposer_hasVoted_true() {
            var challenge = new DeathmatchChallenge(proposer, TEAM_A, 100, 200, twoTeams(1, 1));
            assertTrue(challenge.hasVoted(proposer));
        }

        @Test
        @DisplayName("未投票者はfalse")
        void nonVoter_hasVoted_false() {
            var challenge = new DeathmatchChallenge(proposer, TEAM_A, 100, 200, twoTeams(2, 2));
            assertFalse(challenge.hasVoted(UUID.randomUUID()));
        }

        @Test
        @DisplayName("投票後はtrue")
        void afterVoting_hasVoted_true() {
            var challenge = new DeathmatchChallenge(proposer, TEAM_A, 100, 400, twoTeams(3, 3));
            UUID voter = UUID.randomUUID();
            assertFalse(challenge.hasVoted(voter));
            challenge.vote(voter, TEAM_B, true);
            assertTrue(challenge.hasVoted(voter));
        }
    }

    // ========================================================================
    // getVoteStatus
    // ========================================================================

    @Nested
    @DisplayName("getVoteStatus - 投票状況表示")
    class VoteStatusTest {

        @Test
        @DisplayName("初期状態で提案者の自動投票が反映される")
        void initial_showsProposerVote() {
            var challenge = new DeathmatchChallenge(proposer, TEAM_A, 100, 600, twoTeams(3, 3));
            String status = challenge.getVoteStatus(TEAM_A);
            assertEquals("✔1 ✗0 (1/3)", status);
        }

        @Test
        @DisplayName("投票後にフォーマットが正しい")
        void afterVotes_formatCorrect() {
            var challenge = new DeathmatchChallenge(proposer, TEAM_A, 100, 600, twoTeams(3, 3));
            challenge.vote(UUID.randomUUID(), TEAM_A, true);
            challenge.vote(UUID.randomUUID(), TEAM_A, false);
            String status = challenge.getVoteStatus(TEAM_A);
            // proposer(yes) + voter1(yes) + voter2(no) = ✔2 ✗1
            assertEquals("✔2 ✗1 (2/3)", status);
        }

        @Test
        @DisplayName("未投票チームは0票を表示")
        void noVotes_showsZeros() {
            var challenge = new DeathmatchChallenge(proposer, TEAM_A, 100, 600, twoTeams(3, 3));
            String status = challenge.getVoteStatus(TEAM_B);
            assertEquals("✔0 ✗0 (0/3)", status);
        }
    }

    // ========================================================================
    // Getters & 不変性
    // ========================================================================

    @Nested
    @DisplayName("Getters & 不変性")
    class ImmutabilityTest {

        @Test
        @DisplayName("getTeamSizesは変更不可マップを返す")
        void teamSizes_isUnmodifiable() {
            var challenge = new DeathmatchChallenge(proposer, TEAM_A, 100, 600, twoTeams(3, 3));
            assertThrows(UnsupportedOperationException.class,
                    () -> challenge.getTeamSizes().put("Gamma", 5));
        }

        @Test
        @DisplayName("getVotesは変更不可マップを返す（外側）")
        void votes_outerMap_isUnmodifiable() {
            var challenge = new DeathmatchChallenge(proposer, TEAM_A, 100, 600, twoTeams(3, 3));
            assertThrows(UnsupportedOperationException.class,
                    () -> challenge.getVotes().put("Gamma", new HashMap<>()));
        }

        @Test
        @DisplayName("getVotesは変更不可マップを返す（内側）")
        void votes_innerMap_isUnmodifiable() {
            var challenge = new DeathmatchChallenge(proposer, TEAM_A, 100, 600, twoTeams(3, 3));
            Map<UUID, Boolean> innerMap = challenge.getVotes().get(TEAM_A);
            assertNotNull(innerMap);
            assertThrows(UnsupportedOperationException.class,
                    () -> innerMap.put(UUID.randomUUID(), true));
        }
    }

    // ========================================================================
    // エッジケース
    // ========================================================================

    @Nested
    @DisplayName("エッジケース")
    class EdgeCaseTest {

        @Test
        @DisplayName("チームサイズ0は評価でスキップされ自動承認扱い")
        void teamSizeZero_isSkipped() {
            Map<String, Integer> sizes = new HashMap<>();
            sizes.put(TEAM_A, 1);
            sizes.put(TEAM_B, 0); // 空チーム
            var challenge = new DeathmatchChallenge(proposer, TEAM_A, 100, 100, sizes);
            // TEAM_A: proposer auto-yes → 1/1 OK
            // TEAM_B: size=0 → skip
            assertEquals(VoteResult.ACCEPTED, challenge.evaluateResult());
        }

        @Test
        @DisplayName("単一チームでproposer自動yes → ACCEPTED")
        void singleTeam_proposerOnly_accepted() {
            Map<String, Integer> sizes = oneTeam(TEAM_A, 1);
            var challenge = new DeathmatchChallenge(proposer, TEAM_A, 100, 100, sizes);
            assertEquals(VoteResult.ACCEPTED, challenge.evaluateResult());
        }
    }
}
