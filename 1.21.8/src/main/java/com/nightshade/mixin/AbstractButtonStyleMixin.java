package com.nightshade.mixin;

import com.nightshade.render.WidgetStyle;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Styled Buttons: replaces the stone button sprite with the Nightshade glass button. */
@Mixin(AbstractButton.class)
public abstract class AbstractButtonStyleMixin extends AbstractWidget {
    private AbstractButtonStyleMixin(int x, int y, int w, int h, Component message) {
        super(x, y, w, h, message);
    }

    /** Target (1.21.8): the button sprite blitSprite(...) call in renderWidget(GuiGraphics, int, int, float). */
    @Redirect(method = "renderWidget",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;blitSprite(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/ResourceLocation;IIIII)V"))
    private void nightshade$styledButton(GuiGraphics graphics, com.mojang.blaze3d.pipeline.RenderPipeline pipeline, net.minecraft.resources.ResourceLocation sprite,
                                         int x, int y, int w, int h, int color) {
        if (WidgetStyle.enabled()) {
            WidgetStyle.button(graphics, getX(), getY(), getWidth(), getHeight(), active, isHoveredOrFocused(), alpha);
        } else {
            graphics.blitSprite(pipeline, sprite, x, y, w, h, color);
        }
    }
}
