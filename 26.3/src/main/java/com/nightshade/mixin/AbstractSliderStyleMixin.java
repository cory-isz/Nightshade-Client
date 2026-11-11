package com.nightshade.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.nightshade.render.WidgetStyle;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Styled Buttons: rounded slider track and knob in the client colour. */
@Mixin(AbstractSliderButton.class)
public abstract class AbstractSliderStyleMixin extends AbstractWidget {
    private AbstractSliderStyleMixin(int x, int y, int w, int h, Component message) {
        super(x, y, w, h, message);
    }

    /** Target: the track blitSprite (ordinal 0) in renderWidget. */
    @WrapOperation(method = "extractWidgetRenderState", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blitSprite(Lcom/mojang/renderpearl/api/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIIII)V", ordinal = 0))
    private void nightshade$track(GuiGraphicsExtractor graphics, RenderPipeline pipeline, Identifier sprite, int x, int y, int w, int h, int color, Operation<Void> original) {
        if (WidgetStyle.enabled()) {
            WidgetStyle.sliderTrack(graphics, x, y, w, h, isHoveredOrFocused(), alpha);
        } else {
            original.call(graphics, pipeline, sprite, x, y, w, h, color);
        }
    }

    /** Target: the handle blitSprite (ordinal 1) in renderWidget. */
    @WrapOperation(method = "extractWidgetRenderState", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blitSprite(Lcom/mojang/renderpearl/api/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIIII)V", ordinal = 1))
    private void nightshade$handle(GuiGraphicsExtractor graphics, RenderPipeline pipeline, Identifier sprite, int x, int y, int w, int h, int color, Operation<Void> original) {
        if (WidgetStyle.enabled()) {
            WidgetStyle.sliderHandle(graphics, x, y, w, h, getX(), isHoveredOrFocused(), alpha);
        } else {
            original.call(graphics, pipeline, sprite, x, y, w, h, color);
        }
    }
}
