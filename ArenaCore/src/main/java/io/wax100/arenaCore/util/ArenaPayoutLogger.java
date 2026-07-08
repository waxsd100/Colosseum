package io.wax100.arenaCore.util;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * アリーナ配当専用のファイルロガー。
 *
 * <p>配当分配・ゲームモード復元・ジャックポット等のイベントを
 * 日付別ログファイル ({@code Logs/payout-YYYY-MM-DD.log}) に記録する。
 * バグ発生時の調査・差し戻しに使用する。
 */
public class ArenaPayoutLogger {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final File logsDir;
    private final Logger serverLogger;

    /**
     * ロガーを初期化する。
     *
     * @param dataFolder プラグインのデータフォルダ
     * @param serverLogger サーバーロガー（IO エラー通知用）
     */
    public ArenaPayoutLogger(File dataFolder, Logger serverLogger) {
        this.logsDir = new File(dataFolder, "Logs");
        this.serverLogger = serverLogger;
        if (!logsDir.exists()) {
            logsDir.mkdirs();
        }
    }

    // ── パブリック API ──

    /**
     * 天引き分配計算の結果を記録する。
     *
     * @param totalPool  総ベットプール
     * @param loserShare 敗者闘技者還元額
     * @param winnerShare 勝者闘技者還元額
     * @param houseFee   運営手数料
     * @param bettorPool 観客配当プール
     */
    public void logDistribution(long totalPool, long loserShare,
                                long winnerShare, long houseFee, long bettorPool) {
        write("DISTRIBUTION", "総プール: " + fmt(totalPool) + " E"
                + " | 敗者還元: " + fmt(loserShare) + " E"
                + " | 勝者還元: " + fmt(winnerShare) + " E"
                + " | 手数料: " + fmt(houseFee) + " E"
                + " | 観客配当: " + fmt(bettorPool) + " E");
    }

    /**
     * 闘技者への報酬を記録する。
     *
     * @param playerName プレイヤー名
     * @param playerId   プレイヤー UUID
     * @param amount     金額
     * @param type       種別（例: "勝者還元", "最低保証金", "参加費払い戻し"）
     */
    public void logFighterPayout(String playerName, UUID playerId, long amount, String type) {
        write("FIGHTER", type + ": " + playerName + " (" + playerId + ") → " + fmt(amount) + " E");
    }

    /**
     * ベッター（観客）への配当を記録する。
     *
     * @param playerName プレイヤー名
     * @param playerId   プレイヤー UUID
     * @param amount     配当額
     * @param betAmount  ベット額
     * @param teamName   ベットしたチーム名
     */
    public void logBettorPayout(String playerName, UUID playerId,
                                long amount, long betAmount, String teamName) {
        write("BETTOR", "配当: " + playerName + " (" + playerId + ") → " + fmt(amount) + " E"
                + " (ベット額: " + fmt(betAmount) + " E / チーム: " + teamName + ")");
    }

    /**
     * 運営手数料の処理を記録する。
     *
     * @param amount        金額
     * @param recipientName 振込先プレイヤー名（null/空 = ジャックポットのみ）
     */
    public void logHouseFee(long amount, String recipientName) {
        if (recipientName != null && !recipientName.isEmpty()) {
            write("HOUSE_FEE", "運営手数料: " + fmt(amount) + " E → " + recipientName + " + ジャックポット");
        } else {
            write("HOUSE_FEE", "運営手数料: " + fmt(amount) + " E → ジャックポット");
        }
    }

    /**
     * ジャックポットイベントを記録する。
     *
     * @param eventType イベント種別（"積立", "発動", "引出"）
     * @param amount    金額
     */
    public void logJackpot(String eventType, long amount) {
        write("JACKPOT", eventType + ": " + fmt(amount) + " E");
    }

    /**
     * ゲームモード復元を記録する。
     *
     * @param playerName プレイヤー名
     * @param playerId   プレイヤー UUID
     * @param fromMode   変更前のモード
     * @param toMode     変更後のモード
     */
    public void logGameModeRestore(String playerName, UUID playerId,
                                   String fromMode, String toMode) {
        write("GAMEMODE", playerName + " (" + playerId + "): " + fromMode + " → " + toMode);
    }

    /**
     * セッションイベントを記録する。
     *
     * @param eventType イベント種別（"開始", "終了", "キャンセル", "勝者決定"）
     * @param details   詳細
     */
    public void logSessionEvent(String eventType, String details) {
        write("SESSION", eventType + ": " + details);
    }

    /**
     * 返金を記録する。
     *
     * @param playerName プレイヤー名
     * @param playerId   プレイヤー UUID
     * @param amount     金額
     * @param reason     理由
     */
    public void logRefund(String playerName, UUID playerId, long amount, String reason) {
        write("REFUND", reason + ": " + playerName + " (" + playerId + ") → " + fmt(amount) + " E");
    }

    // ── 内部メソッド ──

    /**
     * 金額をカンマ区切りでフォーマットする。
     */
    private static String fmt(long amount) {
        return String.format("%,d", amount);
    }

    /**
     * ログファイルに1行書き込む。
     *
     * @param eventType イベント種別タグ
     * @param message   メッセージ
     */
    private synchronized void write(String eventType, String message) {
        String fileName = "payout-" + LocalDate.now().format(DATE_FMT) + ".log";
        File logFile = new File(logsDir, fileName);

        try (PrintWriter pw = new PrintWriter(new FileWriter(logFile, true))) {
            String time = LocalTime.now().format(TIME_FMT);
            pw.println("[" + time + "] [" + eventType + "] " + message);
        } catch (IOException e) {
            serverLogger.warning("配当ログ書き込みエラー: " + e.getMessage());
        }
    }
}
