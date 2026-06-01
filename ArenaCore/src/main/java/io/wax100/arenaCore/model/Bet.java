package io.wax100.arenaCore.model;


import java.util.Objects;
import java.util.UUID;

/**
 * 1件のベット情報を表すレコード。
 *
 * <p>観客がカーペットを設置した際に記録される。
 * 同一プレイヤーが同一チームに複数回ベットした場合、金額が加算される。
 */
public class Bet {

    private final UUID playerId;
    private final String teamName;
    private long amount;

    /**
     * @param playerId ベットしたプレイヤーの UUID（null不可）
     * @param teamName ベット先チーム名（null不可）
     * @param amount   ベット額
     * @throws NullPointerException playerId または teamName が null の場合
     */
    public Bet(UUID playerId, String teamName, long amount) {
        this.playerId = Objects.requireNonNull(playerId, "playerId must not be null");
        this.teamName = Objects.requireNonNull(teamName, "teamName must not be null");
        if (amount < 0) {
            throw new IllegalArgumentException("amount must not be negative: " + amount);
        }
        this.amount = amount;
    }

    public UUID playerId() { return playerId; }
    public String teamName() { return teamName; }
    public long amount() { return amount; }

    /**
     * ベット額を加算する。
     *
     * @param additional 追加金額（減算の場合は負数）
     * @throws IllegalArgumentException 結果の金額が負になる場合
     */
    public void addAmount(long additional) {
        long newAmount = Math.addExact(this.amount, additional);
        if (newAmount < 0) {
            throw new IllegalArgumentException(
                    "Resulting amount must not be negative: " + this.amount + " + " + additional);
        }
        this.amount = newAmount;
    }

    /**
     * playerId と teamName に基づく等価性判定。
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Bet bet)) return false;
        return playerId.equals(bet.playerId) && teamName.equals(bet.teamName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(playerId, teamName);
    }

    @Override
    public String toString() {
        return "Bet{" +
                "playerId=" + playerId +
                ", teamName='" + teamName + '\'' +
                ", amount=" + amount +
                '}';
    }
}
