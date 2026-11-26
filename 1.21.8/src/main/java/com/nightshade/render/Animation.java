package com.nightshade.render;

/**
 * Frame-rate independent exponential easing towards a target value. Uses real elapsed time so animations look
 * identical at any FPS, scaled by the global animation speed from the theme.
 */
public final class Animation {
    private final float speed;
    private float value;
    private float target;
    private long lastNanos = -1L;

    public Animation(float speed, float initial) {
        this.speed = speed;
        this.value = initial;
        this.target = initial;
    }

    public Animation setTarget(float target) {
        this.target = target;
        return this;
    }

    public float getTarget() {
        return target;
    }

    public void snap(float newValue) {
        value = newValue;
        target = newValue;
        lastNanos = -1L;
    }

    /** Advances the animation using wall-clock time and returns the current value. */
    public float update() {
        long now = System.nanoTime();
        if (lastNanos < 0L) {
            lastNanos = now;
            return value;
        }
        float dt = Math.min(0.25f, (now - lastNanos) / 1_000_000_000f);
        lastNanos = now;
        float factor = 1f - (float) Math.exp(-speed * Theme.animationSpeed() * dt);
        value += (target - value) * factor;
        if (Math.abs(target - value) < 0.001f) {
            value = target;
        }
        return value;
    }

    public float get() {
        return value;
    }
}
