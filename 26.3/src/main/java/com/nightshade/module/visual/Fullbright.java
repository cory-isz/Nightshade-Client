package com.nightshade.module.visual;

import com.nightshade.util.EffectProbe;
import com.nightshade.module.Module;
import com.nightshade.module.ModuleCategory;
import com.nightshade.module.setting.NumberSetting;

/** Overrides the gamma value used when building the lightmap (see LightmapTextureManagerMixin). */
public final class Fullbright extends Module {
    private static volatile Fullbright active;
    private final NumberSetting gamma = add(new NumberSetting("Brightness", "Gamma value applied while enabled", 10, 1, 16, 0.5));

    public Fullbright() {
        super("Fullbright", "Makes dark areas fully visible", ModuleCategory.VISUAL);
    }

    @Override
    protected void onEnable() {
        active = this;
    }

    @Override
    protected void onDisable() {
        active = null;
    }

    /** Returns the overridden gamma, or NaN when inactive. */
    public static double gammaOverride() {
        Fullbright module = active;
        if (module == null) {
            return Double.NaN;
        }
        EffectProbe.hit("Fullbright");
        return module.gamma.get();
    }
}
