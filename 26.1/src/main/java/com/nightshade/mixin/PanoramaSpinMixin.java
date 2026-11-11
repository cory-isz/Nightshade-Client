package com.nightshade.mixin;

import com.nightshade.module.interfaces.MainMenuModule;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Animate Background off: the Minecraft panorama stops spinning. */
@Mixin(Screen.class)
public abstract class PanoramaSpinMixin {
    /** Target: protected boolean panoramaShouldSpin(). */
    @Inject(method = "panoramaShouldSpin", at = @At("HEAD"), cancellable = true)
    private void nightshade$still(CallbackInfoReturnable<Boolean> cir) {
        if (!MainMenuModule.animateBackground()) {
            cir.setReturnValue(false);
        }
    }
}
