package com.nightshade.mixin;

import com.nightshade.module.performance.EffectReducer;
import com.nightshade.module.visual.CustomCrosshair;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public abstract class InGameHudMixin {
    /** Target: private void renderCrosshair(DrawContext, RenderTickCounter) */
    @Inject(method = "extractCrosshair", at = @At("HEAD"), cancellable = true)
    private void nightshade$crosshair(GuiGraphicsExtractor context, DeltaTracker tickCounter, CallbackInfo ci) {
        if (CustomCrosshair.render(context)) {
            ci.cancel();
        }
    }

    /** Target: private void renderVignetteOverlay(DrawContext, Entity) */
    @Inject(method = "extractVignette", at = @At("HEAD"), cancellable = true)
    private void nightshade$vignette(GuiGraphicsExtractor context, Entity entity, CallbackInfo ci) {
        if (EffectReducer.hideVignette()) {
            ci.cancel();
        }
    }

    /** Target: private void renderOverlay(DrawContext, Identifier, float) - pumpkin blur and powder snow. */
    @Inject(method = "extractTextureOverlay", at = @At("HEAD"), cancellable = true)
    private void nightshade$overlay(GuiGraphicsExtractor context, Identifier texture, float opacity, CallbackInfo ci) {
        if (EffectReducer.hideOverlay(texture)) {
            ci.cancel();
        }
    }

    /** Target: private void renderPortalOverlay(DrawContext, float) */
    @Inject(method = "extractPortalOverlay", at = @At("HEAD"), cancellable = true)
    private void nightshade$portal(GuiGraphicsExtractor context, float nauseaStrength, CallbackInfo ci) {
        if (EffectReducer.hidePortal()) {
            ci.cancel();
        }
    }
}
