package com.nightshade.module.performance;

import com.nightshade.module.Module;
import com.nightshade.module.ModuleCategory;
import com.nightshade.module.setting.BooleanSetting;
import com.nightshade.module.setting.NumberSetting;
import com.nightshade.util.EffectProbe;
import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * Skips drawing entities that are completely hidden behind solid blocks. Visibility is tested with a few rays from
 * the camera to the entity's bounding box, walking the blocks in between and stopping at the first full opaque block.
 * Results are cached for a short time per entity so the cost stays low, and only rendering is skipped: hidden
 * entities still tick, move and make sounds as normal.
 */
public final class EntityCulling extends Module {
    private static volatile EntityCulling active;

    private final NumberSetting range = add(new NumberSetting("Max Distance", "Entities further away than this are left to Minecraft", 64, 16, 128, 8, " blocks"));
    private final NumberSetting recheck = add(new NumberSetting("Recheck Every", "How often each entity's visibility is tested again", 3, 1, 10, 1, " ticks"));
    private final BooleanSetting players = add(new BooleanSetting("Cull Players", "Also hide players behind walls (their name tags disappear too)", false));

    /** entity id -> (game time of the test << 1) | visible */
    private final Int2LongOpenHashMap cache = new Int2LongOpenHashMap();
    private long frameTime = -1;
    private int raysThisTick;
    private static final int RAY_BUDGET = 3000;
    private static long culled;

    public EntityCulling() {
        super("Entity Culling", "Skips drawing entities hidden behind walls", ModuleCategory.PERFORMANCE);
        enabledByDefault(); // invisible by design: only things you cannot see are skipped
    }

    @Override
    protected void onEnable() {
        cache.clear();
        active = this;
    }

    @Override
    protected void onDisable() {
        active = null;
        cache.clear();
    }

    public static long culledTotal() {
        return culled;
    }

    /** Hook from EntityRenderDispatcherMixin#shouldRender: true when the entity should not be drawn. */
    public static boolean shouldCull(Entity entity, double camX, double camY, double camZ) {
        EntityCulling module = active;
        if (module == null || mc.level == null || entity == mc.getCameraEntity() || entity == mc.player) {
            return false;
        }
        if (entity instanceof Player && !module.players.get() || entity.isCurrentlyGlowing() || entity.hasPassenger(mc.player)
                || mc.player != null && (mc.player.isSpectator() || entity.getVehicle() == mc.player)) {
            return false;
        }
        double max = module.range.get();
        if (entity.distanceToSqr(camX, camY, camZ) > max * max) {
            return false;
        }
        long now = mc.level.getGameTime();
        if (now != module.frameTime) {
            module.frameTime = now;
            module.raysThisTick = 0;
            if (module.cache.size() > 4096) {
                module.cache.clear();
            }
        }
        long cached = module.cache.getOrDefault(entity.getId(), Long.MIN_VALUE);
        boolean visible;
        if (cached != Long.MIN_VALUE && now - (cached >> 1) < module.recheck.intValue()) {
            visible = (cached & 1L) != 0;
        } else if (module.raysThisTick > RAY_BUDGET) {
            return false; // out of budget this tick: draw it, test it next tick
        } else {
            visible = module.isVisible(mc.level, entity, camX, camY, camZ);
            module.cache.put(entity.getId(), (now << 1) | (visible ? 1L : 0L));
        }
        if (!visible) {
            culled++;
            EffectProbe.hit("Entity Culling");
        }
        return !visible;
    }

    private boolean isVisible(Level level, Entity entity, double camX, double camY, double camZ) {
        raysThisTick += 10;
        return com.nightshade.util.Occlusion.isVisible(level, entity.getBoundingBox().inflate(0.1), camX, camY, camZ);
    }
}
