package com.nightshade.mixin;

import com.nightshade.module.interfaces.MainMenuModule;
import net.minecraft.client.renderer.Panorama;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Animate Background off: the Minecraft panorama stops spinning. */
@Mixin(Panorama.class)
public abstract class PanoramaSpinMixin {
    @Shadow
    private boolean shouldSpin;

    /** Target: public void extractRenderState(GuiGraphicsExtractor, int, int). */
    @Inject(method = "extractRenderState", at = @At("HEAD"))
    private void nightshade$still(CallbackInfo ci) {
        if (!MainMenuModule.animateBackground()) {
            shouldSpin = false;
        }
    }
}
