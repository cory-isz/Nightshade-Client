package com.nightshade.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.nightshade.module.performance.BlockEntityCulling;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BlockEntityRenderDispatcher.class)
public abstract class BlockEntityRenderManagerMixin {
    @Shadow
    public Camera camera;

    /** Target: public void render(BlockEntity, float, PoseStack, MultiBufferSource) - skips culled block entities this frame. */
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private <E extends BlockEntity> void nightshade$cull(E blockEntity, float tickProgress, PoseStack poseStack, MultiBufferSource buffers,
            CallbackInfo ci) {
        if (camera != null && BlockEntityCulling.shouldSkip(blockEntity, camera.getPosition())) {
            ci.cancel();
        }
    }
}
