package io.wax100.arenaCore.model;

import org.bukkit.Location;

import java.util.UUID;

/**
 * 1件の賭け情報を表すレコード。
 *
 * <p>観客がカーペットを設置した際に記録される。
 * 同一プレイヤーが同一チームに複数回賭けた場合、金額が加算される。
 */
public class Bet {

    private final UUID playerId;
    private final String teamName;
    private long amount;
    /** 固定オッズ方式で使用: 賭け時点のオッズ */
    private double lockedOdds;

    /**
     * @param playerId 賭けたプレイヤーの UUID
     * @param teamName 賭け先チーム名
     * @param amount   賭け金額
     */
    public Bet(UUID playerId, String teamName, long amount) {
        this.playerId = playerId;
        this.teamName = teamName;
        this.amount = amount;
        this.lockedOdds = 0.0;
    }

    public UUID getPlayerId() { return playerId; }
    public String getTeamName() { return teamName; }
    public long getAmount() { return amount; }
    public double getLockedOdds() { return lockedOdds; }

    /**
     * 賭け金額を加算する。
     *
     * @param additional 追加金額
     */
    public void addAmount(long additional) {
        this.amount += additional;
    }

    /**
     * 固定オッズを確定する。
     *
     * @param odds 確定オッズ
     */
    public void setLockedOdds(double odds) {
        this.lockedOdds = odds;
    }
}
