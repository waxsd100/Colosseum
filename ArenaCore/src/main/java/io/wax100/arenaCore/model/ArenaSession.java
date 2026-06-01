package io.wax100.arenaCore.model;

import io.wax100.arenaCore.util.ArenaMessages;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;

import java.util.*;

/**
 * 闘技場セッションのデータモデル。
 *
 * <p>チーム構成、賭け情報、設置カーペット座標、スコアなどを保持する。
 *
 * <h3>状態遷移</h3>
 * <pre>
 * SETUP → BETTING → CLOSED → ACTIVE → FINISHED
 *   any state → FINISHED  (cancel)
 * </pre>
 */
public class ArenaSession {

    private final String name;
    private final List<String> teamNames;
    private final Map<String, List<UUID>> teams;
    private final Map<UUID, Map<String, Bet>> bets;
    /** 設置されたカーペットの座標 → 賭け情報(playerId, teamName, chipValue) */
    private final Map<Location, PlacedChipInfo> placedChips;
    /** チーム別スコア（キル数） */
    private final Map<String, Integer> scores;

    /** チーム別賭け金キャッシュ */
    private final Map<String, Long> teamPools = new HashMap<>();
    /** 全賭け金合計キャッシュ */
    private long totalPool;

    private ArenaState state;
    private String winningTeam;
    private long entryFeePool;

    /** モンスターチームのセット */
    private final Set<String> mobTeams = new HashSet<>();
    /** チーム別の待機場設定（プレイヤー・モンスター共通） */
    private final Map<String, TeamAreaConfig> teamAreaConfigs = new HashMap<>();
    /** スポーン済みモンスターのUUID → チーム名 */
    private final Map<UUID, String> trackedMobs = new HashMap<>();
    /** 全滅済みチーム（Mobチーム等、プレイヤーメンバーがいないチーム用） */
    private final Set<String> eliminatedTeams = new HashSet<>();
    /** チーム別カスタムカラー（未設定時はデフォルトパレットから自動割当） */
    private final Map<String, ChatColor> teamColors = new HashMap<>();

    /**
     * @param name      セッション名（null不可）
     * @param teamNames チーム名リスト（null不可・空許容）
     * @throws NullPointerException name または teamNames が null の場合
     */
    public ArenaSession(String name, List<String> teamNames) {
        this.name = Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(teamNames, "teamNames must not be null");
        this.teamNames = new ArrayList<>(teamNames);
        this.teams = new LinkedHashMap<>();
        this.bets = new HashMap<>();
        this.placedChips = new HashMap<>();
        this.scores = new HashMap<>();
        this.state = ArenaState.SETUP;
        this.entryFeePool = 0;

        for (String team : teamNames) {
            teams.put(team, new ArrayList<>());
            scores.put(team, 0);
        }
    }


    // ── 基本情報 ──

    public String getName() { return name; }
    public ArenaState getState() { return state; }

    /**
     * セッション状態を遷移させる。
     *
     * <p>許可される遷移:
     * <ul>
     *   <li>SETUP → BETTING</li>
     *   <li>BETTING → CLOSED</li>
     *   <li>BETTING → ACTIVE</li>
     *   <li>CLOSED → ACTIVE</li>
     *   <li>ACTIVE → FINISHED</li>
     *   <li>任意 → FINISHED（キャンセル）</li>
     * </ul>
     *
     * @param newState 新しい状態
     * @throws NullPointerException  newState が null の場合
     * @throws IllegalStateException 許可されていない遷移の場合
     */
    public void setState(ArenaState newState) {
        Objects.requireNonNull(newState, "newState must not be null");
        if (!isValidTransition(this.state, newState)) {
            throw new IllegalStateException(
                    "Invalid state transition: " + this.state + " → " + newState);
        }
        this.state = newState;
    }

    /**
     * 状態遷移が有効かどうかを判定する。
     */
    private static final Map<ArenaState, EnumSet<ArenaState>> VALID_TRANSITIONS;
    static {
        Map<ArenaState, EnumSet<ArenaState>> m = new EnumMap<>(ArenaState.class);
        m.put(ArenaState.SETUP,    EnumSet.of(ArenaState.BETTING, ArenaState.FINISHED));
        m.put(ArenaState.BETTING,  EnumSet.of(ArenaState.CLOSED, ArenaState.ACTIVE, ArenaState.FINISHED));
        m.put(ArenaState.CLOSED,   EnumSet.of(ArenaState.ACTIVE, ArenaState.FINISHED));
        m.put(ArenaState.ACTIVE,   EnumSet.of(ArenaState.FINISHED));
        m.put(ArenaState.FINISHED, EnumSet.noneOf(ArenaState.class));
        VALID_TRANSITIONS = Collections.unmodifiableMap(m);
    }

    private static boolean isValidTransition(ArenaState from, ArenaState to) {
        EnumSet<ArenaState> allowed = VALID_TRANSITIONS.get(from);
        return allowed != null && allowed.contains(to);
    }
    public List<String> getTeamNames() { return Collections.unmodifiableList(teamNames); }
    public String getWinningTeam() { return winningTeam; }
    public void setWinningTeam(String team) { this.winningTeam = team; }

    /**
     * チーム名に対応するチーム色を返す。
     *
     * <p>カスタムカラーが設定されている場合はそれを優先し、
     * 未設定の場合はデフォルトパレットから自動割当する。
     * チーム名が見つからない場合は {@link ChatColor#WHITE} を返す。
     *
     * @param teamName チーム名
     * @return チーム色
     */
    public ChatColor getTeamColor(String teamName) {
        ChatColor custom = teamColors.get(teamName);
        if (custom != null) return custom;
        int index = teamNames.indexOf(teamName);
        return index >= 0 ? ArenaMessages.getTeamColor(index) : ChatColor.WHITE;
    }

    /**
     * チームのカスタムカラーを設定する。
     *
     * @param teamName チーム名
     * @param color    設定する色
     */
    public void setTeamColor(String teamName, ChatColor color) {
        teamColors.put(teamName, color);
    }

    /**
     * 全チームのカスタムカラー設定を返す。
     *
     * @return チーム名→ChatColor の不変マップ
     */
    public Map<String, ChatColor> getTeamColors() {
        return Collections.unmodifiableMap(teamColors);
    }

    // ── チーム管理 ──

    /**
     * チームを追加する。同名チームが既に存在する場合は何もしない。
     *
     * @param teamName チーム名
     */
    public void addTeam(String teamName) {
        if (teams.containsKey(teamName)) return;
        teamNames.add(teamName);
        teams.put(teamName, new ArrayList<>());
        scores.put(teamName, 0);
    }

    public boolean hasTeam(String teamName) { return teams.containsKey(teamName); }

    public void addTeamMember(String teamName, UUID playerId) {
        List<UUID> members = teams.get(teamName);
        if (members == null) return;
        if (isFighter(playerId)) return;
        members.add(playerId);
    }

    /**
     * チームからメンバーを削除する。
     *
     * @param teamName チーム名
     * @param playerId プレイヤーの UUID
     */
    public void removeTeamMember(String teamName, UUID playerId) {
        List<UUID> members = teams.get(teamName);
        if (members == null) return;
        members.remove(playerId);
    }

    public List<UUID> getTeamMembers(String teamName) {
        List<UUID> members = teams.get(teamName);
        return members != null ? Collections.unmodifiableList(members) : Collections.emptyList();
    }

    public int getTeamSize(String teamName) {
        List<UUID> members = teams.get(teamName);
        return members != null ? members.size() : 0;
    }

    public boolean isFighter(UUID playerId) {
        for (List<UUID> members : teams.values()) {
            if (members.contains(playerId)) return true;
        }
        return false;
    }

    public String getPlayerTeam(UUID playerId) {
        for (Map.Entry<String, List<UUID>> entry : teams.entrySet()) {
            if (entry.getValue().contains(playerId)) return entry.getKey();
        }
        return null;
    }

    // ── 参加費 ──

    public long getEntryFeePool() { return entryFeePool; }

    /**
     * 参加費をプールに加算する。
     *
     * @param fee 加算額（0以上）
     * @throws IllegalArgumentException fee が負の場合
     */
    public void addEntryFee(long fee) {
        if (fee < 0) {
            throw new IllegalArgumentException("fee must not be negative: " + fee);
        }
        this.entryFeePool = Math.addExact(this.entryFeePool, fee);
    }

    // ── 賭け管理 ──

    /**
     * 全賭けをフラットなコレクションとして返す。
     *
     * @return 全プレイヤーの全チームへの賭けリスト（変更不可）
     */
    public List<Bet> getAllBets() {
        List<Bet> result = new ArrayList<>();
        for (Map<String, Bet> teamBets : bets.values()) {
            result.addAll(teamBets.values());
        }
        return Collections.unmodifiableList(result);
    }

    /**
     * 指定プレイヤーの全賭けを返す。
     *
     * @param playerId プレイヤーの UUID
     * @return チーム名→Bet のマップ。賭けがない場合は空マップ
     */
    public Map<String, Bet> getPlayerBets(UUID playerId) {
        Map<String, Bet> teamBets = bets.get(playerId);
        return teamBets != null ? Collections.unmodifiableMap(teamBets) : Collections.emptyMap();
    }

    /**
     * 指定プレイヤーの指定チームへの賭けを返す。
     *
     * @param playerId プレイヤーの UUID
     * @param teamName チーム名
     * @return 賭け。存在しない場合 {@code null}
     */
    public Bet getBet(UUID playerId, String teamName) {
        Map<String, Bet> teamBets = bets.get(playerId);
        return teamBets != null ? teamBets.get(teamName) : null;
    }

    /**
     * 指定プレイヤーの指定チームへの賭けを削除する。
     *
     * @param playerId プレイヤーの UUID
     * @param teamName チーム名
     */
    public void removeBet(UUID playerId, String teamName) {
        Map<String, Bet> teamBets = bets.get(playerId);
        if (teamBets == null) return;
        Bet removed = teamBets.remove(teamName);
        if (removed != null) {
            long amt = removed.amount();
            teamPools.merge(teamName, -amt, Long::sum);
            // データ不整合時にオーバーフローしないよう安全に減算
            totalPool = Math.max(0L, totalPool - amt);
        }
        if (teamBets.isEmpty()) bets.remove(playerId);
    }

    /**
     * 賭けを追加または更新する。複数チームへの賭けも許可される。
     *
     * @param playerId 賭けたプレイヤーの UUID（null不可）
     * @param teamName 賭け先チーム名（null不可）
     * @param amount   賭け金額
     * @throws NullPointerException playerId または teamName が null の場合
     */
    public void addOrUpdateBet(UUID playerId, String teamName, long amount) {
        Objects.requireNonNull(playerId, "playerId must not be null");
        Objects.requireNonNull(teamName, "teamName must not be null");
        Map<String, Bet> teamBets = bets.computeIfAbsent(playerId, k -> new HashMap<>());
        Bet existing = teamBets.get(teamName);
        if (existing != null) {
            existing.addAmount(amount);
        } else {
            teamBets.put(teamName, new Bet(playerId, teamName, amount));
        }
        teamPools.merge(teamName, amount, Math::addExact);
        totalPool = Math.addExact(totalPool, amount);
    }

    /** チーム別の賭け金合計（キャッシュ済み O(1)） */
    public long getTeamPool(String teamName) {
        return teamPools.getOrDefault(teamName, 0L);
    }

    /** 全賭け金合計（キャッシュ済み O(1)） */
    public long getTotalPool() {
        return totalPool;
    }

    // ── 設置チップ管理 ──

    /**
     * 設置されたカーペットチップを記録する。
     *
     * @param location  設置座標
     * @param playerId  設置者
     * @param teamName  賭け先チーム
     * @param chipValue チップ額面
     */
    public void addPlacedChip(Location location, UUID playerId, String teamName,
                              long chipValue, Material originalBlock) {
        placedChips.put(location, new PlacedChipInfo(playerId, teamName, chipValue, originalBlock));
    }

    /**
     * 設置チップ情報を取得する。
     *
     * @param location 座標
     * @return 設置情報。存在しない場合 {@code null}
     */
    public PlacedChipInfo getPlacedChip(Location location) {
        return placedChips.get(location);
    }

    /**
     * 設置チップを削除する（賭け取消時）。
     *
     * @param location 座標
     */
    public void removePlacedChip(Location location) {
        placedChips.remove(location);
    }

    /**
     * 全設置チップの一覧を返す。
     *
     * @return 設置チップマップ
     */
    public Map<Location, PlacedChipInfo> getPlacedChips() {
        return Collections.unmodifiableMap(placedChips);
    }

    // ── スコア管理 ──

    public int getScore(String teamName) {
        return scores.getOrDefault(teamName, 0);
    }

    public void addScore(String teamName, int points) {
        scores.merge(teamName, points, Integer::sum);
    }


    /**
     * セッション終了時に全データをクリアする。
     *
     * <p>メモリリーク防止のため、セッション参照を破棄する前に呼び出すこと。
     *
     * @throws IllegalStateException セッションが FINISHED 状態でない場合
     */
    public void clearAllData() {
        if (this.state != ArenaState.FINISHED) {
            throw new IllegalStateException(
                    "clearAllData() は FINISHED 状態でのみ呼び出せます (現在: " + this.state + ")");
        }
        bets.clear();
        placedChips.clear();
        trackedMobs.clear();
        eliminatedTeams.clear();
        for (List<UUID> members : teams.values()) {
            members.clear();
        }
        scores.clear();
        teamAreaConfigs.clear();
        mobTeams.clear();
        teamPools.clear();
        totalPool = 0;
        entryFeePool = 0;
        winningTeam = null;
        teamColors.clear();
        fieldConfig = null;
    }

    // ── 戦闘エリア管理 ──

    private ArenaFieldConfig fieldConfig;

    /**
     * 戦闘エリア設定を取得する。
     *
     * @return 戦闘エリア設定。未設定の場合は {@code null}
     */
    public ArenaFieldConfig getFieldConfig() { return fieldConfig; }

    /**
     * 戦闘エリアを設定する。
     *
     * @param config 戦闘エリア設定（null許容）
     */
    public void setFieldConfig(ArenaFieldConfig config) { this.fieldConfig = config; }

    // ── チーム待機場管理 ──

    /**
     * チームの待機場設定を登録する（プレイヤー・モンスター共通）。
     */
    public void setTeamAreaConfig(String teamName, TeamAreaConfig config) {
        teamAreaConfigs.put(teamName, config);
    }

    /**
     * チームの待機場設定を取得する。
     */
    public TeamAreaConfig getTeamAreaConfig(String teamName) {
        return teamAreaConfigs.get(teamName);
    }

    // ── モンスターチーム管理 ──

    /**
     * チームをモンスターチームとしてマークする。
     */
    public void markAsMobTeam(String teamName) {
        if (hasTeam(teamName)) mobTeams.add(teamName);
    }

    /**
     * チームがモンスターチームかどうかを返す。
     */
    public boolean isMobTeam(String teamName) {
        return mobTeams.contains(teamName);
    }

    /**
     * スポーン済みモンスターを追跡登録する。
     */
    public void trackMob(UUID entityId, String teamName) {
        trackedMobs.put(entityId, teamName);
    }

    /**
     * モンスターのチーム名を取得する。
     */
    public String getMobTeam(UUID entityId) {
        return trackedMobs.get(entityId);
    }

    /**
     * スポーン済みモンスターを除去する（死亡時）。
     */
    public void removeMob(UUID entityId) {
        trackedMobs.remove(entityId);
    }

    /**
     * 指定チームに生存中のモンスターがいるかを返す。
     */
    public boolean hasAliveMobs(String teamName) {
        return trackedMobs.containsValue(teamName);
    }

    /**
     * チームを全滅済みとしてマークする（Mobチーム等で使用）。
     */
    public void markTeamEliminated(String teamName) {
        eliminatedTeams.add(teamName);
    }

    /**
     * チームが全滅済みかどうかを返す。
     */
    public boolean isTeamEliminated(String teamName) {
        return eliminatedTeams.contains(teamName);
    }

    /**
     * 指定チームの生存モンスター数を返す。
     */
    public int getAliveMobCount(String teamName) {
        int count = 0;
        for (String team : trackedMobs.values()) {
            if (team.equals(teamName)) count++;
        }
        return count;
    }

    /**
     * チームの実効メンバー数を返す（プレイヤー + 待機場内Mob数）。
     */
    public int getEffectiveTeamSize(String teamName) {
        int playerSize = getTeamSize(teamName);
        if (isMobTeam(teamName)) {
            TeamAreaConfig config = getTeamAreaConfig(teamName);
            if (config != null) {
                return playerSize + config.scanEntities().size();
            }
        }
        return playerSize;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ArenaSession that)) return false;
        return name.equals(that.name);
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }

    @Override
    public String toString() {
        return "ArenaSession{" +
                "name='" + name + '\'' +
                ", state=" + state +
                ", teams=" + teamNames.size() +
                ", bets=" + getAllBets().size() +
                ", winningTeam='" + winningTeam + '\'' +
                '}';
    }

    // ── 設置チップ情報 ──

    /**
     * 設置されたカーペットチップの情報（不変オブジェクト）。
     *
     * <p>{@code originalBlock} はカーペット設置前の元ブロックの素材を保持する。
     * 元ブロックが存在しない場合（空気ブロック等）は {@code null} が許容される。
     * 呼び出し側は {@link #getOriginalBlock()} の戻り値が {@code null} の場合に
     * {@link Material#AIR} へフォールバックすること。
     */
    public static class PlacedChipInfo {
        private final UUID playerId;
        private final String teamName;
        private final long chipValue;
        /** カーペット設置前の元ブロック素材。{@code null} の場合は AIR として扱う。 */
        private final Material originalBlock;

        /**
         * @param playerId      設置者（null不可）
         * @param teamName      賭け先チーム（null不可）
         * @param chipValue     チップ額面
         * @param originalBlock 元ブロック素材（null許容: AIR として扱われる）
         * @throws NullPointerException playerId または teamName が null の場合
         */
        public PlacedChipInfo(UUID playerId, String teamName, long chipValue, Material originalBlock) {
            this.playerId = Objects.requireNonNull(playerId, "playerId must not be null");
            this.teamName = Objects.requireNonNull(teamName, "teamName must not be null");
            this.chipValue = chipValue;
            this.originalBlock = originalBlock;
        }

        public UUID playerId() { return playerId; }
        public String teamName() { return teamName; }
        public long chipValue() { return chipValue; }

        /**
         * カーペット設置前の元ブロック素材を返す。
         *
         * @return 元ブロック素材。{@code null} の場合は {@link Material#AIR} として扱うこと。
         */
        public Material originalBlock() { return originalBlock; }

        /**
         * 元ブロック素材を返す（null安全版）。
         *
         * <p>{@code originalBlock} が {@code null} の場合、{@link Material#AIR} を返す。
         *
         * @return 元ブロック素材（非null保証）
         */
        public Material getOriginalBlockOrAir() {
            return originalBlock != null ? originalBlock : Material.AIR;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof PlacedChipInfo that)) return false;
            return chipValue == that.chipValue
                    && playerId.equals(that.playerId)
                    && teamName.equals(that.teamName)
                    && originalBlock == that.originalBlock;
        }

        @Override
        public int hashCode() {
            return Objects.hash(playerId, teamName, chipValue, originalBlock);
        }

        @Override
        public String toString() {
            return "PlacedChipInfo{" +
                    "playerId=" + playerId +
                    ", teamName='" + teamName + '\'' +
                    ", chipValue=" + chipValue +
                    ", originalBlock=" + originalBlock +
                    '}';
        }
    }
}
