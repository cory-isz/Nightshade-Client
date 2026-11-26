package com.nightshade.mixin;

import com.nightshade.module.performance.ParticleOptimizer;
import com.nightshade.module.visual.Particles;
import com.nightshade.render.ParticleScaled;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.particle.TerrainParticle;
import net.minecraft.core.particles.ParticleOptions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ParticleEngine.class)
public abstract class ParticleManagerMixin {
    /** Target: public Particle createParticle(ParticleOptions, ...) - Particles: hidden types are never made. */
    @Inject(method = "createParticle", at = @At("HEAD"), cancellable = true)
    private void nightshade$hideType(ParticleOptions options, double x, double y, double z, double vx, double vy, double vz,
                                     CallbackInfoReturnable<Particle> cir) {
        if (Particles.hides(options.getType())) {
            cir.setReturnValue(null);
        }
    }

    /** Same method, on return: the new particle takes its group's size. */
    @Inject(method = "createParticle", at = @At("RETURN"))
    private void nightshade$sizeType(ParticleOptions options, double x, double y, double z, double vx, double vy, double vz,
                                     CallbackInfoReturnable<Particle> cir) {
        Particle p = cir.getReturnValue();
        if (p != null && Particles.instance() != null) {
            ((ParticleScaled) p).nightshade$setScale(Particles.scale(Particles.groupOf(options.getType())));
        }
    }

    /** Target: public void addParticle(Particle) - every particle path ends here. */
    @Inject(method = "add(Lnet/minecraft/client/particle/Particle;)V", at = @At("HEAD"), cancellable = true)
    private void nightshade$filterParticle(Particle particle, CallbackInfo ci) {
        if (particle instanceof TerrainParticle && Particles.instance() != null) {
            // block-breaking cracks and debris are added directly, without createParticle
            if (Particles.hidesGroup(Particles.Group.BLOCKS)) {
                ci.cancel();
                return;
            }
            ((ParticleScaled) particle).nightshade$setScale(Particles.scale(Particles.Group.BLOCKS));
        }
        if (ParticleOptimizer.shouldSkip(particle)) {
            ci.cancel();
        }
    }
}
