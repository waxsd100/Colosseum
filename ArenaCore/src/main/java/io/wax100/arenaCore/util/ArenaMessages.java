package io.wax100.arenaCore.util;

import org.bukkit.ChatColor;

/**
 * 闘技場プラグイン用のメッセージ定数。
 *
 * <p>ハードコーディングを排除し、メッセージ変更を一箇所に集約する。
 */
public final class ArenaMessages {

    /** メッセージプレフィックス */
    public static final String PREFIX = ChatColor.DARK_RED + "[" + ChatColor.RED + "闘技場" + ChatColor.DARK_RED + "] " + ChatColor.RESET;

    /** セパレーター */
    public static final String SEPARATOR = ChatColor.DARK_RED + "━━━━━━━━━━━━━━━━━━━━━━━━━━━";

    // ── 共通エラーメッセージ ──

    /** プレイヤー専用コマンド */
    public static final String MSG_PLAYER_ONLY = "プレイヤーのみ使用できます。";

    /** セッションが存在しない */
    public static final String MSG_NO_SESSION = "闘技場セッションがありません。";

    /** WorldEdit が必要 */
    public static final String MSG_WE_REQUIRED = "WorldEdit が必要です。";

    /** WorldEdit 範囲選択が必要 */
    public static final String MSG_WE_SELECT_FIRST = "WorldEdit で範囲を選択してからコマンドを実行してください。";

    /** すでにセッションが存在する */
    public static final String MSG_SESSION_EXISTS = "すでにセッションが存在します。";

    /** 現在の状態では操作不可 */
    public static final String MSG_INVALID_STATE = "現在の状態ではこの操作はできません。";

    /** セッション稼働中 */
    public static final String MSG_SESSION_ALREADY_ACTIVE = "既にセッションが稼働中です。先に /arena cancel で中止してください。";

    /** セッション作成失敗 */
    public static final String MSG_SESSION_CREATE_FAILED = "セッション作成に失敗しました。";

    /** セットアップ中のみ設定可能 */
    public static final String MSG_SETUP_ONLY = "セットアップ中のみ設定可能です。";

    /** セットアップ中のみチーム編集可能 */
    public static final String MSG_SETUP_ONLY_TEAM_EDIT = "セットアップ中のみチーム編集可能です。";

    /** プレイヤーが見つからない（フォーマット用、チーム名の引数あり） */
    public static final String MSG_PLAYER_NOT_FOUND_FMT = "プレイヤー「%s」が見つかりません。";

    /** 追加失敗 */
    public static final String MSG_ADD_FAILED = "追加に失敗しました。";

    /** 待機場未設定（フォーマット用） */
    public static final String MSG_AREA_NOT_SET_FMT = "先に %s で待機場を設定してください。";

    /** 賭け未開始 */
    public static final String MSG_BETTING_NOT_STARTED = "賭けが開始されていません。";

    /** まだ賭けていない */
    public static final String MSG_NO_BET = "まだ賭けていません。";

    /** セットアップ中のみ賭け受付開始可能 */
    public static final String MSG_OPEN_SETUP_ONLY = "セットアップ中のみ賭け受付を開始できます。";

    /** 最低2チームにメンバーが必要 */
    public static final String MSG_MIN_TEAMS_REQUIRED = "最低2チームにメンバーが必要です。";

    /** 賭け受付中のみ試合開始可能 */
    public static final String MSG_START_BETTING_ONLY = "賭け受付中のみ試合を開始できます。";

    /** 試合中のみ勝者宣言可能 */
    public static final String MSG_WIN_ACTIVE_ONLY = "試合中のみ勝者を宣言できます。";

    /** セットアップ中のみ使用可能 */
    public static final String MSG_SETUP_ONLY_USE = "セットアップ中のみ使用できます。";

    /** 選択範囲内にプレイヤーがいない */
    public static final String MSG_NO_PLAYERS_IN_SELECTION = "選択範囲内にプレイヤーがいません。";

    /** セットアップ中 or 賭け受付中のみ設定可能 */
    public static final String MSG_SETUP_OR_BETTING_ONLY = "セットアップ中 or 賭け受付中のみ設定できます。";

    /** チームカラーパレット */
    private static final ChatColor[] TEAM_COLORS = {
            ChatColor.RED, ChatColor.BLUE, ChatColor.GREEN, ChatColor.YELLOW,
            ChatColor.LIGHT_PURPLE, ChatColor.AQUA, ChatColor.GOLD, ChatColor.WHITE
    };

    private ArenaMessages() {}

    /**
     * チームインデックスに対応する色を返す。
     *
     * @param index チームインデックス
     * @return チーム色
     */
    public static ChatColor getTeamColor(int index) {
        return TEAM_COLORS[index % TEAM_COLORS.length];
    }
}
