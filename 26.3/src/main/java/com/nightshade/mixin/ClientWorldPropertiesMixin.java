package com.nightshade.mixin;

import com.nightshade.module.visual.TimeChanger;
import net.minecraft.client.ClientClockManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 26.3 keeps each world clock in a ClientClockInstance; everything client-side reads time through it. */
@Mixin(ClientClockManager.ClientClockInstance.class)
public abstract class ClientWorldPropertiesMixin {
    /** Target: public long totalTicks() */
    @Inject(method = "totalTicks", at = @At("HEAD"), cancellable = true)
    private void nightshade$timeOfDay(CallbackInfoReturnable<Long> cir) {
        long override = TimeChanger.timeOverride();
        if (override >= 0L) {
            cir.setReturnValue(override);
        }
    }

    /** Target: public float partialTick() - frozen while the time is overridden, so the sky does not jitter. */
    @Inject(method = "partialTick", at = @At("HEAD"), cancellable = true)
    private void nightshade$partial(CallbackInfoReturnable<Float> cir) {
        if (TimeChanger.timeOverride() >= 0L) {
            cir.setReturnValue(0f);
        }
    }
}
