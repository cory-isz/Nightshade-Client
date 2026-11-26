package com.nightshade.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Client-wide logger. */
public final class Log {
    public static final Logger LOGGER = LoggerFactory.getLogger("Nightshade");

    private Log() {
    }

    public static void info(String message, Object... args) {
        LOGGER.info(message, args);
    }

    public static void warn(String message, Object... args) {
        LOGGER.warn(message, args);
    }

    public static void error(String message, Throwable throwable) {
        LOGGER.error(message, throwable);
    }
}
