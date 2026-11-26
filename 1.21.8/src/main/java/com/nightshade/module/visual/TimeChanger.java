package com.nightshade.module.visual;

import com.nightshade.util.EffectProbe;
import com.nightshade.module.Module;
import com.nightshade.module.ModuleCategory;
import com.nightshade.module.setting.NumberSetting;

/** Client-side visual time of day override (see ClientWorldPropertiesMixin). Server time is unaffected. */
public final class TimeChanger extends Module {
    private static volatile TimeChanger active;
    private final NumberSetting time = add(new NumberSetting("Time", "Displayed time of day (0 = sunrise, 6000 = noon, 18000 = midnight)", 6000, 0, 23999, 250, " ticks"));

    public TimeChanger() {
        super("Time Changer", "Changes the displayed time of day client-side", ModuleCategory.VISUAL);
    }

    @Override
    protected void onEnable() {
        active = this;
    }

    @Override
    protected void onDisable() {
        active = null;
    }

    /** Returns the overridden time of day, or -1 when inactive. */
    public static long timeOverride() {
        TimeChanger module = active;
        if (module == null) {
            return -1L;
        }
        EffectProbe.hit("Time Changer");
        return (long) module.time.intValue();
    }
}
