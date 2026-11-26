package com.nightshade.module.visual;

import com.nightshade.util.EffectProbe;
import com.nightshade.module.Module;
import com.nightshade.module.ModuleCategory;
import com.nightshade.module.setting.NumberSetting;

/**
 * Pushes back the fog at the edge of your render distance so distant terrain and players stay clear. Fog from
 * water, lava, blindness, darkness and powder snow is never changed.
 */
public final class NoFog extends Module {
    private static NoFog instance;

    private final NumberSetting strength = add(new NumberSetting("Reduction", "100% removes the distance fog completely", 100, 10, 100, 5, "%"));

    public NoFog() {
        super("No Fog", "Removes the fog at the edge of render distance", ModuleCategory.VISUAL);
        instance = this;
    }

    public static boolean active() {
        return instance != null && instance.isEnabled();
    }

    /** Returns the adjusted {start, end} of the render-distance fog. */
    public static float[] adjust(float start, float end) {
        EffectProbe.hit("No Fog");
        float f = instance.strength.get().floatValue() / 100f;
        if (f >= 0.999f) {
            return new float[]{Float.MAX_VALUE / 4f, Float.MAX_VALUE / 2f};
        }
        float scale = 1f + f * 4f;
        return new float[]{start * scale, end * scale};
    }
}
