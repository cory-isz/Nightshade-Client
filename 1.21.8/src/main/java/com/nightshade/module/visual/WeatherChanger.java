package com.nightshade.module.visual;

import com.nightshade.util.EffectProbe;
import com.nightshade.module.Module;
import com.nightshade.module.ModuleCategory;
import com.nightshade.module.setting.ModeSetting;

/** Client-side weather override (see WorldMixin). Server weather is unaffected. */
public final class WeatherChanger extends Module {
    private static volatile WeatherChanger active;
    private final ModeSetting weather = add(new ModeSetting("Weather", "Displayed weather", "Clear", "Clear", "Rain", "Thunder"));

    public WeatherChanger() {
        super("Weather Changer", "Changes the displayed weather client-side", ModuleCategory.VISUAL);
    }

    @Override
    protected void onEnable() {
        active = this;
    }

    @Override
    protected void onDisable() {
        active = null;
    }

    /** Rain gradient override, or NaN when inactive. */
    public static float rainOverride() {
        WeatherChanger module = active;
        if (module == null) {
            return Float.NaN;
        }
        EffectProbe.hit("Weather Changer");
        return module.weather.is("Clear") ? 0f : 1f;
    }

    /** Thunder gradient override, or NaN when inactive. */
    public static float thunderOverride() {
        WeatherChanger module = active;
        return module == null ? Float.NaN : module.weather.is("Thunder") ? 1f : 0f;
    }
}
