package com.nightshade.mixin;

import com.nightshade.module.visual.WeatherChanger;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Level.class)
public abstract class WorldMixin {
    /** Target: public float getRainGradient(float). Only the client world is affected. */
    @Inject(method = "getRainLevel", at = @At("HEAD"), cancellable = true)
    private void nightshade$rain(float delta, CallbackInfoReturnable<Float> cir) {
        if ((Object) this instanceof ClientLevel) {
            float override = WeatherChanger.rainOverride();
            if (!Float.isNaN(override)) {
                cir.setReturnValue(override);
            }
        }
    }

    /** Target: public float getThunderGradient(float). Only the client world is affected. */
    @Inject(method = "getThunderLevel", at = @At("HEAD"), cancellable = true)
    private void nightshade$thunder(float delta, CallbackInfoReturnable<Float> cir) {
        if ((Object) this instanceof ClientLevel) {
            float override = WeatherChanger.thunderOverride();
            if (!Float.isNaN(override)) {
                cir.setReturnValue(override);
            }
        }
    }
}
