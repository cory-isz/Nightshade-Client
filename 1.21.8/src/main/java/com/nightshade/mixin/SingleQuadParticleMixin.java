package com.nightshade.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.nightshade.module.visual.Particles;
import net.minecraft.client.particle.SingleQuadParticle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Particles: draws each particle at the size chosen for its group. */
@Mixin(SingleQuadParticle.class)
public abstract class SingleQuadParticleMixin {
    /**
     * Target: the getQuadSize(partialTick) read in renderRotatedQuad(buffer, rotation, x, y, z, partialTick) - the size the
     * quad is drawn at. Hooking the read (not getQuadSize itself) also covers particles that override getQuadSize.
     */
    @ModifyExpressionValue(method = "renderRotatedQuad(Lcom/mojang/blaze3d/vertex/VertexConsumer;Lorg/joml/Quaternionf;FFFF)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/particle/SingleQuadParticle;getQuadSize(F)F"))
    private float nightshade$size(float size) {
        return Particles.drawSize(this, size);
    }
}
