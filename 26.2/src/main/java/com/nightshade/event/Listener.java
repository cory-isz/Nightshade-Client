package com.nightshade.event;

/** Functional listener for a single event type. */
@FunctionalInterface
public interface Listener<E> {
    void handle(E event);
}
