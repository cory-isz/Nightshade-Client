package com.nightshade.cosmetic;

import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Emotes: short animations for your own player model, picked from the emote wheel. Only you see them (nothing is
 * sent to the server). Moving or attacking stops an emote; Sit lasts until you move.
 */
public final class Emotes {
    public enum Emote {
        WAVE("Wave", 2.6f), CLAP("Clap", 2.8f), CHEER("Cheer", 2.8f), DAB("Dab", 2.0f),
        FACEPALM("Facepalm", 2.6f), SALUTE("Salute", 2.4f), SHRUG("Shrug", 2.2f), SIT("Sit", 0f);

        public final String label;
        /** Seconds it plays for; 0 = until you move. */
        public final float seconds;

        Emote(String label, float seconds) {
            this.label = label;
            this.seconds = seconds;
        }
    }

    private static final float BLEND = 0.25f; // seconds to ease in and out

    private static Emote current;
    private static long startedAt;
    private static long endingAt;
    private static Vec3 startPos;
    private static boolean switchedCamera;
    private static CameraType cameraBefore;
    /** When true, playing an emote shows you from the front (and goes back afterwards). */
    public static boolean thirdPerson = true;
    /** Test counter: frames an emote pose was applied to the model. */
    public static long posedFrames;

    private Emotes() {
    }

    public static Emote current() {
        return current;
    }

    public static boolean playing() {
        return current != null;
    }

    public static void play(Emote e) {
        Minecraft mc = Minecraft.getInstance();
        Player p = mc.player;
        if (p == null) {
            return;
        }
        current = e;
        startedAt = System.currentTimeMillis();
        endingAt = 0;
        startPos = p.position();
        if (thirdPerson && mc.options.getCameraType() == CameraType.FIRST_PERSON) {
            cameraBefore = CameraType.FIRST_PERSON;
            mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
            switchedCamera = true;
        }
    }

    public static void stop() {
        if (current != null && endingAt == 0) {
            endingAt = System.currentTimeMillis(); // ease out, then finish in tick()
        }
    }

    /** Once per client tick: ends emotes on time, on movement or on attack, and puts the camera back. */
    public static void tick() {
        if (current == null) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        Player p = mc.player;
        long now = System.currentTimeMillis();
        if (p == null || p.isDeadOrDying()) {
            finish(mc);
            return;
        }
        boolean moved = startPos != null && p.position().distanceToSqr(startPos) > 0.04;
        boolean timeUp = current.seconds > 0 && now - startedAt > current.seconds * 1000;
        if (endingAt == 0 && (moved || timeUp || p.isSwinging())) {
            endingAt = now;
        }
        if (endingAt != 0 && now - endingAt > BLEND * 1000) {
            finish(mc);
        }
    }

    private static void finish(Minecraft mc) {
        current = null;
        endingAt = 0;
        if (switchedCamera && mc.options != null && mc.options.getCameraType() == CameraType.THIRD_PERSON_FRONT) {
            mc.options.setCameraType(cameraBefore); // only if you didn't change the view yourself
        }
        switchedCamera = false;
    }

    /** How much of the emote pose to use right now (eases in and out). */
    private static float weight(long now) {
        float in = Math.min(1f, (now - startedAt) / (BLEND * 1000f));
        float out = endingAt == 0 ? 1f : Math.max(0f, 1f - (now - endingAt) / (BLEND * 1000f));
        float w = Math.min(in, out);
        return w * w * (3f - 2f * w);
    }

    /** Poses the model for the current emote. Called after vanilla's own animation, for your player only. */
    public static void apply(ModelPart head, ModelPart body, ModelPart rightArm, ModelPart leftArm, ModelPart rightLeg, ModelPart leftLeg) {
        Emote e = current;
        if (e == null) {
            return;
        }
        long now = System.currentTimeMillis();
        float w = weight(now);
        float t = (now - startedAt) / 50f; // ticks since the start
        posedFrames++;
        switch (e) {
            case WAVE -> {
                pose(rightArm, w, -2.7f, 0f, 0.25f + (float) Math.sin(t * 0.55f) * 0.4f);
                pose(head, w, -0.1f, 0.25f, 0.05f);
            }
            case CLAP -> {
                float c = ((float) Math.sin(t * 0.9f) + 1f) / 2f;
                pose(rightArm, w, -1.35f, -0.15f - 0.45f * c, 0f);
                pose(leftArm, w, -1.35f, 0.15f + 0.45f * c, 0f);
            }
            case CHEER -> {
                float b = (float) Math.sin(t * 0.5f) * 0.12f;
                pose(rightArm, w, -2.95f, 0f, 0.35f + b);
                pose(leftArm, w, -2.95f, 0f, -0.35f - b);
                pose(head, w, -0.3f, 0f, 0f);
            }
            case DAB -> {
                pose(head, w, 0.55f, -0.55f, 0f);
                pose(rightArm, w, -1.75f, -0.85f, 0f);
                pose(leftArm, w, -2.3f, 0f, -1.1f);
            }
            case FACEPALM -> {
                pose(rightArm, w, -2.25f, -0.55f, 0.1f);
                pose(head, w, 0.45f, 0f, (float) Math.sin(t * 0.25f) * 0.08f);
            }
            case SALUTE -> {
                pose(rightArm, w, -2.35f, -0.95f, 0.45f);
                pose(head, w, -0.05f, 0f, 0f);
            }
            case SHRUG -> {
                float s = Math.min(1f, t / 8f);
                pose(rightArm, w, -0.45f, 0.2f, 0.55f * s);
                pose(leftArm, w, -0.45f, -0.2f, -0.55f * s);
                pose(head, w, 0f, 0f, 0.22f * s);
            }
            case SIT -> {
                float drop = 10f * w;
                for (ModelPart part : new ModelPart[]{head, body, rightArm, leftArm, rightLeg, leftLeg}) {
                    part.y += drop; // sit down on the ground
                }
                pose(rightLeg, w, -1.45f, 0.18f, 0.05f);
                pose(leftLeg, w, -1.45f, -0.18f, -0.05f);
                pose(rightArm, w, -0.55f, 0f, 0.15f);
                pose(leftArm, w, -0.55f, 0f, -0.15f);
            }
        }
    }

    private static void pose(ModelPart part, float w, float x, float y, float z) {
        part.xRot += (x - part.xRot) * w;
        part.yRot += (y - part.yRot) * w;
        part.zRot += (z - part.zRot) * w;
    }
}
