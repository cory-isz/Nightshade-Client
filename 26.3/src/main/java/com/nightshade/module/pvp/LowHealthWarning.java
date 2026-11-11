package com.nightshade.module.pvp;

import com.nightshade.util.EffectProbe;
import com.nightshade.event.events.HudRenderEvent;
import com.nightshade.event.events.TickEvent;
import com.nightshade.module.Module;
import com.nightshade.module.ModuleCategory;
import com.nightshade.module.setting.BooleanSetting;
import com.nightshade.module.setting.ColorSetting;
import com.nightshade.module.setting.NumberSetting;
import com.nightshade.render.ColorUtil;
import com.nightshade.util.Sounds;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** A pulsing screen-edge glow (and optional heartbeat) when your health is low. */
public final class LowHealthWarning extends Module {
    private final NumberSetting hearts = add(new NumberSetting("Hearts", "Warn at or below this many hearts", 4, 1, 10, 0.5, " hearts"));
    private final BooleanSetting absorption = add(new BooleanSetting("Count Absorption", "Include golden hearts", true));
    private final ColorSetting color = add(new ColorSetting("Color", "Glow colour", 0xB0FF2040));
    private final NumberSetting thickness = add(new NumberSetting("Thickness", "Width of the glow", 40, 10, 120, 5, "px"));
    private final BooleanSetting pulse = add(new BooleanSetting("Pulse", "Pulse the glow", true));
    private final BooleanSetting heartbeat = add(new BooleanSetting("Heartbeat", "Play a soft heartbeat sound", false));

    private int beatTicks;

    public LowHealthWarning() {
        super("Low Health Warning", "Screen glow and heartbeat at low health", ModuleCategory.PVP);
        listen(HudRenderEvent.class, event -> render(event.context()));
        listen(TickEvent.Post.class, event -> tick());
    }

    public boolean isLow() {
        if (mc.player == null || mc.player.isCreative() || mc.player.isSpectator() || mc.player.isDeadOrDying()) {
            return false;
        }
        float health = mc.player.getHealth() + (absorption.get() ? mc.player.getAbsorptionAmount() : 0f);
        return health <= hearts.get() * 2.0;
    }

    private void tick() {
        if (heartbeat.get() && isLow()) {
            if (--beatTicks <= 0) {
                Sounds.play("Hat", 0.6f, 0.5f);
                beatTicks = 20;
            }
        } else {
            beatTicks = 0;
        }
    }

    private void render(GuiGraphicsExtractor ctx) {
        if (!isLow() || mc.gui.hud.isHidden()) {
            return;
        }
        float strength = 1f;
        if (pulse.get()) {
            strength = 0.55f + 0.45f * (float) Math.abs(Math.sin(System.currentTimeMillis() / 300.0));
        }
        EffectProbe.hit("Low Health Warning");
        int base = color.argb();
        int edge = ColorUtil.fade(base, strength);
        int clear = base & 0x00FFFFFF;
        int w = ctx.guiWidth();
        int h = ctx.guiHeight();
        int t = thickness.get().intValue();
        ctx.fillGradient(0, 0, w, t, edge, clear);
        ctx.fillGradient(0, h - t, w, h, clear, edge);
        // Horizontal gradients for the sides, drawn as thin vertical strips.
        int steps = Math.max(1, t / 2);
        for (int i = 0; i < steps; i++) {
            int c = ColorUtil.fade(edge, 1f - i / (float) steps);
            int x = i * t / steps;
            int x2 = (i + 1) * t / steps;
            ctx.fill(x, 0, x2, h, c);
            ctx.fill(w - x2, 0, w - x, h, c);
        }
    }
}
