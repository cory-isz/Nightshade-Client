package com.nightshade.util;

import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Test instrumentation: records each time a module actually changes the game (a frame cap lowered, a vignette
 * removed, a shield tinted, a slot switched...). Disabled unless the JVM runs with -Dnightshade.probe=true, so
 * release builds pay nothing but a static boolean check.
 */
public final class EffectProbe {
    public static final boolean ENABLED = Boolean.getBoolean("nightshade.probe");
    private static final Map<String, AtomicLong> COUNTS = new ConcurrentHashMap<>();

    private EffectProbe() {
    }

    public static void hit(String module) {
        if (ENABLED) {
            COUNTS.computeIfAbsent(module, k -> new AtomicLong()).incrementAndGet();
        }
    }

    public static long count(String module) {
        AtomicLong n = COUNTS.get(module);
        return n == null ? 0L : n.get();
    }

    public static Map<String, Long> snapshot() {
        Map<String, Long> out = new TreeMap<>();
        COUNTS.forEach((k, v) -> out.put(k, v.get()));
        return out;
    }
}
