package com.nightshade.module.performance;

import com.nightshade.module.Module;
import com.nightshade.module.ModuleCategory;
import com.nightshade.module.setting.BooleanSetting;
import com.nightshade.module.setting.NumberSetting;
import com.nightshade.util.EffectProbe;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/**
 * Skips name tags of far-away entities. Every visible name tag is text drawn in the world each frame; hub and lobby
 * servers are full of named armour stands (holograms) and named mobs, so culling distant ones saves real frame time.
 */
public final class NameTagCulling extends Module {
    private static volatile NameTagCulling active;
    private final NumberSetting distance = add(new NumberSetting("Distance", "Name tags further away than this are not drawn", 24, 8, 64, 2, " blocks"));
    private final BooleanSetting keepPlayers = add(new BooleanSetting("Keep Players", "Always show players' names (only mobs, holograms and items are culled)", true));

    public NameTagCulling() {
        super("Name Tag Culling", "Skips name tags of distant mobs and holograms for more FPS", ModuleCategory.PERFORMANCE);
        enabledByDefault();
    }

    @Override
    protected void onEnable() {
        active = this;
    }

    @Override
    protected void onDisable() {
        active = null;
    }

    /** Hook from EntityRendererNameTagMixin: whether this entity's name tag should be skipped this frame. */
    public static boolean cull(Entity entity) {
        NameTagCulling m = active;
        Minecraft mc = Minecraft.getInstance();
        if (m == null || mc.player == null || entity == mc.player || (m.keepPlayers.get() && entity instanceof Player)) {
            return false;
        }
        double d = m.distance.get();
        if (entity.distanceToSqr(mc.player) > d * d) {
            EffectProbe.hit("Name Tag Culling");
            return true;
        }
        return false;
    }
}
