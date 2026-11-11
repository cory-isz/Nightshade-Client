package com.nightshade.mixin;

import com.nightshade.module.visual.TimeChanger;
import net.minecraft.client.multiplayer.ClientLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientLevel.ClientLevelData.class)
public abstract class ClientWorldPropertiesMixin {
    /** Target: public long getTimeOfDay() */
    @Inject(method = "getDayTime", at = @At("HEAD"), cancellable = true)
    private void nightshade$timeOfDay(CallbackInfoReturnable<Long> cir) {
        long override = TimeChanger.timeOverride();
        if (override >= 0L) {
            cir.setReturnValue(override);
        }
    }
}
