package com.nightshade.mixin;

import com.nightshade.module.performance.WeatherOptimizer;
import net.minecraft.client.renderer.CloudRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Weather & Clouds: skip cloud rendering. */
@Mixin(CloudRenderer.class)
public abstract class WeatherCloudMixin {
    /** Target: public void render(int, CloudStatus, float, Vec3, long, float). */
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void nightshade$clouds(CallbackInfo ci) {
        if (WeatherOptimizer.hideClouds()) {
            ci.cancel();
        }
    }
}
