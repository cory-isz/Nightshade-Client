package com.nightshade.mixin;

import com.nightshade.module.visual.TimeChanger;
import net.minecraft.client.ClientClockManager;
import net.minecraft.core.Holder;
import net.minecraft.world.clock.WorldClock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 26.x keeps time per world clock; the client reads every clock through ClientClockManager. */
@Mixin(ClientClockManager.class)
public abstract class ClientWorldPropertiesMixin {
    /** Target: public long getTotalTicks(Holder<WorldClock>) */
    @Inject(method = "getTotalTicks", at = @At("HEAD"), cancellable = true)
    private void nightshade$timeOfDay(Holder<WorldClock> clock, CallbackInfoReturnable<Long> cir) {
        long override = TimeChanger.timeOverride();
        if (override >= 0L) {
            cir.setReturnValue(override);
        }
    }
}
