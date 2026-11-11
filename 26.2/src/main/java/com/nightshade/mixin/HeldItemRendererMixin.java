package com.nightshade.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.nightshade.module.pvp.ItemView;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemInHandRenderer.class)
public abstract class HeldItemRendererMixin {
    /** Target (26.2): private void submitArmWithItem(...) - wraps the whole first-person hand in our transform. */
    @Inject(method = "submitArmWithItem", at = @At("HEAD"))
    private void nightshade$push(AbstractClientPlayer player, float tickProgress, float pitch, InteractionHand hand, float swingProgress,
                                 ItemStack item, float equipProgress, PoseStack matrices, SubmitNodeCollector queue,
                                 int light, CallbackInfo ci) {
        com.nightshade.module.pvp.ShieldStatus.firstPersonPass = true;
        matrices.pushPose();
        ItemView.apply(matrices, player, hand, item);
    }

    @Inject(method = "submitArmWithItem", at = @At("RETURN"))
    private void nightshade$pop(AbstractClientPlayer player, float tickProgress, float pitch, InteractionHand hand, float swingProgress,
                                ItemStack item, float equipProgress, PoseStack matrices, SubmitNodeCollector queue,
                                int light, CallbackInfo ci) {
        com.nightshade.module.pvp.ShieldStatus.firstPersonPass = false;
        matrices.popPose();
    }
}
