package com.nightshade.gui.menu;

import com.nightshade.gui.NsScreen;
import com.nightshade.render.Fx;
import com.nightshade.render.MenuBackground;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/** Base for the Nightshade menu screens: they all share the same background (Nightshade Sky or the panorama). */
public abstract class NsMenuScreen extends NsScreen {
    protected NsMenuScreen(Component title) {
        super(title);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        if (minecraft != null && minecraft.level != null) {
            // Opened in game: blur the world and tint it in the client colour.
            boolean blur = com.nightshade.module.performance.MenuOptimizer.allowBlur();
            if (blur) {
                extractBlurredBackground(ctx);
            }
            Fx.verticalGradient(ctx, 0, 0, width, height, com.nightshade.render.Theme.tint(blur ? 0x900A0614 : 0xB80A0614), com.nightshade.render.Theme.tint(blur ? 0xD00A0614 : 0xE00A0614));
            return;
        }
        switch (MenuBackground.mode()) {
            case "Panorama" -> {
                extractPanorama(ctx, delta);
                Fx.verticalGradient(ctx, 0, 0, width, height, com.nightshade.render.Theme.tint(0x480C0618), com.nightshade.render.Theme.tint(0xB80A0514));
                MenuBackground.lights(ctx, width, height);
            }
            case "Blurred Panorama" -> {
                extractPanorama(ctx, delta);
                if (com.nightshade.module.performance.MenuOptimizer.allowBlur()) {
                    extractBlurredBackground(ctx);
                }
                Fx.verticalGradient(ctx, 0, 0, width, height, com.nightshade.render.Theme.tint(0x600C0618), com.nightshade.render.Theme.tint(0xC00A0514));
                MenuBackground.lights(ctx, width, height);
            }
            default -> {
                MenuBackground.parallax(mouseX, mouseY, width, height);
                MenuBackground.sky(ctx, width, height);
                MenuBackground.lights(ctx, width, height);
            }
        }
        Fx.vignette(ctx, width, height, com.nightshade.render.Theme.tint(0xC8050210));
    }

    /**
     * The full-page frame shared by the Nightshade menus: soft header and footer bands across the whole screen and an
     * accent hairline under the header, instead of a small box in the middle.
     */
    protected void renderPage(GuiGraphicsExtractor ctx, int x1, int x2) {
        int dark = com.nightshade.render.Theme.tint(0xC80A0614);
        ctx.fillGradient(0, 0, width, 46, dark, 0x000A0614);
        ctx.fillGradient(0, height - 42, width, height, 0x000A0614, com.nightshade.render.Theme.tint(0xD80A0614));
        int line = com.nightshade.render.ColorUtil.withAlpha(com.nightshade.render.Theme.accentLight(), 0x70);
        int mid = (x1 + x2) / 2;
        com.nightshade.render.RenderUtil.horizontalGradient(ctx, x1, 37, mid, 38, 0x00000000, line);
        com.nightshade.render.RenderUtil.horizontalGradient(ctx, mid, 37, x2, 38, line, 0x00000000);
    }
}
