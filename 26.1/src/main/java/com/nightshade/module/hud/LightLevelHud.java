package com.nightshade.module.hud;

import com.nightshade.module.setting.BooleanSetting;
import com.nightshade.render.Theme;
import net.minecraft.world.level.LightLayer;

/** Block light at your feet (red at 0, where hostile mobs can spawn), optionally with sky light. */
public final class LightLevelHud extends SimpleTextHud {
    private final BooleanSetting sky = add(new BooleanSetting("Show Sky Light", "Also show the sky light level", false));

    public LightLevelHud() {
        super("Light Level", "Shows the light level at your feet (red where mobs can spawn)", "Light ", 0.0, 0.46);
    }

    @Override
    protected void refresh() {
        if (mc.player == null || mc.level == null) {
            return;
        }
        var pos = mc.player.blockPosition();
        int block = mc.level.getBrightness(LightLayer.BLOCK, pos);
        value = Integer.toString(block) + (sky.get() ? "  (sky " + mc.level.getBrightness(LightLayer.SKY, pos) + ")" : "");
        valueColor = block == 0 ? Theme.DANGER : block < 8 ? Theme.WARNING : 0;
    }
}
