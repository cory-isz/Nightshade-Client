package com.nightshade.module.hud;

import java.util.Locale;

/** A stopwatch: start, stop and reset it with the "Stopwatch Key" (Utility). */
public final class StopwatchHud extends SimpleTextHud {
    private static long startedAt;
    private static long elapsed;
    private static boolean running;

    public StopwatchHud() {
        super("Stopwatch", "A stopwatch - start/stop/reset it with the Stopwatch Key", "Time ", 0.0, 0.62);
    }

    @Override
    public boolean shouldRender() {
        return true;
    }

    /** Running -> stop; stopped with time -> reset; reset -> start. */
    public static void press() {
        if (running) {
            elapsed += System.currentTimeMillis() - startedAt;
            running = false;
        } else if (elapsed > 0) {
            elapsed = 0;
        } else {
            startedAt = System.currentTimeMillis();
            running = true;
        }
    }

    public static long millis() {
        return elapsed + (running ? System.currentTimeMillis() - startedAt : 0);
    }

    @Override
    protected void refresh() {
        long ms = millis();
        value = String.format(Locale.ROOT, "%d:%02d.%d", ms / 60000, ms / 1000 % 60, ms / 100 % 10) + (running ? "" : ms > 0 ? "  (stopped)" : "");
    }
}
