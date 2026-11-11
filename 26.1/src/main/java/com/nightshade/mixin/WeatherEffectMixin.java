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
    /** Target: public void render(MultiBufferSource, Vec3, WeatherRenderState). */
    @Inject(method = "render(Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/client/renderer/state/level/WeatherRenderState;)V",
            at = @At("HEAD"), cancellable = true)
    private void nightshade$weather(CallbackInfo ci) {
        if (WeatherOptimizer.hideWeather()) {
            ci.cancel();
        }
    }

    /** Target: public void tickRainParticles(ClientLevel, Camera, int, ParticleStatus, int). */
    @Inject(method = "tickRainParticles", at = @At("HEAD"), cancellable = true)
    private void nightshade$splashes(CallbackInfo ci) {
        if (WeatherOptimizer.hideSplashes()) {
            ci.cancel();
        }
    }
}
