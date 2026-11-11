package com.nightshade.module.utility;

import com.nightshade.module.BindMode;
import com.nightshade.module.Module;
import com.nightshade.module.ModuleCategory;
import com.nightshade.module.setting.ModeSetting;
import com.nightshade.util.EffectProbe;
import net.minecraft.client.CameraType;

/** Snaplook: hold the key to look at yourself from the front (or back); let go to return to your view. */
public final class Snaplook extends Module {
    private final ModeSetting view = add(new ModeSetting("View", "Camera while held", "Front", "Front", "Back"));
    private CameraType previous;

    public Snaplook() {
        super("Snaplook", "Hold to see yourself from the front or back, release to return", ModuleCategory.UTILITY);
        defaultBindMode(BindMode.HOLD);
    }

    @Override
    protected void onEnable() {
        if (mc.options == null) {
            return; // restored from the config while the game is still starting
        }
        previous = mc.options.getCameraType();
        mc.options.setCameraType(view.is("Front") ? CameraType.THIRD_PERSON_FRONT : CameraType.THIRD_PERSON_BACK);
        EffectProbe.hit("Snaplook");
    }

    @Override
    protected void onDisable() {
        if (previous != null && mc.options != null) {
            mc.options.setCameraType(previous);
        }
        previous = null;
    }
}
