package com.nightshade.module.visual;

import com.nightshade.util.EffectProbe;
import com.nightshade.module.Module;
import com.nightshade.module.ModuleCategory;
import com.nightshade.module.setting.NumberSetting;

/** Scales the camera tilt applied when taking damage (see GameRendererMixin). */
public final class NoHurtCam extends Module {
    private static volatile NoHurtCam active;
    private final NumberSetting intensity = add(new NumberSetting("Intensity", "Remaining hurt-tilt strength", 0, 0, 100, 5, "%"));

    public NoHurtCam() {
        super("No Hurt Cam", "Removes or reduces the damage camera shake", ModuleCategory.VISUAL);
    }

    @Override
    protected void onEnable() {
        active = this;
    }

    @Override
    protected void onDisable() {
        active = null;
    }

    /** Multiplier applied to the vanilla damage tilt strength (1.0 when inactive). */
    public static double multiplier() {
        NoHurtCam module = active;
        if (module == null) {
            return 1.0;
        }
        EffectProbe.hit("No Hurt Cam");
        return module.intensity.get() / 100.0;
    }
}
