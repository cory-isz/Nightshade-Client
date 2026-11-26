package com.nightshade.module.visual;

import com.nightshade.module.Module;
import com.nightshade.module.ModuleCategory;
import com.nightshade.module.setting.BooleanSetting;
import com.nightshade.module.setting.NumberSetting;
import com.nightshade.util.EffectProbe;
import net.minecraft.world.phys.AABB;

/**
 * Dropped items lie flat on the ground (blocks rest on a face) instead of floating and spinning, and tumble while
 * they fall. Purely visual and client-side (see ItemEntityRendererMixin).
 */
public final class ItemPhysics extends Module {
    private static volatile ItemPhysics active;
    private final BooleanSetting tumble = add(new BooleanSetting("Tumble In Air", "Items spin end over end while falling", true));
    private final NumberSetting speed = add(new NumberSetting("Tumble Speed", "How fast falling items spin", 1.0, 0.2, 3.0, 0.1, "x")).visibleWhen(tumble::get);

    public ItemPhysics() {
        super("Item Physics", "Dropped items lie flat on the ground instead of floating and spinning", ModuleCategory.VISUAL);
    }

    @Override
    protected void onEnable() {
        active = this;
    }

    @Override
    protected void onDisable() {
        active = null;
    }

    public static boolean active() {
        return active != null;
    }

    /** Flat (sprite) item models are thin along Z; those are laid down, block models stay upright. */
    public static boolean isFlat(AABB box) {
        return box.maxZ - box.minZ < 0.2 && box.maxY - box.minY > 0.2;
    }

    public static boolean tumble() {
        ItemPhysics m = active;
        return m != null && m.tumble.get();
    }

    public static float tumbleSpeed() {
        ItemPhysics m = active;
        return m == null ? 1f : m.speed.floatValue();
    }

    public static void applied() {
        EffectProbe.hit("Item Physics");
    }
}
