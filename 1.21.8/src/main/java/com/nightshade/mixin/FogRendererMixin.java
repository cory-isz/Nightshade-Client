package com.nightshade.mixin;

import com.nightshade.module.visual.NoFog;
import net.minecraft.client.renderer.fog.FogRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(FogRenderer.class)
public abstract class FogRendererMixin {
    /**
     * Target (1.21.11): the call to private updateBuffer(ByteBuffer, int, Vector4f, envStart, envEnd, renderStart,
     * renderEnd, skyEnd, cloudEnd) inside public setupFog(...). Only the render-distance fog (args 5 and 6) is pushed
     * back; environmental fog (water, lava, blindness, darkness, powder snow) is left untouched.
     */
    @ModifyArgs(method = "setupFog",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/fog/FogRenderer;updateBuffer(Ljava/nio/ByteBuffer;ILorg/joml/Vector4f;FFFFFF)V"))
    private void nightshade$distanceFog(Args args) {
        if (NoFog.active()) {
            float start = args.get(5);
            float end = args.get(6);
            float[] adjusted = NoFog.adjust(start, end);
            args.set(5, adjusted[0]);
            args.set(6, adjusted[1]);
        }
    }
}
