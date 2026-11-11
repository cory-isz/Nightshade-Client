package com.nightshade.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.nightshade.module.pvp.ItemView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FirstPersonHandsAndItemsRenderer.class)
public abstract class HeldItemRendererMixin {
    @Unique
    private boolean nightshade$pushed;

    /** Target (26.3): private void submitArmWithItem(PlayerRenderState, FirstPersonHandsAndItemsRenderState, ...) - render-state based. */
    @Inject(method = "submitArmWithItem", at = @At("HEAD"))
    private void nightshade$push(PlayerRenderState player, FirstPersonHandsAndItemsRenderState hands, float tickProgress, float pitch,
                                 InteractionHand hand, float swingProgress, ItemStack item, float equipProgress, PoseStack matrices,
                                 SubmitNodeCollector queue, int light, CallbackInfo ci) {
        com.nightshade.module.pvp.ShieldStatus.firstPersonPass = true;
        matrices.pushPose();
        nightshade$pushed = true;
        if (Minecraft.getInstance().player != null) {
            ItemView.apply(matrices, Minecraft.getInstance().player, hand, item);
        }
    }

    @Inject(method = "submitArmWithItem", at = @At("RETURN"))
    private void nightshade$pop(PlayerRenderState player, FirstPersonHandsAndItemsRenderState hands, float tickProgress, float pitch,
                                InteractionHand hand, float swingProgress, ItemStack item, float equipProgress, PoseStack matrices,
                                SubmitNodeCollector queue, int light, CallbackInfo ci) {
        com.nightshade.module.pvp.ShieldStatus.firstPersonPass = false;
        if (nightshade$pushed) {
            matrices.popPose();
            nightshade$pushed = false;
        }
    }
}
