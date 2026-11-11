package com.nightshade.replay;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Plays a clip back in the current world: every recorded player becomes a client-only replica (nobody else sees
 * them), driven frame by frame, and the camera rides your recorded point of view. Pause, speed and seeking are
 * controlled from {@link com.nightshade.gui.menu.ReplayControlsScreen}.
 */
public final class ReplayPlayer {
    private static final EquipmentSlot[] ARMOR = {EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD};
    private static ReplayPlayer current;
    private static int nextId = -2_000_000;

    private final ReplayClip clip;
    private final Map<String, RemotePlayer> ghosts = new HashMap<>();
    private final double dx;
    private final double dy;
    private final double dz;
    private double position;
    private float speed = 1f;
    private boolean paused;

    private ReplayPlayer(ReplayClip clip, boolean here) {
        this.clip = clip;
        Minecraft mc = Minecraft.getInstance();
        ReplayClip.Pose start = selfPose(0);
        if (here && start != null && mc.player != null) {
            // Different server or dimension: replay the fight around where you stand now.
            dx = mc.player.getX() - start.x;
            dy = mc.player.getY() - start.y;
            dz = mc.player.getZ() - start.z;
        } else {
            dx = 0;
            dy = 0;
            dz = 0;
        }
    }

    public static boolean active() {
        return current != null;
    }

    public static ReplayPlayer current() {
        return current;
    }

    /** Starts playback; {@code here} moves the fight to your position (use when the place does not match). */
    public static void start(ReplayClip clip, boolean here) {
        stop();
        if (Minecraft.getInstance().level == null || clip.frames.isEmpty()) {
            return;
        }
        current = new ReplayPlayer(clip, here);
        current.apply(0, true);
    }

    public static void stop() {
        ReplayPlayer p = current;
        current = null;
        if (p == null) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            for (RemotePlayer ghost : p.ghosts.values()) {
                mc.level.removeEntity(ghost.getId(), Entity.RemovalReason.DISCARDED);
            }
        }
        p.ghosts.clear();
        if (mc.player != null) {
            mc.setCameraEntity(mc.player);
        }
    }

    /** Called every client tick. */
    public static void tickPlayback() {
        ReplayPlayer p = current;
        if (p == null) {
            return;
        }
        if (Minecraft.getInstance().level == null) {
            current = null;
            return;
        }
        if (!p.paused) {
            int before = (int) p.position;
            p.position = Math.min(p.clip.frames.size() - 1, p.position + p.speed);
            int after = (int) p.position;
            for (int f = before + 1; f <= after; f++) {
                p.apply(f, f == after);
            }
            if (after >= p.clip.frames.size() - 1) {
                p.paused = true;
            }
        }
    }

    private ReplayClip.Pose selfPose(int frame) {
        for (ReplayClip.Pose pose : clip.frames.get(frame).players) {
            if (pose.id.equals(clip.self)) {
                return pose;
            }
        }
        return null;
    }

    /** Moves every replica to frame {@code index}. {@code last} = this is the frame shown (camera follows). */
    private void apply(int index, boolean last) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        ReplayClip.Frame frame = clip.frames.get(index);
        java.util.Set<String> present = new java.util.HashSet<>();
        for (ReplayClip.Pose pose : frame.players) {
            present.add(pose.id);
            RemotePlayer ghost = ghosts.computeIfAbsent(pose.id, id -> spawn(id));
            double x = pose.x + dx;
            double y = pose.y + dy;
            double z = pose.z + dz;
            ghost.setOldPosAndRot();
            ghost.yHeadRotO = ghost.getYHeadRot();
            ghost.yBodyRotO = ghost.yBodyRot;
            ghost.snapTo(x, y, z, pose.yaw, pose.pitch);
            if (!last) {
                ghost.setOldPosAndRot();
            }
            ghost.setYHeadRot(pose.head);
            ghost.setYBodyRot(pose.body);
            ghost.setShiftKeyDown(pose.sneak);
            if (pose.swing) {
                ghost.swing(InteractionHand.MAIN_HAND);
            }
            if (pose.hurt > ghost.hurtTime) {
                ghost.hurtDuration = 10;
            }
            ghost.hurtTime = pose.hurt;
            ghost.setItemSlot(EquipmentSlot.MAINHAND, stack(pose.main));
            ghost.setItemSlot(EquipmentSlot.OFFHAND, stack(pose.off));
            for (int i = 0; i < 4; i++) {
                ghost.setItemSlot(ARMOR[i], stack(pose.armor == null ? null : pose.armor[i]));
            }
            ghost.setInvisible(false);
        }
        for (Map.Entry<String, RemotePlayer> e : ghosts.entrySet()) {
            if (!present.contains(e.getKey())) {
                e.getValue().setInvisible(true); // out of range in this frame
            }
        }
        RemotePlayer self = ghosts.get(clip.self);
        if (last && self != null && mc.getCameraEntity() != self) {
            mc.setCameraEntity(self);
        }
    }

    private RemotePlayer spawn(String id) {
        Minecraft mc = Minecraft.getInstance();
        String name = clip.names.getOrDefault(id, "Player");
        UUID uuid;
        try {
            uuid = UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            uuid = UUID.randomUUID();
        }
        if (mc.level.getPlayerByUUID(uuid) != null) {
            uuid = UUID.randomUUID(); // that player is here right now: do not clash with the real one
        }
        RemotePlayer ghost = new RemotePlayer(mc.level, new GameProfile(uuid, name));
        ghost.setId(nextId--);
        mc.level.addEntity(ghost);
        return ghost;
    }

    private static ItemStack stack(String id) {
        if (id == null) {
            return ItemStack.EMPTY;
        }
        try {
            return new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse(id)));
        } catch (Exception e) {
            return ItemStack.EMPTY;
        }
    }

    // ------------------------------------------------------------------ controls

    public void togglePause() {
        if (paused && position >= clip.frames.size() - 1) {
            seekTo(0);
        }
        paused = !paused;
    }

    public void seek(double seconds) {
        seekTo(position + seconds * 20);
    }

    public void seekTo(double frame) {
        position = Math.max(0, Math.min(clip.frames.size() - 1, frame));
        apply((int) position, true);
        apply((int) position, true); // twice: no interpolation smear across the jump
    }

    public void faster() {
        speed = Math.min(4f, speed * 2f);
    }

    public void slower() {
        speed = Math.max(0.25f, speed / 2f);
    }

    public float speed() {
        return speed;
    }

    public boolean paused() {
        return paused;
    }

    public double seconds() {
        return position / 20.0;
    }

    public double length() {
        return clip.seconds();
    }

    public double progress() {
        return clip.frames.size() <= 1 ? 1 : position / (clip.frames.size() - 1);
    }

    public int players() {
        return ghosts.size();
    }
}
