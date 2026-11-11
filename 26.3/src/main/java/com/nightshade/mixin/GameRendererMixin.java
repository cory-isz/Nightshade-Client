package com.nightshade.mixin;

import com.nightshade.module.utility.Zoom;
import com.nightshade.module.visual.NoHurtCam;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    /** Target (26.x): the OptionsRenderState.damageTiltStrength read inside private void bobHurt(...). */
    @ModifyExpressionValue(method = "bobHurt",
            at = @At(value = "FIELD", target = "Lnet/minecraft/client/renderer/state/OptionsRenderState;damageTiltStrength:D"))
    private double nightshade$scaleHurtTilt(double original) {
        return original * NoHurtCam.multiplier();
    }
}
