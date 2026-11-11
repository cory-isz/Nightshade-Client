package com.nightshade.mixin;

import com.mojang.blaze3d.platform.FramerateLimitTracker;
import com.nightshade.module.performance.DynamicFps;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FramerateLimitTracker.class)
public abstract class InactivityFpsLimiterMixin {
    /** Target: public int update() - the frame-rate cap used for this frame. */
    @Inject(method = "getFramerateLimit", at = @At("RETURN"), cancellable = true)
    private void nightshade$limit(CallbackInfoReturnable<Integer> cir) {
        int limit = com.nightshade.module.performance.MenuFpsCap.limit(DynamicFps.limit(cir.getReturnValueI()));
        if (limit != cir.getReturnValueI()) {
            cir.setReturnValue(limit);
        }
    }
}
