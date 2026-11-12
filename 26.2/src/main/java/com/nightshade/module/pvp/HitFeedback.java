package com.nightshade.module.pvp;

import com.nightshade.event.events.AttackEntityEvent;
import com.nightshade.event.events.HitLandedEvent;
import com.nightshade.event.events.WorldChangeEvent;
import com.nightshade.module.HudModule;
import com.nightshade.module.ModuleCategory;
import com.nightshade.module.setting.BooleanSetting;
import com.nightshade.render.ColorUtil;
import com.nightshade.render.Fonts;
import com.nightshade.render.Fx;
import com.nightshade.render.Theme;
import com.nightshade.util.EffectProbe;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import java.util.Locale;

/**
 * Tells you what kind of hit you just landed - a critical hit (falling, not sprinting) or a sprint hit (extra
 * knockback) - right under your crosshair, and keeps score of how many of your hits were sprint hits and crits, so
 * you can practise W-tapping and crit timing. Only counts hits the server confirmed.
 */
public final class HitFeedback extends HudModule {
    private final BooleanSetting normalHits = add(new BooleanSetting("Show Normal Hits", "Also flash \"HIT\" for plain hits", false));
    private final BooleanSetting stats = add(new BooleanSetting("Show Stats", "Show your sprint-hit and crit rate", true));

    private Entity pendingTarget;
    private boolean pendingCrit;
    private boolean pendingSprint;
    private long pendingAt;

    private String label = "";
    private int labelColor;
    private long shownAt;
    private int hits;
    private int sprintHits;
    private int crits;

    public HitFeedback() {
        super("Hit Feedback", "Shows CRIT or SPRINT when your hit lands, and your sprint-hit rate", ModuleCategory.PVP, 0.5, 0.6, false);
        listen(AttackEntityEvent.class, event -> {
            if (mc.player == null || !(event.target() instanceof LivingEntity)) {
                return;
            }
            var p = mc.player;
            boolean charged = p.getAttackStrengthScale(0.5f) > 0.9f;
            pendingCrit = charged && p.fallDistance > 0 && !p.onGround() && !p.onClimbable() && !p.isInWater()
                    && !p.hasEffect(MobEffects.BLINDNESS) && !p.isPassenger() && !p.isSprinting();
            pendingSprint = charged && p.isSprinting();
            pendingTarget = event.target();
            pendingAt = System.currentTimeMillis();
        });
        listen(HitLandedEvent.class, event -> {
            if (event.target() != pendingTarget || System.currentTimeMillis() - pendingAt > 800) {
                return;
            }
            pendingTarget = null;
            hits++;
            if (pendingCrit) {
                crits++;
                show("CRIT", 0xFFFFC857);
            } else if (pendingSprint) {
                sprintHits++;
                show("SPRINT", 0xFF6FE3FF);
            } else if (normalHits.get()) {
                show("HIT", 0xFFFFFFFF);
            }
            EffectProbe.hit("Hit Feedback");
        });
        listen(WorldChangeEvent.class, event -> resetStats());
    }

    private void show(String text, int color) {
        label = text;
        labelColor = color;
        shownAt = System.currentTimeMillis();
    }

    public void resetStats() {
        hits = 0;
        sprintHits = 0;
        crits = 0;
    }

    /** Test hooks. */
    public String lastLabelForTest() {
        return label;
    }

    public int hitsForTest() {
        return hits;
    }

    @Override
    public int getWidth() {
        return 96;
    }

    @Override
    public int getHeight() {
        return stats.get() ? 26 : 14;
    }

    @Override
    public boolean shouldRender() {
        return mc.player != null && (hits > 0 || System.currentTimeMillis() - shownAt < 900);
    }

    @Override
    protected void renderElement(GuiGraphicsExtractor context, float tickDelta, boolean preview) {
        int w = getWidth();
        drawBackground(context, w, getHeight());
        long age = System.currentTimeMillis() - shownAt;
        String text = preview && label.isEmpty() ? "SPRINT" : label;
        if (preview || age < 900) {
            float t = preview ? 0.3f : age / 900f;
            float alpha = t < 0.7f ? 1f : 1f - (t - 0.7f) / 0.3f;
            float pop = 1f + 0.35f * (1f - Fx.easeOutCubic(Math.min(1f, t * 4f)));
            int color = ColorUtil.withAlpha(preview && label.isEmpty() ? 0xFF6FE3FF : labelColor, Math.round(255 * alpha));
            context.pose().pushMatrix();
            context.pose().translate(w / 2f, 7f);
            context.pose().scale(pop, pop);
            Fonts.drawCentered(context, text, 0, -4, color, true);
            context.pose().popMatrix();
        }
        if (stats.get()) {
            String line = hits == 0 ? "Sprint --  ·  Crit --"
                    : String.format(Locale.ROOT, "Sprint %d%%  ·  Crit %d%%", Math.round(100f * sprintHits / hits), Math.round(100f * crits / hits));
            Fonts.drawCentered(context, line, w / 2, 16, Theme.TEXT_MUTED, shadow());
        }
    }
}
