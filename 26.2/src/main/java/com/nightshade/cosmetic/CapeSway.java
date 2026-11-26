package com.nightshade.cosmetic;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Cape physics for your own cape: the cape is a damped spring that swings out with your speed, lifts when you fall,
 * flutters while you sprint and sways sideways when you turn or strafe, then settles back when you stop.
 * The same turn motion makes your pet trail behind a little.
 */
public final class CapeSway {
    private static final float STIFFNESS = 55f;
    private static final float DAMPING = 2f * (float) Math.sqrt(STIFFNESS) * 0.55f; // a little bouncy

    private static float lift = 6f;
    private static float liftVel;
    private static float side;
    private static float sideVel;
    private static float trail;
    private static float trailVel;
    private static float lastYaw = Float.NaN;
    private static long lastNanos;

    private CapeSway() {
    }

    /** Steps the springs to now. Call once per frame before reading the angles. */
    public static void update(float ageTicks) {
        long now = System.nanoTime();
        float dt = lastNanos == 0 ? 0f : Math.min(0.05f, (now - lastNanos) / 1e9f);
        lastNanos = now;
        Player p = Minecraft.getInstance().player;
        if (p == null || dt <= 0f) {
            return;
        }
        Vec3 v = p.getDeltaMovement();
        double horizontal = Math.sqrt(v.x * v.x + v.z * v.z);
        float yaw = p.getYRot();
        float turn = Float.isNaN(lastYaw) ? 0f : wrap(yaw - lastYaw) / dt; // degrees per second
        lastYaw = yaw;
        // sideways part of the movement, relative to where you face (strafing)
        double rad = Math.toRadians(yaw);
        double strafe = v.x * Math.cos(rad) + v.z * Math.sin(rad);

        float liftTarget = 6f + (float) Math.min(70.0, horizontal * 230.0);
        if (!p.onGround() && v.y < -0.1) {
            liftTarget += (float) Math.min(35.0, -v.y * 45.0); // falling: the cape rises
        }
        if (p.isSprinting() && horizontal > 0.15) {
            liftTarget += (float) Math.sin(ageTicks * 0.9f) * 4f + (float) Math.sin(ageTicks * 1.7f) * 1.5f; // flutter
        }
        if (p.isCrouching()) {
            liftTarget += 20f;
        }
        float sideTarget = clamp(-turn * 0.04f + (float) strafe * -60f, -16f, 16f);
        float trailTarget = clamp(-turn * 0.06f, -25f, 25f);

        liftVel += (STIFFNESS * (liftTarget - lift) - DAMPING * liftVel) * dt;
        lift += liftVel * dt;
        sideVel += (STIFFNESS * (sideTarget - side) - DAMPING * sideVel) * dt;
        side += sideVel * dt;
        trailVel += (STIFFNESS * 0.6f * (trailTarget - trail) - DAMPING * 0.8f * trailVel) * dt;
        trail += trailVel * dt;
    }

    /** Degrees the cape swings back from the body. */
    public static float lift() {
        return clamp(lift, 0f, 100f);
    }

    /** Degrees the cape sways to the side. */
    public static float side() {
        return side;
    }

    /** Degrees your pet trails behind when you turn. */
    public static float trail() {
        return trail;
    }

    /** The gentle idle sway used for the Wardrobe model. */
    public static float previewLift(float ageTicks) {
        return 8f + (float) Math.sin(ageTicks * 0.07f) * 3f;
    }

    private static float wrap(float d) {
        d %= 360f;
        return d >= 180f ? d - 360f : d < -180f ? d + 360f : d;
    }

    private static float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
