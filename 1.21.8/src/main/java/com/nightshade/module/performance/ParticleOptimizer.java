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
    private final NumberSetting maxDistance = add(new NumberSetting("Max Distance", "Particles further away than this are not spawned", 24, 8, 32, 2, " blocks"));
    private final BooleanSetting farPhysics = add(new BooleanSetting("Skip Far Collisions", "Particles further than 12 blocks stop checking for blocks to bounce off (you can't see the difference)", true));
    private final BooleanSetting behind = add(new BooleanSetting("Skip Behind You", "Don't spawn particles behind you (more than 4 blocks back, in first person)", true));
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
        if (module.outOfSight(particle)) {
            skip = true;
        } else if (module.mode.is("Disable")) {
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

    /** Too far away, or behind the player where it will never be seen. */
    private boolean outOfSight(Particle particle) {
        if (mc.gameRenderer == null || mc.player == null) {
            return false;
        }
        net.minecraft.world.phys.Vec3 at = particle.getBoundingBox().getCenter();
        net.minecraft.world.phys.Vec3 eye = mc.player.getEyePosition();
        double dx = at.x - eye.x, dy = at.y - eye.y, dz = at.z - eye.z;
        double distSq = dx * dx + dy * dy + dz * dz;
        double max = maxDistance.get();
        if (distSq > max * max) {
            return true;
        }
        if (behind.get() && distSq > 16 && mc.options.getCameraType().isFirstPerson()) {
            net.minecraft.world.phys.Vec3 look = mc.player.getViewVector(1f);
            return dx * look.x + dy * look.y + dz * look.z < -4.0; // more than 4 blocks behind the eye
        }
        return false;
    }

    /** Total particles skipped since launch (displayed by Frame Stats for measurement). */
    public static long skippedTotal() {
        return SKIPPED.get();
    }

    private static final AtomicLong PHYSICS_SKIPPED = new AtomicLong();

    public static long physicsSkippedTotal() {
        return PHYSICS_SKIPPED.get();
    }

    /** Hook from ParticleMixin: true when a particle at this position may move without block collisions. */
    public static boolean skipPhysics(double x, double y, double z) {
        ParticleOptimizer module = active;
        if (module == null || !module.farPhysics.get()) {
            return false;
        }
        var camera = mc.gameRenderer.getMainCamera().position();
        double dx = x - camera.x, dy = y - camera.y, dz = z - camera.z;
        if (dx * dx + dy * dy + dz * dz > 12 * 12) {
            PHYSICS_SKIPPED.incrementAndGet();
            return true;
        }
        return false;
    }
}
