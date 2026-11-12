package com.nightshade.mixin;

import com.nightshade.module.performance.EntityCulling;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientLevel.class)
public abstract class ClientLevelTickMixin {
    /** Target: public void tickNonPassenger(Entity) - Entity Culling's Tick Culling skips updates of entities you cannot see. */
    @Inject(method = "tickNonPassenger", at = @At("HEAD"), cancellable = true)
    private void nightshade$tickCulling(Entity entity, CallbackInfo ci) {
        if (EntityCulling.skipTick(entity)) {
            entity.setOldPosAndRot(); // no interpolation jitter when it becomes visible again
            entity.tickCount++;
            ci.cancel();
        }
    }
}
