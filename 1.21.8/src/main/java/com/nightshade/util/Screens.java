package com.nightshade.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/** The open screen, and opening one, written once so features don't care how each game version spells it. */
public final class Screens {
    private Screens() {
    }

    public static Screen current() {
        return Minecraft.getInstance().screen;
    }

    public static void open(Screen screen) {
        Minecraft.getInstance().setScreen(screen);
    }
}
