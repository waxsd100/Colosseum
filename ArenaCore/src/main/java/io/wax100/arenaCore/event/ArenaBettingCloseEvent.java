package io.wax100.arenaCore.event;

import io.wax100.arenaCore.model.ArenaSession;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.Objects;

/**
 * ベット受付が締め切られ、試合が開始されるときに発火するイベント。
 *
 * <p>キャンセル不可。試合開始は不可逆操作である。
 */
public class ArenaBettingCloseEvent extends Event {

    private static final HandlerList HANDLER_LIST = new HandlerList();

    private final ArenaSession session;

    /**
     * @param session 対象セッション（null不可）
     */
    public ArenaBettingCloseEvent(ArenaSession session) {
        this.session = Objects.requireNonNull(session, "session must not be null");
    }

    /** セッションを返す。 */
    public ArenaSession getSession() {
        return session;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLER_LIST;
    }

    public static HandlerList getHandlerList() {
        return HANDLER_LIST;
    }
}
