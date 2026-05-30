package io.wax100.arenaCore.util;

import org.bukkit.ChatColor;

/**
 * 闘技場プラグイン用のメッセージ定数。
 */
public final class ArenaMessages {

    /** メッセージプレフィックス */
    public static final String PREFIX = ChatColor.DARK_RED + "[" + ChatColor.RED + "闘技場" + ChatColor.DARK_RED + "] " + ChatColor.RESET;

    /** セパレーター */
    public static final String SEPARATOR = ChatColor.DARK_RED + "━━━━━━━━━━━━━━━━━━━━━━━━━━━";

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
