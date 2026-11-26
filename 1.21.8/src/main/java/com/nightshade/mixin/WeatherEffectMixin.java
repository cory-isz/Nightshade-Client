package com.nightshade.mixin;

import com.nightshade.module.performance.WeatherOptimizer;
import net.minecraft.client.renderer.WeatherEffectRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Weather & Clouds: skip rain/snow rendering and rain splash particles. */
@Mixin(WeatherEffectRenderer.class)
public abstract class WeatherEffectMixin {
    /** Target (1.21.8): public void render(Level, MultiBufferSource, int, float, Vec3). */
    @Inject(method = "render(Lnet/minecraft/world/level/Level;Lnet/minecraft/client/renderer/MultiBufferSource;IFLnet/minecraft/world/phys/Vec3;)V",
            at = @At("HEAD"), cancellable = true)
    private void nightshade$weather(CallbackInfo ci) {
        if (WeatherOptimizer.hideWeather()) {
            ci.cancel();
        }
    }

    /** Target: public void tickRainParticles(ClientLevel, Camera, int, ParticleStatus). */
    @Inject(method = "tickRainParticles", at = @At("HEAD"), cancellable = true)
    private void nightshade$splashes(CallbackInfo ci) {
        if (WeatherOptimizer.hideSplashes()) {
            ci.cancel();
        }
    }
}
