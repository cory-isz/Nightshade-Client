package com.nightshade.module.performance;

import com.nightshade.module.Module;
import com.nightshade.module.ModuleCategory;
import com.nightshade.module.setting.ModeSetting;
import com.nightshade.module.setting.NumberSetting;
import com.nightshade.util.EffectProbe;

/**
 * Animated textures (water, lava, fire, portals, sea lanterns...) are re-uploaded to the GPU every tick. Reduced
 * updates them less often, Off freezes them - a real FPS gain on weak GPUs and laptops (see TextureAtlasMixin).
 */
public final class AnimationOptimizer extends Module {
    private static volatile AnimationOptimizer active;
    private final ModeSetting mode = add(new ModeSetting("Mode", "Reduced: update animations less often. Off: freeze them", "Reduced", "Reduced", "Off"));
    private final NumberSetting every = add(new NumberSetting("Update Every", "Ticks between animation updates in Reduced mode", 3, 2, 10, 1, " ticks"))
            .visibleWhen(() -> mode.is("Reduced"));
    private int counter;

    public AnimationOptimizer() {
        super("Animation Optimizer", "Updates animated textures (water, lava, fire) less often for more FPS", ModuleCategory.PERFORMANCE);
    }

    @Override
    protected void onEnable() {
        active = this;
    }

    @Override
    protected void onDisable() {
        active = null;
    }

    /** Whether this atlas animation step should be skipped. Called once per atlas per tick. */
    public static boolean skip() {
        AnimationOptimizer m = active;
        if (m == null) {
            return false;
        }
        boolean skip = m.mode.is("Off") || (m.counter++ % Math.max(2, m.every.intValue())) != 0;
        if (skip) {
            EffectProbe.hit("Animation Optimizer");
        }
        return skip;
    }
}
