package com.nightshade.module.visual;

import com.nightshade.event.events.HudRenderEvent;
import com.nightshade.module.Module;
import com.nightshade.module.ModuleCategory;
import com.nightshade.render.ColorUtil;
import com.nightshade.render.Fonts;
import com.nightshade.render.Theme;
import com.nightshade.util.EffectProbe;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.phys.Vec3;

import java.util.Locale;

/** Shows how many seconds are left above every lit TNT. */
public final class TntCountdown extends Module {
    public TntCountdown() {
        super("TNT Countdown", "Shows the seconds left above lit TNT", ModuleCategory.VISUAL);
        listen(HudRenderEvent.class, event -> render(event.context(), event.tickDelta()));
    }

    private void render(GuiGraphics ctx, float tickDelta) {
        if (mc.level == null || mc.player == null || mc.options.hideGui) {
            return;
        }
        int w = ctx.guiWidth();
        int h = ctx.guiHeight();
        for (Entity e : mc.level.entitiesForRendering()) {
            if (!(e instanceof PrimedTnt tnt) || e.distanceToSqr(mc.player) > 64 * 64) {
                continue;
            }
            Vec3 pos = e.getPosition(tickDelta).add(0, 1.4, 0);
            Vec3 ndc = mc.gameRenderer.projectPointToScreen(pos);
            if (ndc.z <= 0 || ndc.z >= 1 || Math.abs(ndc.x) > 1.1 || Math.abs(ndc.y) > 1.1) {
                continue;
            }
            float seconds = Math.max(0, tnt.getFuse() - tickDelta) / 20f;
            String text = String.format(Locale.ROOT, "%.1f", seconds);
            int color = seconds < 1f ? Theme.DANGER : seconds < 2f ? Theme.WARNING : Theme.SUCCESS;
            int sx = (int) ((ndc.x + 1) / 2 * w);
            int sy = (int) ((1 - ndc.y) / 2 * h);
            int tw = Fonts.width(text) + 8;
            com.nightshade.render.RenderUtil.roundedRect(ctx, sx - tw / 2, sy - 6, sx + tw / 2, sy + 6, 4, ColorUtil.withAlpha(Theme.BACKGROUND, 0xC0));
            Fonts.drawCentered(ctx, text, sx, sy - 4, color, false);
            EffectProbe.hit("TNT Countdown");
        }
    }
}
