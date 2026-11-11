package com.nightshade.module.hud;

import com.nightshade.event.events.TickEvent;
import com.nightshade.module.HudModule;
import com.nightshade.module.ModuleCategory;
import com.nightshade.module.setting.BooleanSetting;
import com.nightshade.module.setting.ModeSetting;
import com.nightshade.render.Fonts;
import com.nightshade.render.Theme;
import net.minecraft.client.gui.GuiGraphics;

import java.util.Locale;

/** Current movement speed, averaged over the last half second. */
public final class SpeedHud extends HudModule {
    private final ModeSetting unit = add(new ModeSetting("Unit", "Blocks per second or kilometres per hour", "Blocks/s", "Blocks/s", "km/h"));
    private final BooleanSetting vertical = add(new BooleanSetting("Include Vertical", "Count falling and jumping too", false));
    private final double[] samples = new double[10];
    private int index;
    private double lastX = Double.NaN;
    private double lastY;
    private double lastZ;
    private double speed;
    private String text = "Speed";

    public SpeedHud() {
        super("Speed", "Shows how fast you are moving", ModuleCategory.HUD, 0.0, 0.3);
        listen(TickEvent.Post.class, event -> tick());
    }

    private void tick() {
        if (mc.player == null) {
            lastX = Double.NaN;
            return;
        }
        double x = mc.player.getX();
        double y = mc.player.getY();
        double z = mc.player.getZ();
        if (!Double.isNaN(lastX)) {
            double dx = x - lastX;
            double dy = vertical.get() ? y - lastY : 0;
            double dz = z - lastZ;
            double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
            samples[index++ % samples.length] = d > 20 ? 0 : d; // ignore teleports
        }
        lastX = x;
        lastY = y;
        lastZ = z;
        double sum = 0;
        for (double s : samples) {
            sum += s;
        }
        speed = sum / samples.length * 20.0;
    }

    @Override
    protected void onDisable() {
        lastX = Double.NaN;
    }

    @Override
    protected void refresh() {
        boolean kmh = unit.get().equals("km/h");
        text = kmh ? String.format(Locale.ROOT, "%.1f km/h", speed * 3.6) : String.format(Locale.ROOT, "%.2f b/s", speed);
    }

    @Override
    public int getWidth() {
        return Fonts.width("Speed ") + Fonts.width(text) + 8;
    }

    @Override
    public int getHeight() {
        return Fonts.height() + 6;
    }

    @Override
    protected void renderElement(GuiGraphics context, float tickDelta, boolean preview) {
        drawBackground(context, getWidth(), getHeight());
        int x = Fonts.draw(context, "Speed ", 4, 4, Theme.accentLight(), shadow());
        Fonts.draw(context, text, x, 4, textColor.argb(), shadow());
    }

    /** Test hook. */
    public double speedForTest() {
        return speed;
    }
}
