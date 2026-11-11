package com.nightshade.module.utility;

import com.nightshade.module.Module;
import com.nightshade.module.ModuleCategory;
import com.nightshade.module.hud.StopwatchHud;

/** Keybind action for the Stopwatch HUD: start, stop, reset. */
public final class StopwatchKey extends Module {
    public StopwatchKey() {
        super("Stopwatch Key", "Starts, stops and resets the Stopwatch HUD", ModuleCategory.UTILITY);
    }

    @Override
    public boolean isToggleable() {
        return false;
    }

    @Override
    public void onAction() {
        StopwatchHud.press();
    }
}
