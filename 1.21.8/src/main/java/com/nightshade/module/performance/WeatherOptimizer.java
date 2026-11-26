package com.nightshade.module.performance;

import com.nightshade.module.Module;
import com.nightshade.module.ModuleCategory;
import com.nightshade.module.setting.BooleanSetting;
import com.nightshade.util.EffectProbe;

/** Skips drawing clouds, rain and snow, and rain splash particles - client-side only (see WeatherCloudMixin). */
public final class WeatherOptimizer extends Module {
    private static volatile WeatherOptimizer active;
    private final BooleanSetting clouds = add(new BooleanSetting("Hide Clouds", "Do not draw clouds", true));
    private final BooleanSetting weather = add(new BooleanSetting("Hide Rain & Snow", "Do not draw falling rain and snow", true));
    private final BooleanSetting splashes = add(new BooleanSetting("Hide Rain Splashes", "No rain splash particles and dripping sounds", true));

    public WeatherOptimizer() {
        super("Weather & Clouds", "Skips clouds, rain, snow and rain splashes for more FPS", ModuleCategory.PERFORMANCE);
    }

    @Override
    protected void onEnable() {
        active = this;
    }

    @Override
    protected void onDisable() {
        active = null;
    }

    public static boolean hideClouds() {
        WeatherOptimizer m = active;
        if (m != null && m.clouds.get()) {
            EffectProbe.hit("Weather & Clouds");
            return true;
        }
        return false;
    }

    public static boolean hideWeather() {
        WeatherOptimizer m = active;
        if (m != null && m.weather.get()) {
            EffectProbe.hit("Weather & Clouds");
            return true;
        }
        return false;
    }

    public static boolean hideSplashes() {
        WeatherOptimizer m = active;
        return m != null && m.splashes.get();
    }
}
