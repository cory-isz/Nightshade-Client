package com.nightshade.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.nightshade.module.performance.ShadowCulling;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Shadow Culling: no shadow (and none of its per-block sampling) for entities beyond the chosen distance. */
@Mixin(EntityRenderDispatcher.class)
public abstract class EntityShadowMixin {
    /** Target (1.21.8): the getShadowRadius(state) read in render(...); a radius of 0 skips the shadow entirely. */
    @WrapOperation(method = "render(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;DDDLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/client/renderer/entity/EntityRenderer;)V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/entity/EntityRenderer;getShadowRadius(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;)F"))
    private float nightshade$cullShadow(EntityRenderer<?, ?> renderer, EntityRenderState state, Operation<Float> original) {
        float radius = original.call(renderer, state);
        return radius > 0f && ShadowCulling.cull(state.distanceToCameraSq) ? 0f : radius;
    }
}
