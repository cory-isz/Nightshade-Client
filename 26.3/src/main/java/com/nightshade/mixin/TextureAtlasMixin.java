package com.nightshade.mixin;

import com.nightshade.module.performance.AnimationOptimizer;
import net.minecraft.client.renderer.texture.TextureAtlas;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Animation Optimizer: throttles animated texture uploads. */
@Mixin(TextureAtlas.class)
public abstract class TextureAtlasMixin {
    /** Target: public void cycleAnimationFrames() - steps and uploads every animated sprite. */
    @Inject(method = "cycleAnimationFrames", at = @At("HEAD"), cancellable = true)
    private void nightshade$throttle(CallbackInfo ci) {
        if (AnimationOptimizer.skip()) {
            ci.cancel();
        }
    }
}
