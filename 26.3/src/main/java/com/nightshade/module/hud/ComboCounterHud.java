package com.nightshade.module.hud;

import com.nightshade.event.events.HitLandedEvent;
import com.nightshade.event.events.TickEvent;
import com.nightshade.module.HudModule;
import com.nightshade.module.ModuleCategory;
import com.nightshade.module.setting.NumberSetting;
import com.nightshade.render.Fonts;
import com.nightshade.render.Theme;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Counts consecutive hits that landed without you taking damage. A hit counts when the server confirms you damaged
 * the target; taking damage or a pause (3 seconds by default) resets the combo.
 */
public final class ComboCounterHud extends HudModule {
    private final NumberSetting timeout = add(new NumberSetting("Reset After", "Seconds without a hit before the combo resets", 3, 1, 10, 0.5, "s"));
    private int combo;
    private long lastHitMillis;
    private int lastHurtTime;

    public ComboCounterHud() {
        super("Combo Counter", "Counts consecutive hits without taking damage", ModuleCategory.HUD, 0.5, 0.62);
        listen(HitLandedEvent.class, event -> {
            combo++;
            lastHitMillis = System.currentTimeMillis();
        });
        listen(TickEvent.Post.class, event -> tick());
    }

    private void tick() {
        if (mc.player == null) {
            combo = 0;
            return;
        }
        int hurt = mc.player.hurtTime;
        if (hurt > lastHurtTime && hurt > 0) {
            combo = 0; // we took damage
        }
        lastHurtTime = hurt;
        if (combo > 0 && System.currentTimeMillis() - lastHitMillis > timeout.get() * 1000.0) {
            combo = 0;
        }
    }

    @Override
    protected void onDisable() {
        combo = 0;
    }

    public int combo() {
        return combo;
    }

    @Override
    public int getWidth() {
        return Fonts.width(text()) + 8;
    }

    private String text() {
        return combo == 0 ? "No combo" : combo + " Combo";
    }

    @Override
    public int getHeight() {
        return Fonts.height() + 6;
    }

    @Override
    protected void renderElement(GuiGraphicsExtractor context, float tickDelta, boolean preview) {
        drawBackground(context, getWidth(), getHeight());
        Fonts.draw(context, text(), 4, 4, combo >= 3 ? Theme.accentLight() : textColor.argb(), shadow());
    }
}
