package com.nightshade.event;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;

/**
 * Lightweight synchronous event bus.
 * <p>
 * Listeners are stored per concrete event class in copy-on-write lists, so dispatch never allocates and
 * listeners may (un)subscribe during dispatch. Every listener invocation is isolated: an exception thrown by one
 * listener is routed to the error handler (which disables the offending module) and never propagates into
 * Minecraft code.
 */
public final class EventManager {
    private final Map<Class<?>, CopyOnWriteArrayList<Subscription<?>>> listeners = new ConcurrentHashMap<>();
    private final BiConsumer<Object, Throwable> errorHandler;

    public EventManager(BiConsumer<Object, Throwable> errorHandler) {
        this.errorHandler = errorHandler;
    }

    public <E> Subscription<E> subscribe(Class<E> type, Listener<E> listener, Object owner) {
        Subscription<E> subscription = new Subscription<>(type, listener, owner);
        listeners.computeIfAbsent(type, k -> new CopyOnWriteArrayList<>()).add(subscription);
        return subscription;
    }

    public void unsubscribe(Subscription<?> subscription) {
        List<Subscription<?>> list = listeners.get(subscription.type());
        if (list != null) {
            list.remove(subscription);
        }
    }

    /** Number of live subscriptions registered by {@code owner} (used to verify clean unsubscription). */
    public int subscriptionCount(Object owner) {
        int count = 0;
        for (List<Subscription<?>> list : listeners.values()) {
            for (Subscription<?> subscription : list) {
                if (subscription.owner() == owner) {
                    count++;
                }
            }
        }
        return count;
    }

    public boolean hasListeners(Class<?> type) {
        List<Subscription<?>> list = listeners.get(type);
        return list != null && !list.isEmpty();
    }

    @SuppressWarnings("unchecked")
    public <E> E post(E event) {
        List<Subscription<?>> list = listeners.get(event.getClass());
        if (list == null || list.isEmpty()) {
            return event;
        }
        for (Subscription<?> raw : list) {
            Subscription<E> subscription = (Subscription<E>) raw;
            try {
                subscription.listener().handle(event);
            } catch (Throwable t) {
                errorHandler.accept(subscription.owner(), t);
            }
        }
        return event;
    }

    public record Subscription<E>(Class<E> type, Listener<E> listener, Object owner) {
    }
}
