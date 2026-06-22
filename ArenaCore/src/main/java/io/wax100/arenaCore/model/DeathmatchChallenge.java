package io.wax100.arenaCore.model;

import java.util.*;

/**
 * デスマッチ提案の投票モデル。
 *
 * <p>闘技者がデスマッチを提案すると、全チームの闘技者が投票する。
 * 各チームごとに判定を行い、全チームで必要票数を満たせば成立する。
 *
 * <h3>投票ルール</h3>
 * <ul>
 *   <li>5人以下のチーム: 全員一致（全員賛成が必要）</li>
 *   <li>6人以上のチーム: {@code ceil(n/2)} 票で判定</li>
 *   <li>提案者は自動的に賛成票扱い</li>
 *   <li>二重投票は不可（変更不可）</li>
 * </ul>
 */
public class DeathmatchChallenge {

    /**
     * 投票結果。
     */
    public enum VoteResult {
        /** 投票継続中 */
        PENDING,
        /** 全チーム承認 → デスマッチ成立 */
        ACCEPTED,
        /** いずれかのチームで却下 → デスマッチ不成立 */
        REJECTED
    }

    /** チーム内の賛成・反対票の集計結果。 */
    private record VoteTally(int yes, int no) {}

    /** 全員一致ルールを適用するチームサイズの上限。 */
    private static final int UNANIMITY_THRESHOLD = 5;

    private final UUID proposer;
    private final String proposerTeam;
    private final long perPersonFee;
    private final long totalPool;
    /** ALL-INモードかどうか */
    private final boolean allIn;
    /** チームごとの投票: チーム名 → (プレイヤーUUID → 賛成/反対) */
    private final Map<String, Map<UUID, Boolean>> votes = new HashMap<>();
    /** チームごとの人数 */
    private final Map<String, Integer> teamSizes;

    /**
     * デスマッチ提案を作成する。
     *
     * <p>提案者の賛成票は自動的に記録される。
     *
     * @param proposer     提案者のUUID
     * @param proposerTeam 提案者のチーム名
     * @param perPersonFee 1人あたりDM参加費
     * @param totalPool    DM総額
     * @param teamSizes    チームごとの人数マップ
     * @param allIn        ALL-INモードかどうか
     */
    public DeathmatchChallenge(UUID proposer, String proposerTeam,
                               long perPersonFee, long totalPool,
                               Map<String, Integer> teamSizes, boolean allIn) {
        this.proposer = Objects.requireNonNull(proposer, "proposer must not be null");
        this.proposerTeam = Objects.requireNonNull(proposerTeam, "proposerTeam must not be null");
        this.perPersonFee = perPersonFee;
        this.totalPool = totalPool;
        this.allIn = allIn;
        this.teamSizes = new HashMap<>(teamSizes);

        // 各チームの投票マップを初期化
        for (String team : teamSizes.keySet()) {
            votes.put(team, new HashMap<>());
        }

        // 提案者の自動賛成票
        votes.computeIfAbsent(proposerTeam, k -> new HashMap<>()).put(proposer, true);
    }

    /**
     * 投票を記録する。
     *
     * @param player プレイヤーUUID
     * @param team   プレイヤーのチーム名
     * @param accept 賛成なら {@code true}、反対なら {@code false}
     * @return 投票後の全体判定結果
     */
    public VoteResult vote(UUID player, String team, boolean accept) {
        Map<UUID, Boolean> teamVotes = votes.computeIfAbsent(team, k -> new HashMap<>());
        if (teamVotes.containsKey(player)) {
            return evaluateResult(); // 既に投票済み — 変更不可
        }
        teamVotes.put(player, accept);
        return evaluateResult();
    }

    /**
     * 各チームの投票状況を評価し、全体の結果を返す。
     *
     * <p>判定ルール:
     * <ul>
     *   <li>{@value #UNANIMITY_THRESHOLD}人以下: 全員一致（全員賛成で承認、1票でも反対で却下）</li>
     *   <li>{@code UNANIMITY_THRESHOLD+1}人以上: {@code ceil(n/2)} 票で判定</li>
     *   <li>全チームで承認条件を満たす → ACCEPTED</li>
     *   <li>いずれかのチームで却下条件を満たす → REJECTED</li>
     *   <li>それ以外 → PENDING</li>
     * </ul>
     *
     * @return 投票結果
     */
    public VoteResult evaluateResult() {
        boolean allAccepted = true;

        for (Map.Entry<String, Integer> entry : teamSizes.entrySet()) {
            String team = entry.getKey();
            int teamSize = entry.getValue();
            if (teamSize <= 0) continue;

            Map<UUID, Boolean> teamVotes = votes.getOrDefault(team, Collections.emptyMap());
            VoteTally tally = countVotes(teamVotes);

            int required = calculateRequired(teamSize);

            // 却下判定
            if (teamSize <= UNANIMITY_THRESHOLD) {
                // UNANIMITY_THRESHOLD人以下: 1票でも反対なら即却下
                if (tally.no() > 0) return VoteResult.REJECTED;
            } else {
                // UNANIMITY_THRESHOLD+1人以上: 反対票が必要票数に達したら却下
                if (tally.no() >= required) return VoteResult.REJECTED;
            }

            // 承認判定
            if (teamSize <= UNANIMITY_THRESHOLD) {
                // UNANIMITY_THRESHOLD人以下: 全員賛成で承認
                if (tally.yes() < teamSize) allAccepted = false;
            } else {
                // UNANIMITY_THRESHOLD+1人以上: 賛成票が必要票数に達したら承認
                if (tally.yes() < required) allAccepted = false;
            }
        }

        return allAccepted ? VoteResult.ACCEPTED : VoteResult.PENDING;
    }

    /**
     * 必要賛成票数を計算する。
     *
     * @param teamSize チームの人数
     * @return 必要票数
     */
    private static int calculateRequired(int teamSize) {
        if (teamSize <= UNANIMITY_THRESHOLD) return teamSize; // 全員一致
        return (int) Math.ceil(teamSize / 2.0);
    }

    /**
     * 投票マップから賛成・反対票を集計する。
     *
     * @param votes 投票マップ（プレイヤーUUID → 賛成/反対）
     * @return 集計結果
     */
    private VoteTally countVotes(Map<UUID, Boolean> votes) {
        int yes = 0;
        int no = 0;
        for (Boolean v : votes.values()) {
            if (v) yes++;
            else no++;
        }
        return new VoteTally(yes, no);
    }

    /**
     * プレイヤーが既に投票済みかどうかを返す。
     *
     * @param player プレイヤーUUID
     * @return 投票済みなら {@code true}
     */
    public boolean hasVoted(UUID player) {
        for (Map<UUID, Boolean> teamVotes : votes.values()) {
            if (teamVotes.containsKey(player)) return true;
        }
        return false;
    }

    /**
     * 指定チームの投票状況を表示用文字列で返す。
     *
     * <p>例: {@code "✔2 ✗0 (2/3)"}
     *
     * @param team チーム名
     * @return 投票状況文字列
     */
    public String getVoteStatus(String team) {
        Map<UUID, Boolean> teamVotes = votes.getOrDefault(team, Collections.emptyMap());
        int teamSize = teamSizes.getOrDefault(team, 0);
        VoteTally tally = countVotes(teamVotes);
        int required = calculateRequired(teamSize);
        return "✔" + tally.yes() + " ✗" + tally.no() + " (" + tally.yes() + "/" + required + ")";
    }

    // ── Getters ──

    /** 提案者のUUIDを返す。 */
    public UUID getProposer() { return proposer; }

    /** 提案者のチーム名を返す。 */
    public String getProposerTeam() { return proposerTeam; }

    /** 1人あたりDM参加費を返す。 */
    public long getPerPersonFee() { return perPersonFee; }

    /** DM総額を返す。 */
    public long getTotalPool() { return totalPool; }

    /** ALL-INモードかどうかを返す。 */
    public boolean isAllIn() { return allIn; }

    /** チームごとの人数マップを返す。 */
    public Map<String, Integer> getTeamSizes() {
        return Collections.unmodifiableMap(teamSizes);
    }

    /** 投票データを返す。 */
    public Map<String, Map<UUID, Boolean>> getVotes() {
        Map<String, Map<UUID, Boolean>> result = new HashMap<>();
        for (Map.Entry<String, Map<UUID, Boolean>> entry : votes.entrySet()) {
            result.put(entry.getKey(), Collections.unmodifiableMap(entry.getValue()));
        }
        return Collections.unmodifiableMap(result);
    }
}
