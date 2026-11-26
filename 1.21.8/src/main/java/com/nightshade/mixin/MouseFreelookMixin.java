package com.nightshade.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.nightshade.module.utility.Freelook;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(MouseHandler.class)
public abstract class MouseFreelookMixin {
    /** Target: the player.changeLookDirection(dx, dy) call in private void updateMouse(double). */
    @WrapOperation(method = "turnPlayer",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;turn(DD)V"))
    private void nightshade$freelook(LocalPlayer player, double dx, double dy, Operation<Void> original) {
        if (!Freelook.consumeMouse(dx, dy)) {
            original.call(player, dx, dy);
        }
    }
}
