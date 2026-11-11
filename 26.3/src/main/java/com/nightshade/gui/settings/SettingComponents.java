package com.nightshade.gui.settings;

import com.nightshade.module.ModuleSetting;
import com.nightshade.module.setting.BooleanSetting;
import com.nightshade.module.setting.ColorSetting;
import com.nightshade.module.setting.ModeSetting;
import com.nightshade.module.setting.NumberSetting;

/** Maps setting types to their GUI component. */
public final class SettingComponents {
    private SettingComponents() {
    }

    public static SettingComponent<?> create(ModuleSetting<?> setting) {
        if (setting instanceof BooleanSetting s) {
            return new BooleanComponent(s);
        }
        if (setting instanceof NumberSetting s) {
            return new NumberComponent(s);
        }
        if (setting instanceof ModeSetting s) {
            return new ModeComponent(s);
        }
        if (setting instanceof ColorSetting s) {
            return new ColorComponent(s);
        }
        if (setting instanceof com.nightshade.module.setting.TextSetting s) {
            return new TextComponent(s);
        }
        if (setting instanceof com.nightshade.module.setting.ActionSetting s) {
            return new ActionComponent(s);
        }
        throw new IllegalArgumentException("No GUI component for setting type " + setting.getClass().getName());
    }
}
