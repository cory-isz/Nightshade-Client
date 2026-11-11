package com.nightshade.module.hud;

import com.nightshade.NightshadeClient;
import com.nightshade.event.events.WorldRenderEvent;
import com.nightshade.module.HudModule;
import com.nightshade.module.ModuleCategory;
import com.nightshade.module.setting.BooleanSetting;
import com.nightshade.module.setting.NumberSetting;
import com.nightshade.render.ColorUtil;
import com.nightshade.render.Fonts;
import com.nightshade.render.Theme;
import com.nightshade.render.WorldRenderUtil;
import com.nightshade.waypoint.Waypoint;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;

/** Waypoint list on the HUD (name, distance, direction) plus beams in the world. */
public final class WaypointsHud extends HudModule {
    private static final String[] ARROWS = {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};
    private final BooleanSetting beams = add(new BooleanSetting("Beams", "Draw a beam at each waypoint through walls", true));
    private final NumberSetting maxDistance = add(new NumberSetting("Max Distance", "Hide waypoints further than this", 2000, 100, 10000, 100, " blocks"));
    private final NumberSetting maxRows = add(new NumberSetting("Max Listed", "Maximum waypoints listed on the HUD", 5, 1, 15, 1));

    private final List<String> lines = new ArrayList<>();
    private final List<Integer> colors = new ArrayList<>();

    public WaypointsHud() {
        super("Waypoints", "Lists waypoints with distance and direction and marks them in the world", ModuleCategory.UTILITY, 0.0, 0.6);
        listen(WorldRenderEvent.class, event -> {
            if (!beams.get() || mc.player == null) {
                return;
            }
            List<Waypoint> list = NightshadeClient.client().waypoints().inDimension();
            if (list.isEmpty()) {
                return;
            }
            WorldRenderUtil.LineBatch batch = WorldRenderUtil.begin(event.context(), 2f);
            double maxSq = maxDistance.get() * maxDistance.get();
            for (Waypoint w : list) {
                if (!w.visible() || mc.player.distanceToSqr(w.x() + 0.5, w.y(), w.z() + 0.5) > maxSq) {
                    continue;
                }
                double cx = w.x() + 0.5;
                double cz = w.z() + 0.5;
                batch.line(cx, w.y(), cz, cx, w.y() + 256, cz, ColorUtil.withAlpha(w.color(), 0xC0));
                batch.box(new AABB(w.x(), w.y(), w.z(), w.x() + 1, w.y() + 1, w.z() + 1), w.color());
            }
            batch.draw(2f);
        });
    }

    @Override
    protected void refresh() {
        lines.clear();
        colors.clear();
        if (mc.player == null) {
            return;
        }
        List<Waypoint> list = new ArrayList<>(NightshadeClient.client().waypoints().inDimension());
        list.removeIf(w -> !w.visible());
        list.sort((a, b) -> Double.compare(distance(a), distance(b)));
        for (Waypoint w : list) {
            double d = distance(w);
            if (d > maxDistance.get() || lines.size() >= maxRows.intValue()) {
                continue;
            }
            double angle = Math.toDegrees(Math.atan2(w.z() + 0.5 - mc.player.getZ(), w.x() + 0.5 - mc.player.getX())) - 90.0;
            float relative = Mth.wrapDegrees((float) angle - mc.player.getYRot());
            int index = Math.floorMod(Math.round(relative / 45f), 8);
            lines.add(ARROWS[index] + " " + w.name() + "  " + String.format(Locale.ROOT, "%.0fm", d));
            colors.add(w.color());
        }
    }

    private double distance(Waypoint w) {
        return Math.sqrt(mc.player.distanceToSqr(w.x() + 0.5, w.y(), w.z() + 0.5));
    }

    @Override
    public int getWidth() {
        int max = Fonts.width("Waypoints");
        for (String line : lines) {
            max = Math.max(max, Fonts.width(line));
        }
        return max + 8;
    }

    @Override
    public int getHeight() {
        return (Math.max(1, lines.size()) + 1) * (Fonts.height() + 2) + 4;
    }

    @Override
    protected void renderElement(GuiGraphicsExtractor context, float tickDelta, boolean preview) {
        drawBackground(context, getWidth(), getHeight());
        Fonts.draw(context, "Waypoints", 4, 4, Theme.accentLight(), shadow());
        int y = 4 + Fonts.height() + 2;
        if (lines.isEmpty()) {
            Fonts.draw(context, "None here", 4, y, Theme.TEXT_DIM, shadow());
        }
        for (int i = 0; i < lines.size(); i++) {
            Fonts.draw(context, lines.get(i), 4, y, colors.get(i), shadow());
            y += Fonts.height() + 2;
        }
    }
}
