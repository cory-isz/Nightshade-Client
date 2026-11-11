package com.nightshade.mixin;

import com.nightshade.module.pvp.HealthTags;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Health Tags: appends health to the name tag computed for the frame. */
@Mixin(EntityRenderer.class)
public abstract class EntityRendererNameTagMixin {
    /** Target: public void extractRenderState(Entity, EntityRenderState, float) - after nameTag is set. */
    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/client/renderer/entity/state/EntityRenderState;F)V", at = @At("TAIL"))
    private void nightshade$healthTag(Entity entity, EntityRenderState state, float partialTick, CallbackInfo ci) {
        if (state.nameTag != null && com.nightshade.module.performance.NameTagCulling.cull(entity)) {
            state.nameTag = null;
            return;
        }
        if (state.nameTag != null && entity == net.minecraft.client.Minecraft.getInstance().player) {
            state.nameTag = com.nightshade.module.utility.NickHider.nametag(state.nameTag);
        }
        if (state.nameTag != null && entity instanceof LivingEntity living) {
            state.nameTag = HealthTags.decorate(living, state.nameTag);
        }
    }
}
