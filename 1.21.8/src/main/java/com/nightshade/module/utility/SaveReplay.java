package com.nightshade.module.utility;

import com.nightshade.module.Module;
import com.nightshade.module.ModuleCategory;

/** Keybind action: saves the last seconds recorded by Fight Replay as a clip. */
public final class SaveReplay extends Module {
    public SaveReplay() {
        super("Save Replay", "Press to save the last seconds of Fight Replay as a clip", ModuleCategory.UTILITY);
    }

    @Override
    public boolean isToggleable() {
        return false;
    }

    @Override
    public void onAction() {
        FightReplay.saveClip();
        com.nightshade.util.EffectProbe.hit("Save Replay");
    }
}
