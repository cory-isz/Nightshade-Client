package com.nightshade.compat;

import com.nightshade.util.Log;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.screens.Screen;

import java.lang.reflect.Method;
import java.util.function.Supplier;

/**
 * Sodium support for the Nightshade menus. Sodium hooks the vanilla Options screen's "Video Settings" button to open
 * its own video settings; Nightshade replaces that screen, so the Video tile asks Sodium directly (by reflection, so
 * Nightshade neither needs nor bundles Sodium).
 */
public final class SodiumCompat {
    private static final boolean LOADED = FabricLoader.getInstance().isModLoaded("sodium");
    private static Method createScreen;
    private static boolean failed;

    private SodiumCompat() {
    }

    public static boolean loaded() {
        return LOADED;
    }

    /** Sodium's video settings screen when Sodium is installed, otherwise {@code vanilla}. */
    public static Screen videoSettings(Screen parent, Supplier<Screen> vanilla) {
        if (LOADED && !failed) {
            try {
                if (createScreen == null) {
                    createScreen = Class.forName("net.caffeinemc.mods.sodium.client.gui.VideoSettingsScreen")
                            .getMethod("createScreen", Screen.class);
                }
                Object screen = createScreen.invoke(null, parent);
                if (screen instanceof Screen s) {
                    return s;
                }
            } catch (ReflectiveOperationException | LinkageError e) {
                failed = true;
                Log.warn("Could not open Sodium's video settings, using the vanilla screen: {}", e.toString());
            }
        }
        return vanilla.get();
    }
}
