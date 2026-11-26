package com.nightshade.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.nightshade.module.visual.LowFire;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ScreenEffectRenderer.class)
public abstract class InGameOverlayRendererMixin {
    @Unique
    private static boolean nightshade$pushed;

    /** Target (1.21.8): private static void renderFire(PoseStack, MultiBufferSource). Balanced push/pop. */
    @Inject(method = "renderFire", at = @At("HEAD"))
    private static void nightshade$lowerFire(PoseStack matrices, MultiBufferSource consumers, CallbackInfo ci) {
        float offset = LowFire.offset();
        nightshade$pushed = offset > 0f;
        if (nightshade$pushed) {
            matrices.pushPose();
            matrices.translate(0f, -offset, 0f);
        }
    }

    @Inject(method = "renderFire", at = @At("RETURN"))
    private static void nightshade$restoreFire(PoseStack matrices, MultiBufferSource consumers, CallbackInfo ci) {
        if (nightshade$pushed) {
            matrices.popPose();
            nightshade$pushed = false;
        }
    }
}
