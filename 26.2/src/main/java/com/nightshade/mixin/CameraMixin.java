package com.nightshade.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.nightshade.module.utility.Freelook;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Camera.class)
public abstract class CameraMixin {
    /**
     * Target: every setRotation(yaw, pitch) call in public void update(World, Entity, boolean, boolean, float).
     * While freelook is active the camera uses the free angles; the third-person offset is computed afterwards
     * from that rotation, so the camera orbits the player.
     */
    @WrapOperation(method = "alignWithEntity", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;setRotation(FF)V"))
    private void nightshade$freelookRotation(Camera camera, float yaw, float pitch, Operation<Void> original) {
        if (Freelook.isActive()) {
            original.call(camera, Freelook.yaw(), Freelook.pitch());
        } else {
            original.call(camera, yaw, pitch);
        }
    }

    /** Target (26.x): private float calculateFov(float) - the world field of view (Zoom). */
    @org.spongepowered.asm.mixin.injection.Inject(method = "calculateFov", at = @At("RETURN"), cancellable = true)
    private void nightshade$zoom(float partialTick, org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Float> cir) {
        double multiplier = com.nightshade.module.utility.Zoom.fovMultiplier();
        if (multiplier != 1.0) {
            cir.setReturnValue((float) (cir.getReturnValueF() * multiplier));
        }
    }
}
