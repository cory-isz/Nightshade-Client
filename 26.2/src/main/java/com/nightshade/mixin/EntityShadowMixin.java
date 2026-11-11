package com.nightshade.mixin;

import com.nightshade.module.performance.ShadowCulling;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Shadow Culling: skip computing an entity's shadow (the per-block sampling) when it is far away. */
@Mixin(EntityRenderer.class)
public abstract class EntityShadowMixin {
    /** Target: private void extractShadow(EntityRenderState, Minecraft, Level). */
    @Inject(method = "extractShadow(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;Lnet/minecraft/client/Minecraft;Lnet/minecraft/world/level/Level;)V",
            at = @At("HEAD"), cancellable = true)
    private void nightshade$cullShadow(EntityRenderState state, Minecraft minecraft, Level level, CallbackInfo ci) {
        if (ShadowCulling.cull(state.distanceToCameraSq)) {
            state.shadowRadius = 0f;
            state.shadowPieces.clear();
            ci.cancel();
        }
    }
}
