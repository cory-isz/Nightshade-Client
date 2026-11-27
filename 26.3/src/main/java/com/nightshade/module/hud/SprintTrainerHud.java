package com.nightshade.module.hud;

import com.nightshade.event.events.AttackEntityEvent;
import com.nightshade.event.events.TickEvent;
import com.nightshade.event.events.WorldChangeEvent;
import com.nightshade.module.HudModule;
import com.nightshade.module.ModuleCategory;
import com.nightshade.module.setting.BooleanSetting;
import com.nightshade.render.Fonts;
import com.nightshade.render.Theme;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.LivingEntity;

import java.util.Locale;

/**
 * Sprint-reset trainer: shows whether you W-tapped between your hits, and how long you let go of forward for. A W-tap
 * counts as on time when you let go of forward after your last hit, for between {@value #MIN_TAP} and {@value #MAX_TAP}
 * ms, and are sprinting again when you hit. It only watches your keys; it never presses anything.
 */
public final class SprintTrainerHud extends HudModule {
    public static final int MIN_TAP = 40;
    public static final int MAX_TAP = 300;
    /** Hits further apart than this start a new exchange and are not graded. */
    private static final long CHAIN_MS = 1500;
    private static final int HISTORY = 10;

    public enum Grade {
        GOOD("On time", Theme.SUCCESS),
        LONG("Too long", Theme.WARNING),
        SHORT("Too short", Theme.WARNING),
        NOT_SPRINTING("Not sprinting", Theme.DANGER),
        MISSED("No W-tap", Theme.DANGER);

        public final String label;
        public final int color;

        Grade(String label, int color) {
            this.label = label;
            this.color = color;
        }
    }

    private final BooleanSetting chart = add(new BooleanSetting("Hit Chart", "A dot for each of your last ten graded hits", true));

    private boolean wasDown = true;
    private long releasedAt;
    private long releaseLength = -1; // length of the last completed release since the previous hit, -1 = none
    private boolean releasedSinceHit;
    private long lastHit;
    private Grade last;
    private long lastLength;
    private int graded;
    private int good;
    private long goodTotalMs;
    private final Grade[] recent = new Grade[HISTORY];
    private int recentCount;

    public SprintTrainerHud() {
        super("Sprint Trainer", "Shows whether you W-tapped on time between hits (display only)", ModuleCategory.PVP, 0.5, 0.66);
        listen(TickEvent.Post.class, e -> {
            if (mc.player != null) {
                input(mc.options.keyUp.isDown(), System.currentTimeMillis());
            }
        });
        listen(AttackEntityEvent.class, e -> {
            if (e.target() instanceof LivingEntity && mc.player != null) {
                hit(mc.options.keyUp.isDown(), mc.player.isSprinting(), System.currentTimeMillis());
            }
        });
        listen(WorldChangeEvent.class, e -> reset());
    }

    private void input(boolean down, long now) {
        if (wasDown && !down) {
            releasedAt = now;
        } else if (!wasDown && down && lastHit > 0 && releasedAt >= lastHit) {
            releaseLength = now - releasedAt;
            releasedSinceHit = true;
        }
        wasDown = down;
    }

    private void hit(boolean down, boolean sprinting, long now) {
        input(down, now);
        boolean chained = lastHit > 0 && now - lastHit <= CHAIN_MS;
        if (chained) {
            Grade g;
            if (!releasedSinceHit) {
                g = down ? Grade.MISSED : Grade.NOT_SPRINTING; // still holding back when you hit
            } else if (!sprinting) {
                g = Grade.NOT_SPRINTING;
            } else if (releaseLength < MIN_TAP) {
                g = Grade.SHORT;
            } else if (releaseLength > MAX_TAP) {
                g = Grade.LONG;
            } else {
                g = Grade.GOOD;
            }
            grade(g, releasedSinceHit ? releaseLength : 0);
        }
        lastHit = now;
        releasedSinceHit = false;
        releaseLength = -1;
    }

    private void grade(Grade g, long length) {
        last = g;
        lastLength = length;
        graded++;
        if (g == Grade.GOOD) {
            good++;
            goodTotalMs += length;
        }
        System.arraycopy(recent, 0, recent, 1, HISTORY - 1);
        recent[0] = g;
        recentCount = Math.min(HISTORY, recentCount + 1);
    }

    public void reset() {
        lastHit = 0;
        last = null;
        graded = good = recentCount = 0;
        goodTotalMs = 0;
        releasedSinceHit = false;
        releaseLength = -1;
    }

    @Override
    protected void onDisable() {
        reset();
    }

    // ------------------------------------------------------------------ test hooks

    public void inputForTest(boolean forwardDown, long now) {
        input(forwardDown, now);
    }

    public void hitForTest(boolean forwardDown, boolean sprinting, long now) {
        hit(forwardDown, sprinting, now);
    }

    public Grade lastForTest() {
        return last;
    }

    public int gradedForTest() {
        return graded;
    }

    public int goodForTest() {
        return good;
    }

    // ------------------------------------------------------------------ drawing

    private String line1() {
        if (last == null) {
            return "W-tap between hits";
        }
        return last == Grade.GOOD || last == Grade.LONG || last == Grade.SHORT ? last.label + "  " + lastLength + "ms" : last.label;
    }

    private String line2() {
        if (graded == 0) {
            return "On time: -";
        }
        String avg = good == 0 ? "" : String.format(Locale.ROOT, "  ·  avg %dms", goodTotalMs / good);
        return "On time " + good + "/" + graded + " (" + Math.round(good * 100f / graded) + "%)" + avg;
    }

    @Override
    public int getWidth() {
        return Math.max(104, Math.max(Fonts.width(line1()), Fonts.width(line2())) + 8);
    }

    @Override
    public int getHeight() {
        return (Fonts.height() + 2) * 2 + 6 + (chart.get() ? 10 : 0);
    }

    @Override
    protected void renderElement(GuiGraphicsExtractor ctx, float tickDelta, boolean preview) {
        int w = getWidth();
        drawBackground(ctx, w, getHeight());
        Fonts.draw(ctx, line1(), 4, 4, last == null ? textColor.argb() : last.color, shadow());
        Fonts.draw(ctx, line2(), 4, 6 + Fonts.height(), Theme.TEXT_MUTED, shadow());
        if (chart.get()) {
            int y = getHeight() - 8;
            for (int i = 0; i < HISTORY; i++) {
                int idx = HISTORY - 1 - i; // oldest on the left
                Grade g = idx < recentCount ? recent[idx] : preview && recentCount == 0 ? (i % 3 == 2 ? Grade.LONG : Grade.GOOD) : null;
                int x = 4 + i * 9;
                ctx.fill(x, y, x + 6, y + 5, g == null ? 0x30FFFFFF : g.color);
            }
        }
    }
}
