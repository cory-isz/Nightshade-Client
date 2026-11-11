package com.nightshade.module.hud;

import com.nightshade.module.setting.BooleanSetting;

/** Which in-game day it is, the in-game time and the weather. */
public final class DayCounterHud extends SimpleTextHud {
    private final BooleanSetting time = add(new BooleanSetting("Show Time", "Show the in-game time of day", true));
    private final BooleanSetting weather = add(new BooleanSetting("Show Weather", "Show rain or thunder", true));

    public DayCounterHud() {
        super("Day Counter", "Shows the in-game day, time and weather", "Day ", 0.0, 0.50);
    }

    @Override
    protected void refresh() {
        if (mc.level == null) {
            return;
        }
        long dayTime = mc.level.getOverworldClockTime();
        StringBuilder out = new StringBuilder(Long.toString(dayTime / 24000L + 1));
        if (time.get()) {
            long t = (dayTime + 6000L) % 24000L; // tick 0 is 06:00
            out.append(String.format("  %02d:%02d", t / 1000L, (t % 1000L) * 60L / 1000L));
        }
        if (weather.get() && mc.level.isRaining()) {
            out.append(mc.level.isThundering() ? "  Thunder" : "  Rain");
        }
        value = out.toString();
    }
}
