package com.nightshade.mixin;

import com.nightshade.module.performance.WeatherOptimizer;
import net.minecraft.client.multiplayer.ClientLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Weather & Clouds: skip rain splash particles (and their sounds), which live in ClientLevel since 26.2. */
@Mixin(ClientLevel.class)
public abstract class ClientLevelWeatherMixin {
    /** Target: public void tickWeatherEffects(). */
    @Inject(method = "tickWeatherEffects", at = @At("HEAD"), cancellable = true)
    private void nightshade$splashes(CallbackInfo ci) {
        if (WeatherOptimizer.hideSplashes()) {
            ci.cancel();
        }
    }
}
