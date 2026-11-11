package com.nightshade.module.hud;

import com.nightshade.render.Theme;

import java.util.Locale;

/** Hidden hunger saturation: while it is above 0 your food bar does not drop and you heal fastest. */
public final class SaturationHud extends SimpleTextHud {
    public SaturationHud() {
        super("Saturation", "Shows your hidden food saturation (heal fastest while it is above 0)", "Saturation ", 0.0, 0.58);
    }

    @Override
    protected void refresh() {
        if (mc.player == null) {
            return;
        }
        float saturation = mc.player.getFoodData().getSaturationLevel();
        value = String.format(Locale.ROOT, "%.1f", saturation);
        valueColor = saturation <= 0f ? Theme.WARNING : 0;
    }
}
