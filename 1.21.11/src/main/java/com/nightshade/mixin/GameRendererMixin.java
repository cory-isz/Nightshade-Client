package com.nightshade.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.nightshade.module.utility.Zoom;
import com.nightshade.module.visual.NoHurtCam;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    /** Target (1.21.11): private float getFov(Camera, float, boolean) - the world field of view (Zoom). */
    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
    private void nightshade$zoom(Camera camera, float tickDelta, boolean changingFov, CallbackInfoReturnable<Float> cir) {
        double multiplier = Zoom.fovMultiplier();
        if (multiplier != 1.0) {
            cir.setReturnValue((float) (cir.getReturnValueF() * multiplier));
        }
    }

    /** Target (1.21.11): the single options.damageTiltStrength().get() read inside private void bobHurt(PoseStack, float). */
    @ModifyExpressionValue(method = "bobHurt",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/OptionInstance;get()Ljava/lang/Object;"))
    private Object nightshade$scaleHurtTilt(Object original) {
        double multiplier = NoHurtCam.multiplier();
        if (multiplier == 1.0 || !(original instanceof Double strength)) {
            return original;
        }
        return strength * multiplier;
    }
}
