package com.nightshade.util;

import net.minecraft.client.resources.language.I18n;

/** Translation helper for the client's own screens (assets/nightshade/lang/*.json). */
public final class Lang {
    private Lang() {
    }

    public static String t(String key, Object... args) {
        return I18n.get("nightshade." + key, args);
    }
}
