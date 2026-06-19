package io.wax100.casinoCore.manager;

import io.wax100.chipLib.storage.PlayerStatsSnapshot;
import org.bukkit.configuration.ConfigurationSection;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * プレイヤーごとのカジノ統計データを保持するクラス。
 *
 * <p>
 * 各プレイヤーの累計参加回数、購入・換金額、勝敗回数、
 * 最大勝ち負け額、初回・最終参加日時を記録する。
 * {@code data.yml} の {@code players.<UUID>.stats} セクションに永続化される。
 */
public class PlayerStats {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private String name;
    private int totalSessions;
    private long totalPurchases;
    private long totalCashouts;
    private long netProfit;
    private int wins;
    private int losses;
    private int draws;
    private long biggestWin;
    private long biggestLoss;
    private LocalDateTime firstPlayed;
    private LocalDateTime lastPlayed;

    /**
     * 空の統計データを作成する。
     */
    public PlayerStats() {
    }

    // ── ゲッター ──

    public String getName() {
        return name;
    }

    public int getTotalSessions() {
        return totalSessions;
    }

    public long getTotalPurchases() {
        return totalPurchases;
    }

    public long getTotalCashouts() {
        return totalCashouts;
    }

    public long getNetProfit() {
        return netProfit;
    }

    public int getWins() {
        return wins;
    }

    public int getLosses() {
        return losses;
    }

    public int getDraws() {
        return draws;
    }

    public long getBiggestWin() {
        return biggestWin;
    }

    public long getBiggestLoss() {
        return biggestLoss;
    }

    public LocalDateTime getFirstPlayed() {
        return firstPlayed;
    }

    public LocalDateTime getLastPlayed() {
        return lastPlayed;
    }

    // ── 統計更新メソッド ──

    /**
     * セッション参加を記録する。
     *
     * <p>
     * 参加回数をインクリメントし、初回参加日時と最終参加日時を更新する。
     *
     * @param playerName プレイヤー名
     */
    public void recordSessionJoin(String playerName) {
        this.name = playerName;
        this.totalSessions++;
        LocalDateTime now = LocalDateTime.now();
        if (firstPlayed == null) {
            firstPlayed = now;
        }
        lastPlayed = now;
    }

    /**
     * セッション購入額を加算する。
     *
     * <p>オーバーフロー時は {@link Long#MAX_VALUE} にクランプされる。
     *
     * @param amount 購入額
     */
    public void addPurchase(long amount) {
        try {
            this.totalPurchases = Math.addExact(this.totalPurchases, amount);
        } catch (ArithmeticException e) {
            this.totalPurchases = Long.MAX_VALUE;
        }
    }

    /**
     * 未使用チップの返金時に、累計購入額から減算する。
     *
     * @param amount 返金額
     */
    public void refundPurchase(long amount) {
        if (this.totalPurchases != Long.MAX_VALUE) {
            this.totalPurchases = Math.max(0, this.totalPurchases - amount);
        }
    }

    /**
     * 換金結果を記録する。
     *
     * <p>
     * 換金額・損益・勝敗を更新し、最大勝ち負け額を更新する。
     *
     * @param cashoutAmount 換金額
     * @param purchased     セッション中の購入額
     */
    public void recordCashout(long cashoutAmount, long purchased) {
        try {
            this.totalCashouts = Math.addExact(this.totalCashouts, cashoutAmount);
        } catch (ArithmeticException e) {
            this.totalCashouts = Long.MAX_VALUE;
        }
        long sessionNet;
        try {
            sessionNet = Math.subtractExact(cashoutAmount, purchased);
        } catch (ArithmeticException e) {
            sessionNet = cashoutAmount > purchased ? Long.MAX_VALUE : Long.MIN_VALUE;
        }
        try {
            this.netProfit = Math.addExact(this.netProfit, sessionNet);
        } catch (ArithmeticException e) {
            this.netProfit = sessionNet > 0 ? Long.MAX_VALUE : Long.MIN_VALUE;
        }

        if (purchased > 0) {
            if (sessionNet > 0) {
                wins++;
                if (sessionNet > biggestWin) {
                    biggestWin = sessionNet;
                }
            } else if (sessionNet < 0) {
                losses++;
                if (sessionNet < biggestLoss) {
                    biggestLoss = sessionNet;
                }
            } else {
                draws++;
            }
        }
    }

    /**
     * 勝率を計算する。
     *
     * @return 勝率（0.0 〜 1.0）。対戦なしの場合は 0.0
     */
    public double getWinRate() {
        int total = wins + losses + draws;
        return total == 0 ? 0.0 : (double) wins / total;
    }

    // ── 永続化 ──

    /**
     * 設定セクションに統計データを書き出す。
     *
     * @param section 書き出し先の {@code players.<UUID>} セクション
     */
    public void saveTo(ConfigurationSection section) {
        section.set("name", name);
        ConfigurationSection stats = section.createSection("stats");
        stats.set("sessions", totalSessions);
        stats.set("purchases", totalPurchases);
        stats.set("cashouts", totalCashouts);
        stats.set("profit", netProfit);
        stats.set("wins", wins);
        stats.set("losses", losses);
        stats.set("draws", draws);
        stats.set("max-win", biggestWin);
        stats.set("max-loss", biggestLoss);
        stats.set("first-play", firstPlayed != null ? firstPlayed.format(FORMATTER) : null);
        stats.set("last-play", lastPlayed != null ? lastPlayed.format(FORMATTER) : null);
    }

    /**
     * 設定セクションから統計データを読み込む。
     *
     * @param section 読み込み元の {@code players.<UUID>} セクション
     * @return 読み込まれた PlayerStats インスタンス
     */
    public static PlayerStats loadFrom(ConfigurationSection section) {
        PlayerStats ps = new PlayerStats();
        ps.name = section.getString("name", "???");
        ConfigurationSection stats = section.getConfigurationSection("stats");
        if (stats != null) {
            ps.totalSessions = stats.getInt("sessions", 0);
            ps.totalPurchases = stats.getLong("purchases", 0);
            ps.totalCashouts = stats.getLong("cashouts", 0);
            ps.netProfit = stats.getLong("profit", 0);
            ps.wins = stats.getInt("wins", 0);
            ps.losses = stats.getInt("losses", 0);
            ps.draws = stats.getInt("draws", 0);
            ps.biggestWin = stats.getLong("max-win", 0);
            ps.biggestLoss = stats.getLong("max-loss", 0);
            String fp = stats.getString("first-play", null);
            if (fp != null) {
                try {
                    ps.firstPlayed = LocalDateTime.parse(fp, FORMATTER);
                } catch (java.time.format.DateTimeParseException e) {
                    // データ破損時はnullのまま
                }
            }
            String lp = stats.getString("last-play", null);
            if (lp != null) {
                try {
                    ps.lastPlayed = LocalDateTime.parse(lp, FORMATTER);
                } catch (java.time.format.DateTimeParseException e) {
                    // データ破損時はnullのまま
                }
            }
        }
        return ps;
    }

    // ── StorageProvider 連携 ──

    /**
     * {@link PlayerStatsSnapshot} から {@link PlayerStats} を復元する。
     *
     * <p>StorageProvider（Redis / YAML）から取得したスナップショットを
     * CasinoCore 内部の PlayerStats インスタンスに変換する。
     *
     * @param snapshot 変換元のスナップショット
     * @return 復元された PlayerStats インスタンス
     */
    public static PlayerStats fromSnapshot(PlayerStatsSnapshot snapshot) {
        PlayerStats ps = new PlayerStats();
        ps.name = snapshot.name();
        ps.totalSessions = snapshot.totalSessions();
        ps.totalPurchases = snapshot.totalPurchases();
        ps.totalCashouts = snapshot.totalCashouts();
        ps.netProfit = snapshot.netProfit();
        ps.wins = snapshot.wins();
        ps.losses = snapshot.losses();
        ps.draws = snapshot.draws();
        ps.biggestWin = snapshot.biggestWin();
        ps.biggestLoss = snapshot.biggestLoss();
        if (snapshot.firstPlayed() != null) {
            try { ps.firstPlayed = LocalDateTime.parse(snapshot.firstPlayed(), FORMATTER); }
            catch (Exception ignored) {}
        }
        if (snapshot.lastPlayed() != null) {
            try { ps.lastPlayed = LocalDateTime.parse(snapshot.lastPlayed(), FORMATTER); }
            catch (Exception ignored) {}
        }
        return ps;
    }

    /**
     * この {@link PlayerStats} を {@link PlayerStatsSnapshot} に変換する。
     *
     * <p>StorageProvider への書き込み時に使用される。
     *
     * @return 変換されたスナップショット
     */
    public PlayerStatsSnapshot toSnapshot() {
        return new PlayerStatsSnapshot(
            name, totalSessions, totalPurchases, totalCashouts,
            netProfit, wins, losses, draws, biggestWin, biggestLoss,
            firstPlayed != null ? firstPlayed.format(FORMATTER) : null,
            lastPlayed != null ? lastPlayed.format(FORMATTER) : null
        );
    }
}

