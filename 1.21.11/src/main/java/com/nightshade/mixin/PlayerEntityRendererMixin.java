package com.nightshade.mixin;

import com.nightshade.module.visual.OwnNametag;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.world.entity.Avatar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AvatarRenderer.class)
public abstract class PlayerEntityRendererMixin {
    /** Target (1.21.11): protected boolean hasLabel(PlayerLikeEntity, double) - the typed, non-bridge overload. */
    @Inject(method = "shouldShowName(Lnet/minecraft/world/entity/Avatar;D)Z", at = @At("RETURN"), cancellable = true)
    private void nightshade$ownNametag(Avatar entity, double squaredDistance, CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ() && OwnNametag.shouldShow(entity)) {
            cir.setReturnValue(true);
        }
    }
}
