package io.wax100.chipLib.util;

import org.bukkit.ChatColor;

/**
 * チップコマンド用メッセージ定数クラス。
 *
 * <p>CasinoCore の {@code Messages} からチップ関連の定数を抽出したもの。
 * ChipLib はカジノ全体の状態管理を持たないため、カジノモード関連の定数は含まない。
 * インスタンス化不可のユーティリティクラス。
 */
public final class ChipMessages {

    /**
     * チャットプレフィックス
     */
    public static final String PREFIX = ChatColor.GOLD + "" + ChatColor.BOLD + "[Casino] " + ChatColor.RESET;

    /**
     * セパレータライン
     */
    public static final String SEPARATOR = ChatColor.GOLD + "-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-";

    // ── 共通エラーメッセージ ──

    /** コンソールからプレイヤー専用コマンドを実行した場合 */
    public static final String PLAYER_ONLY = PREFIX + ChatColor.RED + "このコマンドはプレイヤーのみ使用できます。";

    // ── チップ関連 ──

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



    /**
     * インスタンス化禁止用のプライベートコンストラクタ
     */
    private ChipMessages() {
    }
}
