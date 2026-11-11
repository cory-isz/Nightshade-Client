package com.nightshade.mixin;

import com.nightshade.module.performance.BlockEntityCulling;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockEntityRenderDispatcher.class)
public abstract class BlockEntityRenderManagerMixin {
    @Shadow
    private Vec3 cameraPos;

    /**
     * Target: public BlockEntityRenderState getRenderState(BlockEntity, float, CrumblingOverlayCommand). Vanilla returns
     * null when a block entity has no renderer and the world renderer skips null states, so returning null here
     * simply skips extracting and drawing that block entity this frame.
     */
    @Inject(method = "tryExtractRenderState", at = @At("HEAD"), cancellable = true)
    private <E extends BlockEntity, S extends BlockEntityRenderState> void nightshade$cull(E blockEntity, float tickProgress,
            ModelFeatureRenderer.CrumblingOverlay crumbling, boolean flag, CallbackInfoReturnable<S> cir) {
        if (crumbling == null && cameraPos != null && BlockEntityCulling.shouldSkip(blockEntity, cameraPos)) {
            cir.setReturnValue(null);
        }
    }
}
