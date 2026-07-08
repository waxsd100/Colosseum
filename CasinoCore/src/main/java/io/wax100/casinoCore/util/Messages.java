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

    /**
     * プレイヤーが見つからない場合のメッセージテンプレート（%s にプレイヤー名）
     */
    public static final String PLAYER_NOT_FOUND = PREFIX + ChatColor.RED + "プレイヤー '%s' が見つかりません。";

    /**
     * コンソールからプレイヤー名を指定せずに実行した場合
     */
    public static final String SPECIFY_PLAYER = PREFIX + ChatColor.RED + "プレイヤー名を指定してください。";
    /**
     * 権限不足
     */
    public static final String NO_PERMISSION = PREFIX + ChatColor.RED + "このコマンドを実行する権限がありません。";

    // ── カジノモード関連 ──

    /**
     * カジノモードが既にON
     */
    public static final String CASINO_ALREADY_ON = PREFIX + ChatColor.YELLOW + "カジノモードは既にONです。";
    /**
     * カジノモードが既にOFF
     */
    public static final String CASINO_ALREADY_OFF = PREFIX + ChatColor.YELLOW + "カジノモードは既にOFFです。";
    /**
     * プレイヤーが既にカジノモードに参加中（%s にプレイヤー名）
     */
    public static final String PLAYER_ALREADY_IN_CASINO = PREFIX + ChatColor.YELLOW + "%s は既にカジノモードに参加しています。";
    /**
     * プレイヤーがカジノモードに未参加（%s にプレイヤー名）
     */
    public static final String PLAYER_NOT_IN_CASINO = PREFIX + ChatColor.YELLOW + "%s はカジノモードに参加していません。";
    /**
     * プレイヤーをカジノモードに追加した（%s にプレイヤー名）
     */
    public static final String PLAYER_ADDED = PREFIX + ChatColor.GREEN + "%s をカジノモードに追加しました。";
    /**
     * あなたがカジノモードに追加された
     */
    public static final String YOU_ADDED = PREFIX + ChatColor.GREEN + "あなたはカジノモードに追加されました。";
    /**
     * プレイヤーをカジノモードから退出させた（%s にプレイヤー名）
     */
    public static final String PLAYER_REMOVED = PREFIX + ChatColor.GREEN + "%s をカジノモードから退出させました。";
    /**
     * あなたがカジノモードから退出した
     */
    public static final String YOU_REMOVED = PREFIX + ChatColor.GREEN + "あなたはカジノモードから退出しました。";
    /**
     * 統計データがない（%s にプレイヤー名）
     */
    public static final String NO_STATS = PREFIX + ChatColor.YELLOW + "%s の統計データがありません。";
    /**
     * ランキングリセット完了
     */
    public static final String RANKING_RESET = PREFIX + ChatColor.GREEN + "ランキングと全プレイヤー統計をリセットしました。";
    /**
     * ランキングデータがない
     */
    public static final String NO_RANKING_DATA = ChatColor.GRAY + "ランキングデータがありません。";


    // ── カジノリスナー ──

    /**
     * ログイン時カジノON通知
     */
    public static final String JOIN_CASINO_ON = PREFIX + ChatColor.GREEN + "現在カジノモードが "
            + ChatColor.YELLOW + ChatColor.BOLD + "ON " + ChatColor.RESET + ChatColor.GREEN + "です！";

    /**
     * インスタンス化禁止用のプライベートコンストラクタ
     */
    private Messages() {
    }
}
