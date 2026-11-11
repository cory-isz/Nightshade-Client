package com.nightshade.util;

import java.util.Arrays;

/**
 * Records real frame durations so performance changes can be measured instead of assumed. Keeps the last
 * {@value #SAMPLES} frames in a ring buffer; statistics are computed on demand into a reused scratch array.
 */
public final class FrameTimeTracker {
    private static final int SAMPLES = 600;
    private final long[] frames = new long[SAMPLES];
    private final long[] scratch = new long[SAMPLES];
    private int head;
    private int size;
    private long lastFrameNanos = -1L;

    public void onFrame() {
        long now = System.nanoTime();
        if (lastFrameNanos >= 0L) {
            long duration = now - lastFrameNanos;
            // Ignore pauses longer than one second (alt-tab, loading screens).
            if (duration < 1_000_000_000L) {
                frames[head] = duration;
                head = (head + 1) % SAMPLES;
                size = Math.min(SAMPLES, size + 1);
            }
        }
        lastFrameNanos = now;
    }

    public void reset() {
        head = 0;
        size = 0;
        lastFrameNanos = -1L;
    }

    public int sampleCount() {
        return size;
    }

    /** Fills {@code out} with: average fps, 1% low fps, average frame ms, worst frame ms. */
    public void stats(double[] out) {
        if (size == 0) {
            Arrays.fill(out, 0.0);
            return;
        }
        long total = 0L;
        for (int i = 0; i < size; i++) {
            scratch[i] = frames[i];
            total += frames[i];
        }
        Arrays.sort(scratch, 0, size);
        double avgNanos = total / (double) size;
        int lowCount = Math.max(1, size / 100);
        long lowTotal = 0L;
        for (int i = size - lowCount; i < size; i++) {
            lowTotal += scratch[i];
        }
        double lowNanos = lowTotal / (double) lowCount;
        out[0] = 1_000_000_000.0 / avgNanos;
        out[1] = 1_000_000_000.0 / lowNanos;
        out[2] = avgNanos / 1_000_000.0;
        out[3] = scratch[size - 1] / 1_000_000.0;
    }
}
