package io.wax100.arenaCore.event;

import io.wax100.arenaCore.model.ArenaSession;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.Objects;

/**
 * 賭け受付が開始されるときに発火するイベント。
 *
 * <p>このイベントは {@link Cancellable} であり、リスナーがキャンセルすると
 * 賭け受付への遷移が中止される。
 */
public class ArenaBettingOpenEvent extends Event implements Cancellable {

    private static final HandlerList HANDLER_LIST = new HandlerList();

    private final ArenaSession session;
    private boolean cancelled;

    /**
     * @param session 対象セッション（null不可）
     */
    public ArenaBettingOpenEvent(ArenaSession session) {
        this.session = Objects.requireNonNull(session, "session must not be null");
    }

    /** セッションを返す。 */
    public ArenaSession getSession() {
        return session;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLER_LIST;
    }

    public static HandlerList getHandlerList() {
        return HANDLER_LIST;
    }
}
