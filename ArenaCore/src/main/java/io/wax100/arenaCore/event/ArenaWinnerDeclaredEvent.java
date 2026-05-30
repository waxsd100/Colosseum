package io.wax100.arenaCore.event;

import io.wax100.arenaCore.model.ArenaSession;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.Objects;

/**
 * 勝者が宣言されたときに発火するイベント。
 *
 * <p>キャンセル不可。配当処理の後に発火される情報通知イベント。
 */
public class ArenaWinnerDeclaredEvent extends Event {

    private static final HandlerList HANDLER_LIST = new HandlerList();

    private final ArenaSession session;
    private final String winningTeam;

    /**
     * @param session     対象セッション（null不可）
     * @param winningTeam 勝利チーム名（null不可）
     */
    public ArenaWinnerDeclaredEvent(ArenaSession session, String winningTeam) {
        this.session = Objects.requireNonNull(session, "session must not be null");
        this.winningTeam = Objects.requireNonNull(winningTeam, "winningTeam must not be null");
    }

    /** セッションを返す。 */
    public ArenaSession getSession() {
        return session;
    }

    /** 勝利チーム名を返す。 */
    public String getWinningTeam() {
        return winningTeam;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLER_LIST;
    }

    public static HandlerList getHandlerList() {
        return HANDLER_LIST;
    }
}
