package com.nightshade.event;

/** Base for events whose default behaviour can be suppressed by a listener. */
public abstract class Cancellable {
    private boolean cancelled;

    public boolean isCancelled() {
        return cancelled;
    }

    public void cancel() {
        cancelled = true;
    }

    protected void resetCancelled() {
        cancelled = false;
    }
}
