package com.nightshade.module.pvp;

import com.nightshade.event.events.HudRenderEvent;
import com.nightshade.event.events.TickEvent;
import com.nightshade.module.Module;
import com.nightshade.module.ModuleCategory;
import com.nightshade.module.setting.BooleanSetting;
import com.nightshade.module.setting.NumberSetting;
import com.nightshade.render.ColorUtil;
import com.nightshade.render.Fonts;
import com.nightshade.util.EffectProbe;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Floating damage numbers that pop up and fade above entities when they lose health (and green ones on healing). */
public final class DamageIndicators extends Module {
    private final NumberSetting range = add(new NumberSetting("Range", "Show numbers for entities this close", 24, 8, 64, 4, " blocks"));
    private final BooleanSetting playersOnly = add(new BooleanSetting("Players Only", "Only show numbers for players", false));
    private final BooleanSetting healing = add(new BooleanSetting("Show Healing", "Green numbers when health goes up", true));
    private final NumberSetting size = add(new NumberSetting("Size", "Text size", 1.0, 0.5, 2.0, 0.1, "x"));

    private final Map<LivingEntity, Float> lastHealth = new IdentityHashMap<>();
    private final List<Popup> popups = new ArrayList<>();

    private static final class Popup {
        final Vec3 pos;
        final float amount;
        final float drift;
        int age;

        Popup(Vec3 pos, float amount, float drift) {
            this.pos = pos;
            this.amount = amount;
            this.drift = drift;
        }
    }

    public DamageIndicators() {
        super("Damage Indicators", "Floating damage numbers above entities you fight", ModuleCategory.PVP);
        listen(TickEvent.Post.class, event -> tick());
        listen(HudRenderEvent.class, event -> render(event.context(), event.tickDelta()));
    }

    private void tick() {
        if (mc.level == null || mc.player == null) {
            lastHealth.clear();
            popups.clear();
            return;
        }
        double r2 = range.get() * range.get();
        Map<LivingEntity, Float> seen = new IdentityHashMap<>();
        for (Entity e : mc.level.entitiesForRendering()) {
            if (!(e instanceof LivingEntity living) || e == mc.player || e.distanceToSqr(mc.player) > r2) {
                continue;
            }
            if (playersOnly.get() && !(e instanceof Player)) {
                continue;
            }
            float hp = living.getHealth() + living.getAbsorptionAmount();
            Float before = lastHealth.get(living);
            if (before != null) {
                float diff = before - hp;
                if (diff > 0.05f || (healing.get() && diff < -0.5f)) {
                    float drift = (float) ((Math.random() - 0.5) * 0.6);
                    popups.add(new Popup(living.position().add(0, living.getBbHeight() + 0.2, 0), diff, drift));
                    if (popups.size() > 40) {
                        popups.remove(0);
                    }
                }
            }
            seen.put(living, hp);
        }
        lastHealth.clear();
        lastHealth.putAll(seen);
        for (Iterator<Popup> it = popups.iterator(); it.hasNext(); ) {
            if (++it.next().age > 30) {
                it.remove();
            }
        }
    }

    private void render(GuiGraphicsExtractor ctx, float tickDelta) {
        if (popups.isEmpty() || mc.options.hideGui) {
            return;
        }
        int w = ctx.guiWidth();
        int h = ctx.guiHeight();
        for (Popup p : popups) {
            float t = (p.age + tickDelta) / 30f;
            Vec3 world = p.pos.add(p.drift * t, t * 0.9, 0);
            Vec3 ndc = mc.gameRenderer.projectPointToScreen(world);
            if (ndc.z <= 0 || ndc.z >= 1 || Math.abs(ndc.x) > 1.2 || Math.abs(ndc.y) > 1.2) {
                continue; // behind the camera or off screen
            }
            float sx = (float) ((ndc.x + 1) / 2 * w);
            float sy = (float) ((1 - ndc.y) / 2 * h);
            boolean damage = p.amount > 0;
            String text = (damage ? "" : "+") + String.format(Locale.ROOT, p.amount >= 10 || p.amount <= -10 ? "%.0f" : "%.1f", Math.abs(p.amount));
            int base = damage ? (p.amount >= 6 ? 0xFFFF3B3B : p.amount >= 3 ? 0xFFFF8A3D : 0xFFFFD45A) : 0xFF5BE49B;
            float pop = t < 0.15f ? 0.6f + t / 0.15f * 0.6f : 1.2f - Math.min(0.2f, (t - 0.15f));
            float scale = size.floatValue() * pop;
            int alpha = Math.round(255 * Math.min(1f, (1f - t) * 2.5f));
            if (alpha <= 4) {
                continue;
            }
            ctx.pose().pushMatrix();
            ctx.pose().translate(sx, sy);
            ctx.pose().scale(scale, scale);
            Fonts.drawCentered(ctx, text, 0, -Fonts.height() / 2, ColorUtil.withAlpha(base, alpha), true);
            ctx.pose().popMatrix();
            EffectProbe.hit("Damage Indicators");
        }
    }

    @Override
    protected void onDisable() {
        lastHealth.clear();
        popups.clear();
    }
}
