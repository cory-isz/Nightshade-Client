package com.nightshade.module.utility;

import com.nightshade.util.EffectProbe;
import com.nightshade.module.BindMode;
import com.nightshade.module.Module;
import com.nightshade.module.ModuleCategory;
import com.nightshade.module.setting.BooleanSetting;
import com.nightshade.module.setting.NumberSetting;
import net.minecraft.client.CameraType;
import net.minecraft.util.Mth;

/**
 * Hold to orbit the camera around yourself in third person while your character keeps facing forward
 * (see MouseMixin / CameraMixin). Off by default with no key: many servers do not allow it.
 */
public final class Freelook extends Module {
    private static volatile Freelook active;
    private final NumberSetting sensitivity = add(new NumberSetting("Sensitivity", "Camera rotation speed", 1.0, 0.25, 3.0, 0.05, "x"));
    private final BooleanSetting invertPitch = add(new BooleanSetting("Invert Pitch", "Invert vertical camera movement", false));
    private float yaw;
    private float pitch;
    private CameraType previous;

    public Freelook() {
        super("Freelook", "Hold to look around without turning (check server rules)", ModuleCategory.UTILITY);
        defaultBindMode(BindMode.HOLD);
    }

    @Override
    protected void onEnable() {
        if (mc.player != null) {
            yaw = mc.player.getYRot();
            pitch = mc.player.getXRot();
        }
        previous = mc.options.getCameraType();
        mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        active = this;
    }

    @Override
    protected void onDisable() {
        active = null;
        if (previous != null) {
            mc.options.setCameraType(previous);
        }
    }

    /** Called instead of {@code player.changeLookDirection} while active. Same scale as vanilla (0.15 deg/unit). */
    public static boolean consumeMouse(double dx, double dy) {
        Freelook module = active;
        if (module == null) {
            return false;
        }
        EffectProbe.hit("Freelook");
        float s = module.sensitivity.floatValue();
        module.yaw += (float) dx * 0.15f * s;
        module.pitch = Mth.clamp(module.pitch + (float) dy * 0.15f * s * (module.invertPitch.get() ? -1f : 1f), -90f, 90f);
        return true;
    }

    public static boolean isActive() {
        return active != null;
    }

    public static float yaw() {
        return active == null ? 0f : active.yaw;
    }

    public static float pitch() {
        return active == null ? 0f : active.pitch;
    }
}
