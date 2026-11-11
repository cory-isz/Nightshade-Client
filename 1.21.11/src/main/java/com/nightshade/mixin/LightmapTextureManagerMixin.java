package com.nightshade.mixin;

import com.nightshade.module.visual.Fullbright;
import net.minecraft.client.renderer.LightTexture;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Slice;

@Mixin(LightTexture.class)
public abstract class LightmapTextureManagerMixin {
    /**
     * Target: the {@code options.getGamma().getValue()} read in {@code public void update(float)}. The slice starts
     * at the getGamma() call so the earlier darkness-effect-scale getValue() is not affected.
     */
    @ModifyExpressionValue(method = "updateLightTexture",
            slice = @Slice(from = @At(value = "INVOKE", target = "Lnet/minecraft/client/Options;gamma()Lnet/minecraft/client/OptionInstance;")),
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/OptionInstance;get()Ljava/lang/Object;", ordinal = 0))
    private Object nightshade$fullbright(Object original) {
        double gamma = Fullbright.gammaOverride();
        return Double.isNaN(gamma) ? original : (Object) gamma;
    }
}
