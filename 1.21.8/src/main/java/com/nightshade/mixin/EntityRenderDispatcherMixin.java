package com.nightshade.mixin;

import com.nightshade.module.performance.EntityRenderLimiter;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderDispatcher.class)
public abstract class EntityRenderDispatcherMixin /* targets EntityRenderManager (renamed in 1.21.9) */ {
    /** Target: public boolean shouldRender(Entity, Frustum, double cameraX, double cameraY, double cameraZ) */
    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
    private void nightshade$limitEntities(Entity entity, Frustum frustum, double x, double y, double z, CallbackInfoReturnable<Boolean> cir) {
        if (EntityRenderLimiter.shouldCull(entity, x, y, z) || com.nightshade.module.performance.EntityCulling.shouldCull(entity, x, y, z)) {
            cir.setReturnValue(false);
        }
    }
}
