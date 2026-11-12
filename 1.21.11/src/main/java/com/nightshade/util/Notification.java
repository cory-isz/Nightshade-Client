package com.nightshade.util;

import com.nightshade.render.Animation;

/** A transient toast shown in the corner of the screen. */
public final class Notification {
    public enum Type {
        INFO, SUCCESS, WARNING, ERROR
    }

    private final String title;
    private final String message;
    private final Type type;
    private long createdMillis;
    private int count = 1;
    private final Animation offset = new Animation(16f, -1f);
    private final long durationMillis;
    private final Animation slide = new Animation(14f, 0f);

    public Notification(String title, String message, Type type, long durationMillis) {
        this.title = title;
        this.message = message;
        this.type = type;
        this.durationMillis = durationMillis;
        this.createdMillis = System.currentTimeMillis();
        slide.setTarget(1f);
    }

    public String title() {
        return title;
    }

    public String message() {
        return message;
    }

    public Type type() {
        return type;
    }

    public Animation slide() {
        return slide;
    }

    /** Animated vertical position in the stack (-1 until first placed). */
    public Animation offset() {
        return offset;
    }

    /** How many identical toasts this one stands for ("x2"). */
    public int count() {
        return count;
    }

    /** The same toast was pushed again: count it and restart its timer instead of adding a copy. */
    public void repeat() {
        count++;
        createdMillis = System.currentTimeMillis();
        slide.setTarget(1f);
    }

    public boolean isExpired(long now) {
        return now - createdMillis > durationMillis;
    }

    /** Remaining lifetime fraction (1 = just created, 0 = expired). */
    public float lifeFraction(long now) {
        return 1f - Math.min(1f, (now - createdMillis) / (float) durationMillis);
    }
}
