package io.wax100.arenaCore.util;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.UUID;
import java.util.function.Consumer;

/**
 * プレイヤー操作のユーティリティ。
 */
public final class Players {

    private Players() {}

    /**
     * プレイヤーがオンラインの場合にアクションを実行する。
     *
     * <p>パターン {@code Player p = Bukkit.getPlayer(id); if (p != null && p.isOnline()) ...}
     * を1行にまとめる。
     *
     * @param playerId プレイヤーUUID
     * @param action   オンライン時に実行するアクション
     */
    public static void ifOnline(UUID playerId, Consumer<Player> action) {
        Player player = Bukkit.getPlayer(playerId);
        if (player != null && player.isOnline()) {
            action.accept(player);
        }
    }

    /**
     * プレイヤーがオンラインかどうかを返す。
     *
     * @param playerId プレイヤーUUID
     * @return オンラインなら true
     */
    public static boolean isOnline(UUID playerId) {
        Player player = Bukkit.getPlayer(playerId);
        return player != null && player.isOnline();
    }
}
