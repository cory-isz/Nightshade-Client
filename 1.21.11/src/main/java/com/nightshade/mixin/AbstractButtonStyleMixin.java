package com.nightshade.mixin;

import com.nightshade.render.WidgetStyle;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Styled Buttons: replaces the stone button sprite with the Nightshade glass button. */
@Mixin(AbstractButton.class)
public abstract class AbstractButtonStyleMixin extends AbstractWidget {
    private AbstractButtonStyleMixin(int x, int y, int w, int h, Component message) {
        super(x, y, w, h, message);
    }

    /** Target: protected final void renderDefaultSprite(GuiGraphics). */
    @Inject(method = "renderDefaultSprite", at = @At("HEAD"), cancellable = true)
    private void nightshade$styledButton(GuiGraphics graphics, CallbackInfo ci) {
        if (WidgetStyle.enabled()) {
            WidgetStyle.button(graphics, getX(), getY(), getWidth(), getHeight(), active, isHoveredOrFocused(), alpha);
            ci.cancel();
        }
    }
}
