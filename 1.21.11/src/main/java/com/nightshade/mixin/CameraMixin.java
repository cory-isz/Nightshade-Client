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
    @WrapOperation(method = "setup", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;setRotation(FF)V"))
    private void nightshade$freelookRotation(Camera camera, float yaw, float pitch, Operation<Void> original) {
        if (Freelook.isActive()) {
            original.call(camera, Freelook.yaw(), Freelook.pitch());
        } else {
            original.call(camera, yaw, pitch);
        }
    }
}
