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

    /** Target: the spin rotate(Axis.YP, spin) in submit(...). */
    @WrapOperation(method = "submit(Lnet/minecraft/client/renderer/entity/state/ItemEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;rotate(Lcom/mojang/math/Axis;F)V", ordinal = 0))
    private void nightshade$spin(PoseStack poseStack, Axis axis, float angle, Operation<Void> original,
                                 @Local(argsOnly = true) ItemEntityRenderState state) {
        if (!ItemPhysics.active()) {
            original.call(poseStack, axis, angle);
            return;
        }
        float yaw = state.bobOffset;
        boolean onGround = ((ItemPhysicsState) state).nightshade$onGround();
        AABB box = state.item.getModelBoundingBox();
        if (onGround) {
            original.call(poseStack, Axis.YP, yaw);
            if (ItemPhysics.isFlat(box)) {
                poseStack.rotate(Axis.XP, (float) Math.toRadians(90));
            }
        } else if (ItemPhysics.tumble()) {
            float t = state.ageInTicks * 12f * ItemPhysics.tumbleSpeed();
            original.call(poseStack, Axis.YP, yaw + (float) Math.toRadians(t * 0.5f));
            poseStack.rotate(Axis.XP, (float) Math.toRadians(t));
            ItemPhysics.applied();
        } else {
            original.call(poseStack, Axis.YP, yaw);
        }
    }
}
