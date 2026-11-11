package com.nightshade.mixin;

import com.nightshade.module.visual.NoFog;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.FogRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FogRenderer.class)
public abstract class FogRendererMixin {
    /**
     * Target: public void updateBuffer(FogData) - uploads this frame's fog. Only the render-distance fog is pushed
     * back; environmental fog (water, lava, blindness, darkness, powder snow) is left untouched.
     */
    @Inject(method = "updateBuffer(Lnet/minecraft/client/renderer/fog/FogData;)V", at = @At("HEAD"))
    private void nightshade$distanceFog(FogData fog, CallbackInfo ci) {
        if (NoFog.active()) {
            float[] adjusted = NoFog.adjust(fog.renderDistanceStart, fog.renderDistanceEnd);
            fog.renderDistanceStart = adjusted[0];
            fog.renderDistanceEnd = adjusted[1];
        }
    }
}
