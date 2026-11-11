package com.nightshade.module.performance;

import com.nightshade.util.EffectProbe;
import com.nightshade.module.Module;
import com.nightshade.module.ModuleCategory;
import com.nightshade.module.setting.BooleanSetting;
import com.nightshade.module.setting.NumberSetting;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.ItemEntity;

/**
 * Skips rendering of distant non-essential entities: dropped items, experience orbs and unnamed armour stands.
 * Players, mobs, projectiles and vehicles are never affected, and only rendering is skipped (the entities still
 * exist, tick and collide normally).
 */
public final class EntityRenderLimiter extends Module {
    private static volatile EntityRenderLimiter active;
    private static final AtomicLong CULLED = new AtomicLong();

    private final BooleanSetting items = add(new BooleanSetting("Limit Items", "Limit dropped item render distance", true));
    private final NumberSetting itemDistance = add(new NumberSetting("Item Distance", "Max render distance for dropped items", 32, 8, 128, 4, " blocks"))
            .visibleWhen(items::get);
    private final BooleanSetting orbs = add(new BooleanSetting("Limit XP Orbs", "Limit experience orb render distance", true));
    private final NumberSetting orbDistance = add(new NumberSetting("XP Orb Distance", "Max render distance for XP orbs", 24, 8, 128, 4, " blocks"))
            .visibleWhen(orbs::get);
    private final BooleanSetting armorStands = add(new BooleanSetting("Limit Armor Stands", "Limit render distance of unnamed armour stands", true));
    private final NumberSetting armorStandDistance = add(new NumberSetting("Armor Stand Distance", "Max render distance for unnamed armour stands", 48, 8, 128, 4, " blocks"))
            .visibleWhen(armorStands::get);

    public EntityRenderLimiter() {
        super("Entity Render Limiter", "Skips rendering distant non-essential entities", ModuleCategory.PERFORMANCE);
    }

    @Override
    protected void onEnable() {
        active = this;
    }

    @Override
    protected void onDisable() {
        active = null;
    }

    /** Hook from EntityRenderDispatcherMixin. Camera position is passed by the caller. */
    public static boolean shouldCull(Entity entity, double cameraX, double cameraY, double cameraZ) {
        EntityRenderLimiter module = active;
        if (module == null) {
            return false;
        }
        double limit;
        if (entity instanceof ItemEntity) {
            if (!module.items.get()) {
                return false;
            }
            limit = module.itemDistance.get();
        } else if (entity instanceof ExperienceOrb) {
            if (!module.orbs.get()) {
                return false;
            }
            limit = module.orbDistance.get();
        } else if (entity instanceof ArmorStand stand) {
            if (!module.armorStands.get() || stand.hasCustomName() || stand.isMarker()) {
                return false;
            }
            limit = module.armorStandDistance.get();
        } else {
            return false;
        }
        boolean cull = entity.distanceToSqr(cameraX, cameraY, cameraZ) > limit * limit;
        if (cull) {
            EffectProbe.hit("Entity Render Limiter");
            CULLED.incrementAndGet();
        }
        return cull;
    }

    public static long culledTotal() {
        return CULLED.get();
    }
}
