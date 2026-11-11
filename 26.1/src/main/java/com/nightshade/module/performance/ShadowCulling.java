package com.nightshade.module.performance;

import com.nightshade.module.Module;
import com.nightshade.module.ModuleCategory;
import com.nightshade.module.setting.BooleanSetting;
import com.nightshade.module.setting.NumberSetting;
import com.nightshade.util.EffectProbe;

/**
 * Skips the round shadows under distant entities. For every entity with a shadow Minecraft samples the blocks below
 * it each frame to shape the shadow; with many mobs, players or dropped items around that adds up, and from a few
 * blocks away you cannot see the shadow anyway. Hooked before the sampling, so the work is saved, not just hidden.
 */
public final class ShadowCulling extends Module {
    private static volatile ShadowCulling active;
    private final NumberSetting distance = add(new NumberSetting("Distance", "Entities further away than this get no shadow", 16, 4, 64, 2, " blocks"));
    private final BooleanSetting all = add(new BooleanSetting("Hide All Shadows", "No entity shadows at all (most FPS)", false));

    public ShadowCulling() {
        super("Shadow Culling", "Skips entity shadows beyond a distance for more FPS", ModuleCategory.PERFORMANCE);
        enabledByDefault();
    }

    @Override
    protected void onEnable() {
        active = this;
    }

    @Override
    protected void onDisable() {
        active = null;
    }

    /** Hook from EntityShadowMixin: whether to skip the shadow of an entity this far from the camera (squared). */
    public static boolean cull(double distanceToCameraSq) {
        ShadowCulling m = active;
        if (m == null) {
            return false;
        }
        double d = m.distance.get();
        if (m.all.get() || distanceToCameraSq > d * d) {
            EffectProbe.hit("Shadow Culling");
            return true;
        }
        return false;
    }
}
