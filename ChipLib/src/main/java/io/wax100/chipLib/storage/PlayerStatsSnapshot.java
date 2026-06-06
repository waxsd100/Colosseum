package io.wax100.chipLib.storage;

import org.jetbrains.annotations.Nullable;

/**
 * プレイヤーの統計データを保持する不変レコード。
 *
 * <p>モジュール間（CasinoCore / ArenaCore ↔ ChipLib）でプレイヤーの統計情報を
 * シリアライズ可能な形で受け渡すためのスナップショット。
 *
 * <p>日時フィールドは {@link java.time.format.DateTimeFormatter#ISO_LOCAL_DATE_TIME}
 * 形式の文字列で、{@code null} は「記録なし」を意味する。
 *
 * @param name           プレイヤー名
 * @param totalSessions  累計セッション数
 * @param totalPurchases 累計チップ購入額
 * @param totalCashouts  累計チップ換金額
 * @param netProfit      累計純損益
 * @param wins           勝利回数
 * @param losses         敗北回数
 * @param draws          引き分け回数
 * @param biggestWin     最大勝利額
 * @param biggestLoss    最大敗北額
 * @param firstPlayed    初回プレイ日時（ISO_LOCAL_DATE_TIME 形式、nullable）
 * @param lastPlayed     最終プレイ日時（ISO_LOCAL_DATE_TIME 形式、nullable）
 * @author wax100
 */
public record PlayerStatsSnapshot(
        String name,
        int totalSessions,
        long totalPurchases,
        long totalCashouts,
        long netProfit,
        int wins,
        int losses,
        int draws,
        long biggestWin,
        long biggestLoss,
        @Nullable String firstPlayed,
        @Nullable String lastPlayed
) {}
