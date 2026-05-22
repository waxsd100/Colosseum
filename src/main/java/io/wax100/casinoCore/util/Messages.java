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

    /**
     * インスタンス化禁止用のプライベートコンストラクタ
     */
    private Messages() {
    }
}
