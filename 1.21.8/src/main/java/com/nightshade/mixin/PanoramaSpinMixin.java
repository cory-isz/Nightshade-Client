package com.nightshade.mixin;

import com.nightshade.module.interfaces.MainMenuModule;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Animate Background off: the Minecraft panorama stops spinning. */
@Mixin(Screen.class)
public abstract class PanoramaSpinMixin {
    /** Target (1.21.8): the spin flag passed to PanoramaRenderer.render(...) in renderPanorama(GuiGraphics, float). */
    @ModifyArg(method = "renderPanorama",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/PanoramaRenderer;render(Lnet/minecraft/client/gui/GuiGraphics;IIZ)V"), index = 3)
    private boolean nightshade$still(boolean spin) {
        return spin && MainMenuModule.animateBackground();
    }
}
