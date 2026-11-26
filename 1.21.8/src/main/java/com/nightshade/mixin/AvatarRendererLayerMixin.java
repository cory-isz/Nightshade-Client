package com.nightshade.mixin;

import com.nightshade.cosmetic.CosmeticLayer;
import com.nightshade.mixin.accessor.LivingEntityRendererAccess;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Adds the Nightshade 3D cosmetics layer to every player renderer. */
@Mixin(PlayerRenderer.class)
public abstract class AvatarRendererLayerMixin {
    @SuppressWarnings({"unchecked", "rawtypes"})
    @Inject(method = "<init>", at = @At("TAIL"))
    private void nightshade$cosmeticLayer(EntityRendererProvider.Context context, boolean slim, CallbackInfo ci) {
        ((LivingEntityRendererAccess) (Object) this).nightshade$addLayer(new CosmeticLayer((PlayerRenderer) (Object) this));
    }
}
