package com.nightshade.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.nightshade.module.visual.ItemPhysics;
import com.nightshade.render.ItemPhysicsState;
import net.minecraft.client.renderer.entity.ItemEntityRenderer;
import net.minecraft.client.renderer.entity.state.ItemEntityRenderState;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;
import org.joml.Quaternionfc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Item Physics: replaces the floating bob and spin of dropped items. */
@Mixin(ItemEntityRenderer.class)
public abstract class ItemEntityRendererMixin {
    /** Target: public void extractRenderState(ItemEntity, ItemEntityRenderState, float) - remember whether it is on the ground. */
    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/item/ItemEntity;Lnet/minecraft/client/renderer/entity/state/ItemEntityRenderState;F)V",
            at = @At("TAIL"))
    private void nightshade$extract(ItemEntity entity, ItemEntityRenderState state, float partialTick, CallbackInfo ci) {
        ((ItemPhysicsState) state).nightshade$setOnGround(entity.onGround());
    }

    /** Target: the bob translate(0, bob + offset, 0) in submit(...). */
    @WrapOperation(method = "submit(Lnet/minecraft/client/renderer/entity/state/ItemEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(FFF)V", ordinal = 0))
    private void nightshade$bob(PoseStack poseStack, float x, float y, float z, Operation<Void> original,
                                @Local(argsOnly = true) ItemEntityRenderState state) {
        if (!ItemPhysics.active() || !((ItemPhysicsState) state).nightshade$onGround()) {
            original.call(poseStack, x, y, z);
            return;
        }
        AABB box = state.item.getModelBoundingBox();
        if (ItemPhysics.isFlat(box)) {
            // Laid down by the extra X rotation below: its depth becomes its height.
            original.call(poseStack, 0f, (float) box.maxZ + 0.005f, 0f);
        } else {
            original.call(poseStack, 0f, (float) -box.minY + 0.001f, 0f);
        }
        ItemPhysics.applied();
    }

    /** Target: the spin mulPose(Axis.YP.rotation(spin)) in submit(...). */
    @WrapOperation(method = "submit(Lnet/minecraft/client/renderer/entity/state/ItemEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;mulPose(Lorg/joml/Quaternionfc;)V", ordinal = 0))
    private void nightshade$spin(PoseStack poseStack, Quaternionfc rotation, Operation<Void> original,
                                 @Local(argsOnly = true) ItemEntityRenderState state) {
        if (!ItemPhysics.active()) {
            original.call(poseStack, rotation);
            return;
        }
        // A fixed yaw per item (from its bob offset) so items on the ground point in different directions.
        float yaw = state.bobOffset * 57.2958f;
        boolean onGround = ((ItemPhysicsState) state).nightshade$onGround();
        AABB box = state.item.getModelBoundingBox();
        if (onGround) {
            original.call(poseStack, Axis.YP.rotationDegrees(yaw));
            if (ItemPhysics.isFlat(box)) {
                poseStack.mulPose(Axis.XP.rotationDegrees(90f));
            }
        } else if (ItemPhysics.tumble()) {
            float t = state.ageInTicks * 12f * ItemPhysics.tumbleSpeed();
            original.call(poseStack, Axis.YP.rotationDegrees(yaw + t * 0.5f));
            poseStack.mulPose(Axis.XP.rotationDegrees(t));
            ItemPhysics.applied();
        } else {
            original.call(poseStack, Axis.YP.rotationDegrees(yaw));
        }
    }
}
