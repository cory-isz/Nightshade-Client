package com.nightshade.module.performance;

import com.nightshade.module.Module;
import com.nightshade.module.ModuleCategory;
import com.nightshade.module.setting.BooleanSetting;
import com.nightshade.module.setting.NumberSetting;
import com.nightshade.util.EffectProbe;
import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

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
        BlockPos camPos = BlockPos.containing(camX, camY, camZ);
        if (opaque(level, camPos.getX(), camPos.getY(), camPos.getZ())) {
            return true; // camera inside a block (e.g. third person clipping): don't guess
        }
        AABB box = entity.getBoundingBox().inflate(0.1);
        double[] xs = {box.minX, (box.minX + box.maxX) / 2, box.maxX};
        double[] ys = {box.minY, (box.minY + box.maxY) / 2, box.maxY};
        double[] zs = {box.minZ, (box.minZ + box.maxZ) / 2, box.maxZ};
        // centre first (most likely visible), then the 8 corners
        if (rayClear(level, camX, camY, camZ, xs[1], ys[1], zs[1])) {
            return true;
        }
        for (int i = 0; i < 8; i++) {
            if (rayClear(level, camX, camY, camZ, xs[(i & 1) * 2], ys[((i >> 1) & 1) * 2], zs[((i >> 2) & 1) * 2])) {
                return true;
            }
        }
        // the top centre too: tall entities peeking over a wall
        return rayClear(level, camX, camY, camZ, xs[1], ys[2], zs[1]);
    }

    private static boolean opaque(Level level, int x, int y, int z) {
        return level.getBlockState(new BlockPos(x, y, z)).isSolidRender();
    }

    /** Walks the blocks on the line from a to b (Amanatides-Woo) and returns false at the first opaque one. */
    private boolean rayClear(Level level, double ax, double ay, double az, double bx, double by, double bz) {
        raysThisTick++;
        int x = Mth.floor(ax), y = Mth.floor(ay), z = Mth.floor(az);
        int ex = Mth.floor(bx), ey = Mth.floor(by), ez = Mth.floor(bz);
        double dx = bx - ax, dy = by - ay, dz = bz - az;
        int sx = dx > 0 ? 1 : dx < 0 ? -1 : 0;
        int sy = dy > 0 ? 1 : dy < 0 ? -1 : 0;
        int sz = dz > 0 ? 1 : dz < 0 ? -1 : 0;
        double tdx = sx == 0 ? Double.MAX_VALUE : Math.abs(1.0 / dx);
        double tdy = sy == 0 ? Double.MAX_VALUE : Math.abs(1.0 / dy);
        double tdz = sz == 0 ? Double.MAX_VALUE : Math.abs(1.0 / dz);
        double tx = sx == 0 ? Double.MAX_VALUE : (sx > 0 ? (x + 1 - ax) : (ax - x)) * tdx;
        double ty = sy == 0 ? Double.MAX_VALUE : (sy > 0 ? (y + 1 - ay) : (ay - y)) * tdy;
        double tz = sz == 0 ? Double.MAX_VALUE : (sz > 0 ? (z + 1 - az) : (az - z)) * tdz;
        int steps = Math.abs(ex - x) + Math.abs(ey - y) + Math.abs(ez - z);
        for (int i = 0; i < steps; i++) {
            if (tx < ty && tx < tz) {
                x += sx;
                tx += tdx;
            } else if (ty < tz) {
                y += sy;
                ty += tdy;
            } else {
                z += sz;
                tz += tdz;
            }
            if (x == ex && y == ey && z == ez) {
                return true; // reached the entity's own block
            }
            if (opaque(level, x, y, z)) {
                return false;
            }
        }
        return true;
    }
}
