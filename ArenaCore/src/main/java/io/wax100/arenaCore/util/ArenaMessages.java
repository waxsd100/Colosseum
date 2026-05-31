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

    /** セッション稼働中 */
    public static final String MSG_SESSION_ALREADY_ACTIVE = "既にセッションが稼働中です。先に /arena cancel で中止してください。";

    /** セッション作成失敗 */
    public static final String MSG_SESSION_CREATE_FAILED = "セッション作成に失敗しました。";

    /** セットアップ中のみ設定可能 */
    public static final String MSG_SETUP_ONLY = "セットアップ中のみ設定可能です。";


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


    /** セットアップ中 or 賭け受付中のみ設定可能 */
    public static final String MSG_SETUP_OR_BETTING_ONLY = "セットアップ中 or 賭け受付中のみ設定できます。";

    // ── 地形復元 ──

    /** 地形追跡開始 */
    public static final String MSG_TERRAIN_TRACKING = PREFIX + ChatColor.GRAY + "地形の追跡を開始しました。";

    /** 地形復元中 */
    public static final String MSG_TERRAIN_FLUSHING = PREFIX + ChatColor.GRAY + "地形を復元中です...";

    /** 地形復元完了 */
    public static final String MSG_TERRAIN_COMPLETE = PREFIX + ChatColor.GREEN + "地形の復元が完了しました。";

    /** 地形復元中でセッション作成不可 */
    public static final String MSG_TERRAIN_BLOCKING = PREFIX + ChatColor.RED + "地形復元中のため、セッションを作成できません。";

    /** 戦闘エリア未設定で地形追跡スキップ */
    public static final String MSG_TERRAIN_NO_FIELD = PREFIX + ChatColor.RED + "戦闘エリアが未設定のため、地形追跡をスキップします。";

    // ── 戦闘エリア ──

    /** 戦闘エリア設定完了 */
    public static final String MSG_FIELD_SET = PREFIX + ChatColor.GREEN + "戦闘エリアを設定し、地形を保存しました。";

    /** 戦闘エリア未設定 */
    public static final String MSG_FIELD_NOT_SET = PREFIX + ChatColor.RED + "戦闘エリアが未設定です。/arena field set で設定してください。";

    /** 戦闘エリアサイズ警告（フォーマット用、%d = ブロック数） */
    public static final String MSG_FIELD_TOO_LARGE_FMT = PREFIX + ChatColor.YELLOW + "警告: フィールドが非常に大きいです (%d ブロック)。パフォーマンスに影響する可能性があります。";

    /** 戦闘エリア情報（フォーマット用） */
    public static final String MSG_FIELD_INFO_FMT = PREFIX + ChatColor.GRAY + "戦闘エリア: " + ChatColor.WHITE + "%s " + ChatColor.GRAY + "(%d, %d, %d) → (%d, %d, %d) " + ChatColor.GRAY + "[%d ブロック]";

    // ── プリセット ──

    /** プリセット保存完了 */
    public static final String MSG_PRESET_SAVED = PREFIX + ChatColor.GREEN + "アリーナ設定を保存しました: ";

    /** プリセットロード完了 */
    public static final String MSG_PRESET_LOADED = PREFIX + ChatColor.GREEN + "保存済み設定をロードしました: ";

    /** プリセット削除完了 */
    public static final String MSG_PRESET_DELETED = PREFIX + ChatColor.GREEN + "削除しました: ";

    /** プリセット未発見 */
    public static final String MSG_PRESET_NOT_FOUND = PREFIX + ChatColor.RED + "保存済み設定が見つかりません: ";

    /** プリセット一覧ヘッダー */
    public static final String MSG_PRESET_LIST_HEADER = PREFIX + ChatColor.GRAY + "保存済みアリーナ一覧:";

    /** プリセット一覧空 */
    public static final String MSG_PRESET_LIST_EMPTY = PREFIX + ChatColor.GRAY + "保存済みアリーナはありません。";

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

    /**
     * チーム情報ラベルを生成する（例: "3人" / "[MOB] 5体"）。
     *
     * @param session セッション
     * @param team    チーム名
     * @return フォーマット済みラベル
     */
    public static String formatTeamLabel(io.wax100.arenaCore.model.ArenaSession session, String team) {
        return session.isMobTeam(team)
                ? "[MOB] " + session.getEffectiveTeamSize(team) + "体"
                : session.getTeamSize(team) + "人";
    }
}
