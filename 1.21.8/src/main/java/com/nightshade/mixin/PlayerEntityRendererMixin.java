package com.nightshade.mixin;

import com.nightshade.module.visual.OwnNametag;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntityRenderer.class)
public abstract class PlayerEntityRendererMixin {
    /** Target (1.21.8): protected boolean shouldShowName(LivingEntity, double) - players use the living-entity rule. */
    @Inject(method = "shouldShowName(Lnet/minecraft/world/entity/LivingEntity;D)Z", at = @At("RETURN"), cancellable = true)
    private void nightshade$ownNametag(LivingEntity entity, double squaredDistance, CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ() && OwnNametag.shouldShow(entity)) {
            cir.setReturnValue(true);
        }
    }
}
