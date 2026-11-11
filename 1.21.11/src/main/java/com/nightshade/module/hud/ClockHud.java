package com.nightshade.module.hud;

import com.nightshade.module.HudModule;
import com.nightshade.module.ModuleCategory;
import com.nightshade.module.setting.BooleanSetting;
import com.nightshade.module.setting.ModeSetting;
import com.nightshade.render.Fonts;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;

/** Real-world clock, optionally with the in-game time of day. */
public final class ClockHud extends HudModule {
    private final ModeSetting format = add(new ModeSetting("Format", "Clock format", "24h", "24h", "12h"));
    private final BooleanSetting seconds = add(new BooleanSetting("Seconds", "Show seconds", false));
    private final BooleanSetting date = add(new BooleanSetting("Date", "Show the date", false));
    private final BooleanSetting gameTime = add(new BooleanSetting("Game Time", "Also show the in-game time of day", false));
    private String text = "";

    public ClockHud() {
        super("Clock", "Shows the real time (and optionally the in-game time)", ModuleCategory.HUD, 0.5, 0.075);
    }

    @Override
    protected void refresh() {
        String pattern = (date.get() ? "EEE d MMM  " : "") + (format.is("24h") ? "HH:mm" : "h:mm") + (seconds.get() ? ":ss" : "") + (format.is("12h") ? " a" : "");
        String value = LocalDateTime.now().format(DateTimeFormatter.ofPattern(pattern, Locale.ROOT));
        if (gameTime.get() && mc.level != null) {
            long ticks = (mc.level.getDayTime() + 6000L) % 24000L; // tick 0 is 06:00
            value += String.format(Locale.ROOT, "  |  Day %02d:%02d", ticks / 1000L, (ticks % 1000L) * 60L / 1000L);
        }
        text = value;
    }

    @Override
    public int getWidth() {
        return Fonts.width(text) + 8;
    }

    @Override
    public int getHeight() {
        return Fonts.height() + 6;
    }

    @Override
    protected void renderElement(GuiGraphics context, float tickDelta, boolean preview) {
        drawBackground(context, getWidth(), getHeight());
        Fonts.draw(context, text, 4, 4, textColor.argb(), shadow());
    }
}
