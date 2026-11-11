package com.nightshade.mixin;

import com.nightshade.module.performance.ParticleOptimizer;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ParticleEngine.class)
public abstract class ParticleManagerMixin {
    /** Target: public void addParticle(Particle) - every particle path ends here. */
    @Inject(method = "add(Lnet/minecraft/client/particle/Particle;)V", at = @At("HEAD"), cancellable = true)
    private void nightshade$filterParticle(Particle particle, CallbackInfo ci) {
        if (ParticleOptimizer.shouldSkip(particle)) {
            ci.cancel();
        }
    }
}
