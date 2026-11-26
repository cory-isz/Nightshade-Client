package com.nightshade.module.hud;

import com.nightshade.event.events.HitLandedEvent;
import com.nightshade.event.events.HurtByEvent;
import com.nightshade.event.events.TickEvent;
import com.nightshade.event.events.WorldChangeEvent;
import com.nightshade.module.HudModule;
import com.nightshade.module.ModuleCategory;
import com.nightshade.module.setting.BooleanSetting;
import com.nightshade.module.setting.NumberSetting;
import com.nightshade.render.ColorUtil;
import com.nightshade.render.Fonts;
import com.nightshade.render.Theme;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.Locale;

/**
 * Practise your combos: your live combo, your best combo and average combo this session, your hit trade (hits you
 * landed : hits you took) and a small chart of your last ten combos. A combo is hits landed in a row without being
 * hit back; it ends when you get hit or stop hitting for a few seconds.
 */
public final class ComboTrainerHud extends HudModule {
    private static final int HISTORY = 10;
    private final NumberSetting timeout = add(new NumberSetting("Combo Ends After", "Seconds without a hit before a combo ends", 2.5, 1, 6, 0.5, "s"));
    private final BooleanSetting chart = add(new BooleanSetting("Combo Chart", "A bar for each of your last ten combos", true));
    private int combo;
    private int best;
    private int ended;
    private int totalInEnded;
    private int landed;
    private int taken;
    private long lastHit;
    private int lastHurtTime;
    private final int[] recent = new int[HISTORY];
    private int recentCount;
    private long flashUntil;

    public ComboTrainerHud() {
        super("Combo Trainer", "Live combo, best and average combo, hit trade and a chart of recent combos", ModuleCategory.PVP, 0.5, 0.74);
        listen(HitLandedEvent.class, e -> {
            combo++;
            landed++;
            lastHit = System.currentTimeMillis();
            if (combo > best) {
                best = combo;
                if (best >= 3) {
                    flashUntil = lastHit + 800; // new personal best this session
                }
            }
        });
        listen(HurtByEvent.class, e -> {
            if (e.attacker() != null) {
                taken++;
                endCombo();
            }
        });
        listen(TickEvent.Post.class, e -> tick());
        listen(WorldChangeEvent.class, e -> reset());
    }

    private void tick() {
        if (mc.player == null) {
            return;
        }
        int hurt = mc.player.hurtTime;
        if (hurt > lastHurtTime && hurt > 0) {
            endCombo();
        }
        lastHurtTime = hurt;
        if (combo > 0 && System.currentTimeMillis() - lastHit > timeout.get() * 1000.0) {
            endCombo();
        }
    }

    private void endCombo() {
        if (combo == 0) {
            return;
        }
        System.arraycopy(recent, 0, recent, 1, HISTORY - 1);
        recent[0] = combo;
        recentCount = Math.min(HISTORY, recentCount + 1);
        ended++;
        totalInEnded += combo;
        combo = 0;
    }

    public void reset() {
        combo = best = ended = totalInEnded = landed = taken = recentCount = 0;
    }

    @Override
    protected void onDisable() {
        reset();
    }

    // ------------------------------------------------------------------ test hooks

    public int comboForTest() {
        return combo;
    }

    public int bestForTest() {
        return best;
    }

    public void endForTest() {
        endCombo();
    }

    // ------------------------------------------------------------------ drawing

    private String line1() {
        return combo > 0 ? combo + " Combo" : "No combo";
    }

    private String line2() {
        String avg = ended == 0 ? "-" : String.format(Locale.ROOT, "%.1f", totalInEnded / (double) ended);
        return "Best " + best + "  ·  Avg " + avg + "  ·  " + landed + ":" + taken;
    }

    @Override
    public int getWidth() {
        return Math.max(96, Math.max(Fonts.width(line1()), Fonts.width(line2())) + 8);
    }

    @Override
    public int getHeight() {
        return (Fonts.height() + 2) * 2 + 6 + (chart.get() ? 14 : 0);
    }

    @Override
    protected void renderElement(GuiGraphicsExtractor ctx, float tickDelta, boolean preview) {
        int w = getWidth();
        drawBackground(ctx, w, getHeight());
        boolean flash = System.currentTimeMillis() < flashUntil;
        int c1 = flash ? 0xFFFFC857 : combo >= 3 ? Theme.accentLight() : textColor.argb();
        Fonts.draw(ctx, line1(), 4, 4, c1, shadow());
        if (flash) {
            Fonts.draw(ctx, "new best!", w - 4 - Fonts.width("new best!"), 4, 0xFFFFC857, shadow());
        }
        int tradeColor = landed > taken ? Theme.SUCCESS : landed < taken ? Theme.DANGER : Theme.TEXT_MUTED;
        Fonts.draw(ctx, line2(), 4, 6 + Fonts.height(), ColorUtil.lerp(Theme.TEXT_MUTED, tradeColor, 0.5f), shadow());
        if (chart.get()) {
            int y0 = getHeight() - 4;
            int bw = (w - 8 - (HISTORY - 1) * 2) / HISTORY;
            int top = Math.max(1, best);
            for (int i = 0; i < HISTORY; i++) {
                int idx = HISTORY - 1 - i; // oldest on the left, newest on the right
                int v = preview && recentCount == 0 ? (i * 7 % 5) + 1 : idx < recentCount ? recent[idx] : 0;
                int h = v == 0 ? 1 : Math.max(2, Math.round(10f * v / Math.max(top, 5)));
                int x = 4 + i * (bw + 2);
                ctx.fill(x, y0 - h, x + bw, y0, v == 0 ? 0x30FFFFFF : v >= 3 ? Theme.accentLight() : 0xC0B7ACD6);
            }
        }
    }
}
