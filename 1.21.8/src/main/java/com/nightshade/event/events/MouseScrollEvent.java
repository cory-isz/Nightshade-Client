package com.nightshade.event.events;

import com.nightshade.event.Cancellable;

/** Mouse wheel input while no screen is open. Cancelling it prevents vanilla hotbar scrolling. */
public final class MouseScrollEvent extends Cancellable {
    private final double horizontal;
    private final double vertical;

    public MouseScrollEvent(double horizontal, double vertical) {
        this.horizontal = horizontal;
        this.vertical = vertical;
    }

    public double horizontal() {
        return horizontal;
    }

    public double vertical() {
        return vertical;
    }
}
