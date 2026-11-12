package com.nightshade.module.pvp;

import com.nightshade.event.events.WorldRenderEvent;
import com.nightshade.module.Module;
import com.nightshade.module.ModuleCategory;
import com.nightshade.module.setting.ColorSetting;
import com.nightshade.render.ColorUtil;
import com.nightshade.render.WorldRenderUtil;
import com.nightshade.util.EffectProbe;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Shows where your arrow, trident, pearl, snowball, egg, potion or wind charge will land while you hold it. Only works
 * in singleplayer (and worlds you open to LAN), because many servers treat a trajectory preview as unfair.
 */
public final class Trajectories extends Module {
    private final ColorSetting color = add(new ColorSetting("Color", "Colour of the path", 0xFFB794FF));
    private final ColorSetting hitColor = add(new ColorSetting("Hit Color", "Colour of the landing box when it would hit a mob or player", 0xFFFF5C7A));

    private record Throw(double speed, double gravity, double drag, float pitchOffset) {
    }

    public Trajectories() {
        super("Trajectories", "Shows where your projectile will land (singleplayer only)", ModuleCategory.PVP);
        listen(WorldRenderEvent.class, event -> render(event));
    }

    /** True when the preview is allowed here: singleplayer or a world you host. */
    public static boolean allowedHere() {
        return mc.getSingleplayerServer() != null;
    }

    private Throw throwFor(Player p) {
        ItemStack main = p.getMainHandItem();
        ItemStack stack = p.isUsingItem() ? p.getUseItem() : main;
        if (stack.is(Items.BOW)) {
            if (!p.isUsingItem()) {
                return null;
            }
            float power = BowItem.getPowerForTime(p.getTicksUsingItem());
            return power < 0.1f ? null : new Throw(power * 3.0, 0.05, 0.99, 0f);
        }
        if (stack.is(Items.CROSSBOW)) {
            return CrossbowItem.isCharged(stack) ? new Throw(3.15, 0.05, 0.99, 0f) : null;
        }
        if (stack.is(Items.TRIDENT)) {
            return p.isUsingItem() && p.getTicksUsingItem() >= 10 ? new Throw(2.5, 0.05, 0.99, 0f) : null;
        }
        if (stack.is(Items.ENDER_PEARL) || stack.is(Items.SNOWBALL) || stack.is(Items.EGG)) {
            return new Throw(1.5, 0.03, 0.99, 0f);
        }
        if (stack.is(Items.SPLASH_POTION) || stack.is(Items.LINGERING_POTION)) {
            return new Throw(0.5, 0.05, 0.99, -20f);
        }
        if (stack.is(Items.EXPERIENCE_BOTTLE)) {
            return new Throw(0.7, 0.07, 0.99, -20f);
        }
        if (stack.is(Items.WIND_CHARGE)) {
            return new Throw(1.5, 0.0, 1.0, 0f);
        }
        return null;
    }

    private void render(WorldRenderEvent event) {
        Player p = mc.player;
        if (p == null || mc.level == null || !allowedHere()) {
            return;
        }
        Throw t = throwFor(p);
        if (t == null) {
            return;
        }
        float tick = 1f;
        float yaw = p.getYRot();
        float pitch = p.getXRot() + t.pitchOffset();
        double rx = Math.toRadians(pitch);
        double ry = Math.toRadians(yaw);
        Vec3 vel = new Vec3(-Math.sin(ry) * Math.cos(rx), -Math.sin(rx), Math.cos(ry) * Math.cos(rx)).scale(t.speed());
        Vec3 move = p.getDeltaMovement();
        vel = vel.add(move.x, p.onGround() ? 0.0 : move.y, move.z);
        Vec3 pos = p.getEyePosition(tick).add(0, -0.1, 0);
        WorldRenderUtil.LineBatch batch = WorldRenderUtil.begin(event.context(), 2f);
        int c = color.argb();
        boolean hitEntity = false;
        Vec3 end = null;
        for (int i = 0; i < 300; i++) {
            Vec3 next = pos.add(vel);
            BlockHitResult block = mc.level.clip(new ClipContext(pos, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
            if (block.getType() != HitResult.Type.MISS) {
                next = block.getLocation();
                end = next;
            }
            AABB sweep = new AABB(pos, next).inflate(0.3);
            for (Entity e : mc.level.getEntities(p, sweep, e -> e instanceof LivingEntity && e.isAlive() && !e.isSpectator())) {
                var hit = e.getBoundingBox().inflate(0.3).clip(pos, next);
                if (hit.isPresent()) {
                    next = hit.get();
                    end = next;
                    hitEntity = true;
                    break;
                }
            }
            // fade the far end of the path a little
            batch.line(pos.x, pos.y, pos.z, next.x, next.y, next.z, ColorUtil.withAlpha(c, Math.max(0x50, 0xFF - i * 2)));
            if (end != null || next.y < mc.level.getMinY() - 16) {
                break;
            }
            pos = next;
            vel = vel.scale(t.drag()).add(0, -t.gravity(), 0);
        }
        if (end != null) {
            double s = 0.15;
            batch.box(new AABB(end.x - s, end.y - s, end.z - s, end.x + s, end.y + s, end.z + s), hitEntity ? hitColor.argb() : c);
        }
        batch.draw(2f);
        EffectProbe.hit("Trajectories");
    }
}
