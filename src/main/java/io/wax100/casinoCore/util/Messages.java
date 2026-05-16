package io.wax100.casinoCore.util;

import org.bukkit.ChatColor;

/**
 * メッセージ表示用の共通定数・ユーティリティ
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

    private Messages() {
    }
}
