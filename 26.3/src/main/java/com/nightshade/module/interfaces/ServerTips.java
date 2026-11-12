package com.nightshade.module.interfaces;

import com.nightshade.NightshadeClient;
import com.nightshade.event.events.HudRenderEvent;
import com.nightshade.module.Module;
import com.nightshade.module.ModuleCategory;
import com.nightshade.render.ColorUtil;
import com.nightshade.render.Fonts;
import com.nightshade.render.Fx;
import com.nightshade.render.RenderUtil;
import com.nightshade.render.Theme;
import com.nightshade.util.EffectProbe;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.Locale;

/** The first time you join a server, a small card shows a few tips for playing there with Nightshade (once per server). */
public final class ServerTips extends Module {
    private static ServerTips instance;
    private static final long SHOW_MS = 9000;
    private String server = "";
    private long shownAt;

    public ServerTips() {
        super("Server Tips", "A short tips card the first time you join each server", ModuleCategory.INTERFACE);
        enabledByDefault();
        instance = this;
        listen(HudRenderEvent.class, event -> render(event.context()));
    }

    /** Called when you join a world or server. */
    public static void onJoin() {
        ServerTips module = instance;
        var mc = net.minecraft.client.Minecraft.getInstance();
        var data = mc.getCurrentServer();
        if (module == null || !module.isEnabled() || mc.getSingleplayerServer() != null || data == null || data.ip == null) {
            return;
        }
        String flag = "tips:" + data.ip.trim().toLowerCase(Locale.ROOT);
        var config = NightshadeClient.client().config();
        if (config.hasFlag(flag)) {
            return;
        }
        config.setFlag(flag);
        module.server = data.name == null || data.name.isBlank() ? data.ip : data.name;
        module.shownAt = System.currentTimeMillis();
    }

    public boolean showingForTest() {
        return System.currentTimeMillis() - shownAt < SHOW_MS;
    }

    private void render(GuiGraphicsExtractor ctx) {
        long age = System.currentTimeMillis() - shownAt;
        if (age > SHOW_MS || mc.gui.screen() != null) {
            return;
        }
        float in = Fx.easeOutCubic(Math.min(1f, age / 300f));
        float out = age > SHOW_MS - 400 ? (SHOW_MS - age) / 400f : 1f;
        float a = Math.max(0f, Math.min(in, out));
        String[] tips = {
                "Right Shift opens the modules menu",
                "Hold K to see all your keybinds",
                "Link a profile to this server: Multiplayer > right-click it > Profile"};
        int w = 20 + Math.max(Fonts.width("Welcome to " + server), maxWidth(tips));
        int h = 22 + tips.length * (Fonts.height() + 3) + 4;
        int x = (ctx.guiWidth() - w) / 2;
        int y = 8 - Math.round((1f - in) * 12f);
        RenderUtil.roundedRect(ctx, x, y, x + w, y + h, 7, ColorUtil.fade(Theme.tint(0xE6100A1E), a));
        RenderUtil.roundedOutline(ctx, x, y, x + w, y + h, 7, ColorUtil.fade(ColorUtil.withAlpha(Theme.accent(), 0xA0), a));
        Fonts.draw(ctx, "Welcome to " + server, x + 10, y + 7, ColorUtil.fade(0xFFFFFFFF, a), false);
        int ty = y + 21;
        for (String tip : tips) {
            RenderUtil.roundedRect(ctx, x + 10, ty + 3, x + 13, ty + 6, 1, ColorUtil.fade(Theme.accentLight(), a));
            Fonts.draw(ctx, tip, x + 18, ty, ColorUtil.fade(Theme.TEXT_MUTED, a), false);
            ty += Fonts.height() + 3;
        }
        float life = 1f - age / (float) SHOW_MS;
        RenderUtil.rect(ctx, x + 8, y + h - 2, x + 8 + Math.round((w - 16) * life), y + h - 1, ColorUtil.fade(Theme.accent(), a * 0.8f));
        EffectProbe.hit("Server Tips");
    }

    private static int maxWidth(String[] lines) {
        int w = 0;
        for (String l : lines) {
            w = Math.max(w, Fonts.width(l) + 8);
        }
        return w;
    }
}
