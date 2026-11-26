package com.nightshade.module.pvp;

import com.nightshade.util.EffectProbe;
import com.nightshade.event.events.AttackEntityEvent;
import com.nightshade.event.events.HitLandedEvent;
import com.nightshade.event.events.TickEvent;
import com.nightshade.module.HudModule;
import com.nightshade.module.ModuleCategory;
import com.nightshade.module.setting.BooleanSetting;
import com.nightshade.module.setting.NumberSetting;
import com.nightshade.render.Fonts;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Shows how far away your last hit landed, measured like vanilla does: from your eyes to the nearest point of the
 * target's hitbox. With "Confirmed Hits Only" a hit counts once the server confirms you damaged the target.
 */
public final class ReachDisplay extends HudModule {
    private final NumberSetting decimals = add(new NumberSetting("Decimals", "Digits after the decimal point", 2, 0, 3, 1));
    private final NumberSetting hideAfter = add(new NumberSetting("Hide After", "Seconds before the value fades (0 = keep)", 3, 0, 15, 0.5, "s"));
    private final BooleanSetting confirmedOnly = add(new BooleanSetting("Confirmed Hits Only", "Only count hits that actually damaged the target", true));

    private double reach = -1;
    private long shownAt;
    private LivingEntity pending;
    private double pendingReach;
    private int pendingTicks;

    public ReachDisplay() {
        super("Reach Display", "Shows the distance of your last hit", ModuleCategory.PVP, 0.5, 0.68);
        listen(AttackEntityEvent.class, event -> onAttack(event.target()));
        listen(HitLandedEvent.class, event -> {
            if (pending != null && event.target() == pending) {
                show(pendingReach);
                pending = null;
            }
        });
        listen(TickEvent.Post.class, event -> tick());
    }

    /** Distance from the eyes to the closest point of the target's bounding box. */
    public static double reachTo(Vec3 eye, AABB box) {
        double x = Mth.clamp(eye.x, box.minX, box.maxX);
        double y = Mth.clamp(eye.y, box.minY, box.maxY);
        double z = Mth.clamp(eye.z, box.minZ, box.maxZ);
        return eye.distanceTo(new Vec3(x, y, z));
    }

    private void onAttack(Entity target) {
        if (mc.player == null) {
            return;
        }
        double distance = reachTo(mc.player.getEyePosition(), target.getBoundingBox());
        if (confirmedOnly.get() && target instanceof LivingEntity living) {
            pending = living;
            pendingReach = distance;
            pendingTicks = 20; // wait up to a second for the server's damage confirmation
        } else {
            show(distance);
        }
    }

    private void tick() {
        if (pending != null && (--pendingTicks <= 0 || pending.isRemoved())) {
            pending = null; // missed, blocked or out of range
        }
    }

    private void show(double distance) {
        reach = distance;
        shownAt = System.currentTimeMillis();
        EffectProbe.hit("Reach Display");
    }

    /** Last measured reach, or -1 if none (exposed for tests). */
    public double lastReach() {
        return reach;
    }

    private String text() {
        boolean expired = hideAfter.get() > 0 && System.currentTimeMillis() - shownAt > hideAfter.get() * 1000.0;
        if (reach < 0 || expired) {
            return "Reach: -";
        }
        return String.format(Locale.ROOT, "Reach: %." + decimals.get().intValue() + "f", reach);
    }

    @Override
    protected void onDisable() {
        pending = null;
        reach = -1;
    }

    @Override
    public int getWidth() {
        return Math.max(Fonts.width("Reach: 0.000"), Fonts.width(text())) + 8;
    }

    @Override
    public int getHeight() {
        return Fonts.height() + 6;
    }

    @Override
    protected void renderElement(GuiGraphics context, float tickDelta, boolean preview) {
        drawBackground(context, getWidth(), getHeight());
        String text = preview && reach < 0 ? "Reach: 2.87" : text();
        Fonts.draw(context, text, 4, 4, textColor.argb(), textShadow.get());
    }
}
