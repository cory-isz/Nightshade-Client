package com.nightshade.mixin;

import com.nightshade.render.MenuBackground;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Nightshade Sky everywhere: every screen that would draw the Minecraft panorama (options, world creation, Realms,
 * the vanilla title screen...) draws the Nightshade sky instead while that background is selected, so it never jumps
 * back to the panorama when a button is pressed.
 */
@Mixin(Screen.class)
public abstract class ScreenPanoramaMixin {
    /** Target: protected void renderPanorama(GuiGraphics, float). */
    @Inject(method = "renderPanorama", at = @At("HEAD"), cancellable = true)
    private void nightshade$sky(GuiGraphics graphics, float partialTick, CallbackInfo ci) {
        if (MenuBackground.skyActive()) {
            MenuBackground.sky(graphics, graphics.guiWidth(), graphics.guiHeight());
            MenuBackground.lights(graphics, graphics.guiWidth(), graphics.guiHeight());
            ci.cancel();
        }
    }
}
