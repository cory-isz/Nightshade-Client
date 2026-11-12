package com.nightshade.mixin;

import com.nightshade.module.performance.ParticleOptimizer;
import net.minecraft.client.particle.Particle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Particle.class)
public abstract class ParticleMixin {
    @Shadow
    protected double x;
    @Shadow
    protected double y;
    @Shadow
    protected double z;
    @Shadow
    protected boolean hasPhysics;

    /** Target: public void move(double, double, double) - far particles skip the block-collision search. */
    @Inject(method = "move", at = @At("HEAD"))
    private void nightshade$farPhysics(double dx, double dy, double dz, CallbackInfo ci) {
        if (hasPhysics && ParticleOptimizer.skipPhysics(x, y, z)) {
            hasPhysics = false;
        }
    }
}
