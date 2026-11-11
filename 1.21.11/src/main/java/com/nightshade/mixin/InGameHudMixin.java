package com.nightshade.mixin;

import com.nightshade.module.performance.EffectReducer;
import com.nightshade.module.visual.CustomCrosshair;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public abstract class InGameHudMixin {
    /** Target: private void renderCrosshair(DrawContext, RenderTickCounter) */
    @Inject(method = "renderCrosshair", at = @At("HEAD"), cancellable = true)
    private void nightshade$crosshair(GuiGraphics context, DeltaTracker tickCounter, CallbackInfo ci) {
        if (CustomCrosshair.render(context)) {
            ci.cancel();
        }
    }

    /** Target: private void renderVignetteOverlay(DrawContext, Entity) */
    @Inject(method = "renderVignette", at = @At("HEAD"), cancellable = true)
    private void nightshade$vignette(GuiGraphics context, Entity entity, CallbackInfo ci) {
        if (EffectReducer.hideVignette()) {
            ci.cancel();
        }
    }

    /** Target: private void renderOverlay(DrawContext, Identifier, float) - pumpkin blur and powder snow. */
    @Inject(method = "renderTextureOverlay", at = @At("HEAD"), cancellable = true)
    private void nightshade$overlay(GuiGraphics context, Identifier texture, float opacity, CallbackInfo ci) {
        if (EffectReducer.hideOverlay(texture)) {
            ci.cancel();
        }
    }

    /** Target: private void renderPortalOverlay(DrawContext, float) */
    @Inject(method = "renderPortalOverlay", at = @At("HEAD"), cancellable = true)
    private void nightshade$portal(GuiGraphics context, float nauseaStrength, CallbackInfo ci) {
        if (EffectReducer.hidePortal()) {
            ci.cancel();
        }
    }
}
