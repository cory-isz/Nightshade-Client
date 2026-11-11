package com.nightshade;

import java.io.InputStream;
import java.util.Properties;

/** Build edition switches, read from assets/nightshade/edition.properties (written by Gradle). */
public final class Edition {
    private static final Properties PROPS = new Properties();

    static {
        try (InputStream in = Edition.class.getResourceAsStream("/assets/nightshade/edition.properties")) {
            if (in != null) {
                PROPS.load(in);
            }
        } catch (Exception ignored) {
            // defaults below
        }
    }

    private Edition() {
    }

    /** "full" or "noauto". */
    public static String name() {
        return PROPS.getProperty("edition", "noauto");
    }

    /** Microsoft app id for in-game sign-in, or empty when this edition has no Microsoft sign-in. */
    public static String microsoftClientId() {
        return PROPS.getProperty("microsoftClientId", "").trim();
    }

    /** Whether "Add Microsoft account" is available (the launcher account and offline accounts always are). */
    public static boolean microsoftSignIn() {
        return !microsoftClientId().isEmpty();
    }
}
