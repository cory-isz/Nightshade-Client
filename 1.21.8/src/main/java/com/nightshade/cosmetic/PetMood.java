package com.nightshade.cosmetic;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * What your pet is up to: it flinches and spins when you get hit, does happy hops when you win a fight or a game,
 * and dozes off (sinking down, tilting and slowing) when you stand still for a while.
 */
public final class PetMood {
    /** Seconds without moving before the pet falls asleep. */
    public static final float SLEEP_AFTER = 30f;

    private static long hurtAt;
    private static long cheerAt;
    private static long lastMoveAt = System.currentTimeMillis();
    private static Vec3 lastPos;
    private static float sleep;
    private static long lastNanos;
    private static int lastHurtTime;
    private static float clock;

    private PetMood() {
    }

    /** You won something (a kill, or Auto GG fired at the end of a game). */
    public static void cheer() {
        cheerAt = System.currentTimeMillis();
    }

    /** Test hooks. */
    public static void hurtForTest() {
        hurtAt = System.currentTimeMillis();
    }

    public static void idleForTest(float seconds) {
        lastMoveAt = System.currentTimeMillis() - (long) (seconds * 1000);
        lastPos = null;
    }

    public static void update() {
        long now = System.currentTimeMillis();
        long nanos = System.nanoTime();
        float dt = lastNanos == 0 ? 0f : Math.min(0.1f, (nanos - lastNanos) / 1e9f);
        lastNanos = nanos;
        Player p = Minecraft.getInstance().player;
        if (p != null) {
            Vec3 pos = p.position();
            if (lastPos == null || pos.distanceToSqr(lastPos) > 0.0004 || p.swinging) {
                if (lastPos != null) {
                    lastMoveAt = now;
                }
                lastPos = pos;
            }
            if (p.hurtTime > lastHurtTime) {
                hurtAt = now;
            }
            lastHurtTime = p.hurtTime;
        }
        boolean asleep = (now - lastMoveAt) / 1000f > SLEEP_AFTER;
        sleep += ((asleep ? 1f : 0f) - sleep) * Math.min(1f, dt * (asleep ? 0.8f : 4f)); // nods off slowly, wakes fast
        clock += dt * 20f * (1f - 0.75f * sleep); // animation ticks: slow while dozing, normal when awake
    }

    /** The pet's animation clock in ticks (runs slower while it sleeps). */
    public static float clock() {
        return clock;
    }

    /** 0 = awake, 1 = fast asleep. */
    public static float sleep() {
        return sleep;
    }

    public static boolean asleep() {
        return sleep > 0.5f;
    }

    /** Extra spin in degrees right after you get hit (a quick full turn). */
    public static float hurtSpin() {
        float t = (System.currentTimeMillis() - hurtAt) / 600f;
        return t >= 0f && t < 1f ? 360f * ease(t) : 0f;
    }

    /** How far the pet jumps back when you get hit (blocks). */
    public static float hurtRecoil() {
        float t = (System.currentTimeMillis() - hurtAt) / 500f;
        return t >= 0f && t < 1f ? (float) Math.sin(t * Math.PI) * 0.18f : 0f;
    }

    /** Upward hop in blocks while cheering (three hops). */
    public static float cheerHop() {
        float t = (System.currentTimeMillis() - cheerAt) / 1500f;
        return t >= 0f && t < 1f ? (float) Math.abs(Math.sin(t * Math.PI * 3)) * 0.22f * (1f - t * 0.5f) : 0f;
    }

    public static boolean cheering() {
        return System.currentTimeMillis() - cheerAt < 1500;
    }

    private static float ease(float t) {
        return 1f - (1f - t) * (1f - t) * (1f - t);
    }
}
