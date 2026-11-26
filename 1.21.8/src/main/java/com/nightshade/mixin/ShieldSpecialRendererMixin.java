package com.nightshade.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.nightshade.module.pvp.ShieldStatus;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.special.ShieldSpecialRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ShieldSpecialRenderer.class)
public abstract class ShieldSpecialRendererMixin {
    /**
     * Target (1.21.8): the handle and plate ModelPart.render(pose, buffer, light, overlay) calls in render(...); they are drawn
     * with the Shield Status tint instead. Banner-patterned shields draw their plate through BannerRenderer and keep vanilla colours.
     */
    @Redirect(method = "render(Lnet/minecraft/core/component/DataComponentMap;Lnet/minecraft/world/item/ItemDisplayContext;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IIZ)V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/model/geom/ModelPart;render(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;II)V"))
    private void nightshade$tint(ModelPart part, PoseStack pose, VertexConsumer buffer, int light, int overlay) {
        part.render(pose, buffer, light, overlay, ShieldStatus.shouldTint() ? ShieldStatus.tint(-1) : -1);
    }
}
