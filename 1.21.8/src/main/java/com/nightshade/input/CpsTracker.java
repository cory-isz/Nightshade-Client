package com.nightshade.input;

/**
 * Rolling one-second click counter. Click timestamps are kept in a fixed-size ring buffer per button, so tracking
 * never allocates. Counting walks back from the newest entry and stops at the first one older than the window.
 */
public final class CpsTracker {
    private static final long WINDOW_NANOS = 1_000_000_000L;
    private static final int CAPACITY = 128;

    private final long[] left = new long[CAPACITY];
    private final long[] right = new long[CAPACITY];
    private int leftHead;
    private int leftSize;
    private int rightHead;
    private int rightSize;

    public synchronized void recordLeft() {
        left[leftHead] = System.nanoTime();
        leftHead = (leftHead + 1) % CAPACITY;
        leftSize = Math.min(CAPACITY, leftSize + 1);
    }

    public synchronized void recordRight() {
        right[rightHead] = System.nanoTime();
        rightHead = (rightHead + 1) % CAPACITY;
        rightSize = Math.min(CAPACITY, rightSize + 1);
    }

    public synchronized int leftCps() {
        return count(left, leftHead, leftSize);
    }

    public synchronized int rightCps() {
        return count(right, rightHead, rightSize);
    }

    private static int count(long[] buffer, int head, int size) {
        long cutoff = System.nanoTime() - WINDOW_NANOS;
        int count = 0;
        for (int i = 1; i <= size; i++) {
            long timestamp = buffer[Math.floorMod(head - i, CAPACITY)];
            if (timestamp < cutoff) {
                break;
            }
            count++;
        }
        return count;
    }

    /** Clears all history (world change / disconnect). */
    public synchronized void reset() {
        leftHead = leftSize = rightHead = rightSize = 0;
    }
}
