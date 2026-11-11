package com.nightshade.module.visual;

import com.nightshade.util.EffectProbe;
import com.nightshade.module.Module;
import com.nightshade.module.ModuleCategory;
import com.nightshade.module.setting.NumberSetting;

/** Lowers the first-person fire overlay (see InGameOverlayRendererMixin). */
public final class LowFire extends Module {
    private static volatile LowFire active;
    private final NumberSetting offset = add(new NumberSetting("Offset", "How far the fire overlay is lowered", 0.3, 0.05, 0.6, 0.05));

    public LowFire() {
        super("Low Fire", "Lowers the fire overlay so it blocks less of the screen", ModuleCategory.VISUAL);
    }

    @Override
    protected void onEnable() {
        active = this;
    }

    @Override
    protected void onDisable() {
        active = null;
    }

    /** Vertical offset to apply, or 0 when inactive. */
    public static float offset() {
        LowFire module = active;
        if (module == null) {
            return 0f;
        }
        EffectProbe.hit("Low Fire");
        return module.offset.floatValue();
    }
}
