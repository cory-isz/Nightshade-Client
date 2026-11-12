package com.nightshade.module.interfaces;

import com.nightshade.NightshadeClient;
import com.nightshade.event.events.HudRenderEvent;
import com.nightshade.module.BindMode;
import com.nightshade.input.Keybind;
import com.nightshade.module.Module;
import com.nightshade.module.ModuleCategory;
import com.nightshade.render.ColorUtil;
import com.nightshade.render.Fonts;
import com.nightshade.render.RenderUtil;
import com.nightshade.render.Theme;
import com.nightshade.util.EffectProbe;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/** Hold K to see every module that has a keybind, its key and whether it is on, in a panel in the middle of the screen. */
public final class KeybindOverlay extends Module {
    public KeybindOverlay() {
        super("Keybind Overlay", "Hold the key to see all your keybinds", ModuleCategory.INTERFACE);
        defaultKeybind(Keybind.key(GLFW.GLFW_KEY_K));
        defaultBindMode(BindMode.HOLD);
        listen(HudRenderEvent.class, event -> render(event.context()));
    }

    private void render(GuiGraphicsExtractor ctx) {
        if (mc.screen != null) {
            return;
        }
        List<Module> bound = new ArrayList<>();
        for (Module m : NightshadeClient.client().modules().getModules()) {
            if (m != this && !m.getKeybind().displayName().equalsIgnoreCase("None")) {
                bound.add(m);
            }
        }
        bound.sort((a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        int rowH = Fonts.height() + 4;
        int cols = bound.size() > 12 ? 2 : 1;
        int rows = Math.max(1, (bound.size() + cols - 1) / cols);
        int colW = 150;
        int w = cols * colW + 16;
        int h = 28 + rows * rowH + 6;
        int x = (ctx.guiWidth() - w) / 2;
        int y = Math.max(6, (ctx.guiHeight() - h) / 2);
        RenderUtil.roundedRect(ctx, x, y, x + w, y + h, 8, Theme.tint(0xE0100A1E));
        RenderUtil.roundedOutline(ctx, x, y, x + w, y + h, 8, ColorUtil.withAlpha(Theme.accent(), 0x90));
        Fonts.draw(ctx, "Keybinds", x + 10, y + 8, 0xFFFFFFFF, false);
        String hint = bound.isEmpty() ? "No keybinds yet - set them in the modules menu" : bound.size() + " bound";
        Fonts.draw(ctx, hint, x + w - 10 - Fonts.width(hint), y + 8, Theme.TEXT_DIM, false);
        for (int i = 0; i < bound.size(); i++) {
            Module m = bound.get(i);
            int cx = x + 8 + (i / rows) * colW;
            int cy = y + 26 + (i % rows) * rowH;
            String key = m.getKeybind().displayName();
            int kw = Fonts.width(key) + 8;
            RenderUtil.roundedRect(ctx, cx, cy, cx + kw, cy + rowH - 2, 3, 0x30FFFFFF);
            Fonts.draw(ctx, key, cx + 4, cy + 1, Theme.TEXT, false);
            boolean on = m.isToggleable() && m.isEnabled();
            Fonts.draw(ctx, Fonts.trim(m.getName(), colW - kw - 22), cx + kw + 6, cy + 1, on ? Theme.accentLight() : Theme.TEXT_MUTED, false);
            if (on) {
                RenderUtil.roundedRect(ctx, cx + colW - 14, cy + 3, cx + colW - 9, cy + 8, 2, Theme.SUCCESS);
            }
        }
        EffectProbe.hit("Keybind Overlay");
    }
}
