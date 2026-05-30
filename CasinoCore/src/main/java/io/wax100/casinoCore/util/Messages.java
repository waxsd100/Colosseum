package io.wax100.casinoCore.util;

import org.bukkit.ChatColor;

/**
 * メッセージ表示用の共通定数クラス。
 *
 * <p>チャットプレフィックスやセパレータラインなど、
 * プラグイン全体で共通して使用するメッセージ定数を定義する。
 * インスタンス化不可のユーティリティクラス。
 */
public final class Messages {

    /**
     * チャットプレフィックス
     */
    public static final String PREFIX = ChatColor.GOLD + "" + ChatColor.BOLD + "[Casino] " + ChatColor.RESET;
    /**
     * セパレータライン
     */
    public static final String SEPARATOR = ChatColor.GOLD + "-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-";

    // ── 共通エラーメッセージ ──

    /** プレイヤーが見つからない場合のメッセージテンプレート（%s にプレイヤー名） */
    public static final String PLAYER_NOT_FOUND = PREFIX + ChatColor.RED + "プレイヤー '%s' が見つかりません。";
    /** コンソールからプレイヤー専用コマンドを実行した場合 */
    public static final String PLAYER_ONLY = PREFIX + ChatColor.RED + "このコマンドはプレイヤーのみ使用できます。";
    /** コンソールからプレイヤー名を指定せずに実行した場合 */
    public static final String SPECIFY_PLAYER = PREFIX + ChatColor.RED + "プレイヤー名を指定してください。";
    /** 権限不足 */
    public static final String NO_PERMISSION = PREFIX + ChatColor.RED + "このコマンドを実行する権限がありません。";

    // ── カジノモード関連 ──

    /** カジノモードが既にON */
    public static final String CASINO_ALREADY_ON = PREFIX + ChatColor.YELLOW + "カジノモードは既にONです。";
    /** カジノモードが既にOFF */
    public static final String CASINO_ALREADY_OFF = PREFIX + ChatColor.YELLOW + "カジノモードは既にOFFです。";
    /** プレイヤーが既にカジノモードに参加中（%s にプレイヤー名） */
    public static final String PLAYER_ALREADY_IN_CASINO = PREFIX + ChatColor.YELLOW + "%s は既にカジノモードに参加しています。";
    /** プレイヤーがカジノモードに未参加（%s にプレイヤー名） */
    public static final String PLAYER_NOT_IN_CASINO = PREFIX + ChatColor.YELLOW + "%s はカジノモードに参加していません。";
    /** プレイヤーをカジノモードに追加した（%s にプレイヤー名） */
    public static final String PLAYER_ADDED = PREFIX + ChatColor.GREEN + "%s をカジノモードに追加しました。";
    /** あなたがカジノモードに追加された */
    public static final String YOU_ADDED = PREFIX + ChatColor.GREEN + "あなたはカジノモードに追加されました。";
    /** プレイヤーをカジノモードから退出させた（%s にプレイヤー名） */
    public static final String PLAYER_REMOVED = PREFIX + ChatColor.GREEN + "%s をカジノモードから退出させました。";
    /** あなたがカジノモードから退出した */
    public static final String YOU_REMOVED = PREFIX + ChatColor.GREEN + "あなたはカジノモードから退出しました。";
    /** 統計データがない（%s にプレイヤー名） */
    public static final String NO_STATS = PREFIX + ChatColor.YELLOW + "%s の統計データがありません。";
    /** ランキングリセット完了 */
    public static final String RANKING_RESET = PREFIX + ChatColor.GREEN + "ランキングと全プレイヤー統計をリセットしました。";
    /** ランキングデータがない */
    public static final String NO_RANKING_DATA = ChatColor.GRAY + "ランキングデータがありません。";

    // ── チップ関連 ──

    /** カジノモードに参加していないため購入不可 */
    public static final String NOT_IN_CASINO_BUY = PREFIX + ChatColor.RED + "カジノモードに参加していないため、チップを購入できません。";
    /** カジノモードに参加していないため換金不可 */
    public static final String NOT_IN_CASINO_CASHOUT = PREFIX + ChatColor.RED + "カジノモードに参加していないため、換金できません。";
    /** 換金できるチップがない */
    public static final String NO_CHIPS_TO_CASHOUT = PREFIX + ChatColor.YELLOW + "換金できるチップがありません。";
    /** 無効な額面 */
    public static final String INVALID_DENOMINATION = PREFIX + ChatColor.RED + "無効な額面です。/chip info で有効な額面を確認してください。";
    /** 枚数が不正 */
    public static final String INVALID_COUNT = PREFIX + ChatColor.RED + "枚数は1以上を指定してください。";
    /** 金額が不正 */
    public static final String INVALID_AMOUNT = PREFIX + ChatColor.RED + "金額は1以上を指定してください。";
    /** チップに変換できない金額 */
    public static final String CANNOT_CONVERT = PREFIX + ChatColor.RED + "指定した金額ではチップに変換できません。";
    /** チップを持っていない */
    public static final String NO_CHIPS = ChatColor.GRAY + "  チップを持っていません。";
    /** 金額オーバーフロー */
    public static final String AMOUNT_OVERFLOW = PREFIX + ChatColor.RED + "金額が大きすぎます。";
    /** 最大購入額超過 */
    public static final String MAX_BUY_EXCEEDED = PREFIX + ChatColor.RED + "最大購入額を超えています。";
    /** 所持金不足 */
    public static final String INSUFFICIENT_FUNDS = PREFIX + ChatColor.RED + "所持金が足りません。";
    /** インベントリ空き不足 */
    public static final String INVENTORY_FULL = PREFIX + ChatColor.RED + "インベントリに空きがありません。";

    // ── カジノリスナー ──

    /** ログイン時カジノON通知 */
    public static final String JOIN_CASINO_ON = PREFIX + ChatColor.GREEN + "現在カジノモードが "
            + ChatColor.YELLOW + ChatColor.BOLD + "ON " + ChatColor.RESET + ChatColor.GREEN + "です！";

    /**
     * インスタンス化禁止用のプライベートコンストラクタ
     */
    private Messages() {
    }
}
