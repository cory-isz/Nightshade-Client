package com.nightshade.mixin;

import com.nightshade.module.pvp.ShieldStatus;
import net.minecraft.client.renderer.special.ShieldSpecialRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(ShieldSpecialRenderer.class)
public abstract class ShieldSpecialRendererMixin {
    /**
     * Target (1.21.11): both submitModelPart(...) calls in submit(...) - the handle and the plate. Argument 8 is the
     * tint colour (vanilla: -1). Banner-patterned shields draw their plate through BannerRenderer and keep vanilla colours.
     */
    @ModifyArg(method = "submit(Lnet/minecraft/core/component/DataComponentMap;Lnet/minecraft/world/item/ItemDisplayContext;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;IIZI)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/SubmitNodeCollector;submitModelPart(Lnet/minecraft/client/model/geom/ModelPart;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/rendertype/RenderType;IILnet/minecraft/client/renderer/texture/TextureAtlasSprite;ZZILnet/minecraft/client/renderer/feature/ModelFeatureRenderer$CrumblingOverlay;I)V"), index = 8)
    private int nightshade$tint(int color) {
        return ShieldStatus.shouldTint() ? ShieldStatus.tint(color) : color;
    }
}
