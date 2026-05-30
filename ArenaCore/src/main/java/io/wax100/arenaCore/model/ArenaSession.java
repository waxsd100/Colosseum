package io.wax100.arenaCore.model;

import org.bukkit.Location;

import java.util.*;

/**
 * 闘技場セッションのデータモデル。
 *
 * <p>チーム構成、賭け情報、設置カーペット座標、スコアなどを保持する。
 */
public class ArenaSession {

    private final String name;
    private final List<String> teamNames;
    private final Map<String, List<UUID>> teams;
    private final Map<UUID, Bet> bets;
    /** 設置されたカーペットの座標 → 賭け情報(playerId, teamName, chipValue) */
    private final Map<Location, PlacedChipInfo> placedChips;
    /** チーム別スコア（キル数） */
    private final Map<String, Integer> scores;

    private ArenaState state;
    private String winningTeam;
    private long entryFeePool;

    /**
     * @param name      セッション名
     * @param teamNames チーム名リスト
     */
    public ArenaSession(String name, List<String> teamNames) {
        this.name = name;
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
    public void setState(ArenaState state) { this.state = state; }
    public List<String> getTeamNames() { return Collections.unmodifiableList(teamNames); }
    public String getWinningTeam() { return winningTeam; }
    public void setWinningTeam(String team) { this.winningTeam = team; }

    // ── チーム管理 ──

    public boolean hasTeam(String teamName) { return teams.containsKey(teamName); }

    public boolean addTeamMember(String teamName, UUID playerId) {
        List<UUID> members = teams.get(teamName);
        if (members == null) return false;
        if (isFighter(playerId)) return false;
        members.add(playerId);
        return true;
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
    public void addEntryFee(long fee) { this.entryFeePool += fee; }

    // ── 賭け管理 ──

    public Map<UUID, Bet> getBets() { return Collections.unmodifiableMap(bets); }

    public Bet getBet(UUID playerId) { return bets.get(playerId); }

    public void addOrUpdateBet(UUID playerId, String teamName, long amount) {
        Bet existing = bets.get(playerId);
        if (existing != null && existing.getTeamName().equals(teamName)) {
            existing.addAmount(amount);
        } else {
            bets.put(playerId, new Bet(playerId, teamName, amount));
        }
    }

    /** チーム別の賭け金合計 */
    public long getTeamPool(String teamName) {
        long total = 0;
        for (Bet bet : bets.values()) {
            if (bet.getTeamName().equals(teamName)) {
                total += bet.getAmount();
            }
        }
        return total;
    }

    /** 全賭け金合計 */
    public long getTotalPool() {
        long total = 0;
        for (Bet bet : bets.values()) {
            total += bet.getAmount();
        }
        return total;
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
    public void addPlacedChip(Location location, UUID playerId, String teamName, long chipValue) {
        placedChips.put(location, new PlacedChipInfo(playerId, teamName, chipValue));
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
     * @return 削除された設置情報。存在しない場合 {@code null}
     */
    public PlacedChipInfo removePlacedChip(Location location) {
        return placedChips.remove(location);
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

    public Map<String, Integer> getScores() {
        return Collections.unmodifiableMap(scores);
    }

    // ── 設置チップ情報 ──

    /**
     * 設置されたカーペットチップの情報。
     */
    public static class PlacedChipInfo {
        private final UUID playerId;
        private final String teamName;
        private final long chipValue;

        public PlacedChipInfo(UUID playerId, String teamName, long chipValue) {
            this.playerId = playerId;
            this.teamName = teamName;
            this.chipValue = chipValue;
        }

        public UUID getPlayerId() { return playerId; }
        public String getTeamName() { return teamName; }
        public long getChipValue() { return chipValue; }
    }
}
