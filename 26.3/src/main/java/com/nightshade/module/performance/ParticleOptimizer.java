package com.nightshade.module.performance;

import com.nightshade.util.EffectProbe;
import com.nightshade.module.Module;
import com.nightshade.module.ModuleCategory;
import com.nightshade.module.setting.BooleanSetting;
import com.nightshade.module.setting.ModeSetting;
import com.nightshade.module.setting.NumberSetting;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.TerrainParticle;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Disables or thins out client particles at the point they are added to the ParticleManager
 * (see ParticleManagerMixin). Particles are purely visual; nothing gameplay-related is affected.
 */
public final class ParticleOptimizer extends Module {
    private static volatile ParticleOptimizer active;
    private static final AtomicLong SKIPPED = new AtomicLong();

    private final ModeSetting mode = add(new ModeSetting("Mode", "Disable all particles or keep a fraction", "Reduce", "Reduce", "Disable"));
    private final NumberSetting keepPercent = add(new NumberSetting("Keep", "Percentage of particles kept in Reduce mode", 40, 5, 95, 5, "%"))
            .visibleWhen(() -> mode.is("Reduce"));
    private final BooleanSetting keepBlockBreak = add(new BooleanSetting("Keep Block Breaking", "Always keep block-breaking particles for feedback", true));
    private int counter;

    public ParticleOptimizer() {
        super("Particle Optimizer", "Disables or reduces particle rendering", ModuleCategory.PERFORMANCE);
    }

    @Override
    protected void onEnable() {
        active = this;
    }

    @Override
    protected void onDisable() {
        active = null;
    }

    /** Hook from ParticleManagerMixin. Returns true when the particle should not be added. */
    public static boolean shouldSkip(Particle particle) {
        ParticleOptimizer module = active;
        if (module == null) {
            return false;
        }
        if (module.keepBlockBreak.get() && particle instanceof TerrainParticle) {
            return false;
        }
        boolean skip;
        if (module.mode.is("Disable")) {
            skip = true;
        } else {
            // Deterministic thinning: keep N of every 20 particles, spreading kept ones evenly.
            int keepOf20 = Math.max(1, (int) Math.round(module.keepPercent.get() / 5.0));
            module.counter = (module.counter + 1) % 20;
            skip = (module.counter * keepOf20) % 20 >= keepOf20;
        }
        if (skip) {
            EffectProbe.hit("Particle Optimizer");
            SKIPPED.incrementAndGet();
        }
        return skip;
    }

    /** Total particles skipped since launch (displayed by Frame Stats for measurement). */
    public static long skippedTotal() {
        return SKIPPED.get();
    }
}
