package com.nightshade.replay;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
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

    /** How the replay is watched. */
    public enum CameraMode {
        POV("Your view"), FOLLOW("Follow"), ORBIT("Orbit"), FREE("Free camera");

        public final String label;

        CameraMode(String label) {
            this.label = label;
        }
    }

    private CameraMode mode = CameraMode.POV;
    private RemotePlayer cam;
    private double camX, camY, camZ;
    private float camYaw, camPitch;
    private float orbitAngle;

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
        if (p.cam != null && mc.level != null) {
            // the camera entity was never added to the world
        }
        p.cam = null;
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
        p.updateCamera(); // the camera keeps moving while paused (orbit, free camera)
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
        if (last) {
            updateCamera();
        }
    }

    // ------------------------------------------------------------------ camera

    /** Places the camera for the current mode. Your view rides your recorded player; the others use a camera entity. */
    private void updateCamera() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        RemotePlayer self = ghosts.get(clip.self);
        if (mode == CameraMode.POV || self == null) {
            if (self != null && mc.getCameraEntity() != self) {
                mc.setCameraEntity(self);
            }
            return;
        }
        if (cam == null) {
            cam = new RemotePlayer(mc.level, new GameProfile(UUID.randomUUID(), "camera"));
            cam.setId(nextId--);
            cam.setInvisible(true);
            cam.noPhysics = true;
            cam.setNoGravity(true);
            // not added to the world: the game would tick it and pull it back; it only has to carry the view
            camX = self.getX();
            camY = self.getEyeY();
            camZ = self.getZ();
        }
        switch (mode) {
            case FOLLOW -> {
                double r = Math.toRadians(self.getYRot());
                camX = self.getX() + Math.sin(r) * 4.0;
                camZ = self.getZ() - Math.cos(r) * 4.0;
                camY = self.getEyeY() + 1.0;
                lookAt(self.getX(), self.getEyeY(), self.getZ());
            }
            case ORBIT -> {
                double cx = 0, cy = 0, cz = 0;
                int n = 0;
                for (RemotePlayer g : ghosts.values()) {
                    if (!g.isInvisible()) {
                        cx += g.getX();
                        cy += g.getY();
                        cz += g.getZ();
                        n++;
                    }
                }
                if (n == 0) {
                    cx = self.getX();
                    cy = self.getY();
                    cz = self.getZ();
                    n = 1;
                }
                cx /= n;
                cy /= n;
                cz /= n;
                orbitAngle += 1.2f;
                double a = Math.toRadians(orbitAngle);
                camX = cx + Math.sin(a) * 6.0;
                camZ = cz + Math.cos(a) * 6.0;
                camY = cy + 3.0;
                lookAt(cx, cy + 1.0, cz);
            }
            case FREE -> {
                // moved by moveFree / lookFree
            }
            default -> {
            }
        }
        cam.setOldPosAndRot();
        cam.yHeadRotO = cam.getYHeadRot();
        cam.snapTo(camX, camY - cam.getEyeHeight(), camZ, camYaw, camPitch);
        cam.setYHeadRot(camYaw);
        if (mc.getCameraEntity() != cam) {
            mc.setCameraEntity(cam);
            cam.setOldPosAndRot();
        }
    }

    private void lookAt(double x, double y, double z) {
        double dx = x - camX, dy = y - camY, dz = z - camZ;
        camYaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        camPitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
    }

    /** Next camera mode: Your view, Follow, Orbit, Free camera. Free starts where the camera is now. */
    public void cycleCamera() {
        CameraMode[] all = CameraMode.values();
        mode = all[(mode.ordinal() + 1) % all.length];
        if (mode == CameraMode.FREE && cam != null) {
            camX = cam.getX();
            camY = cam.getEyeY();
            camZ = cam.getZ();
        }
        updateCamera();
    }

    public CameraMode camera() {
        return mode;
    }

    /** Free camera: move along where it looks (forward, right, up), in blocks. */
    public void moveFree(double forward, double strafe, double up) {
        if (mode != CameraMode.FREE) {
            return;
        }
        double r = Math.toRadians(camYaw);
        camX += -Math.sin(r) * forward - Math.cos(r) * strafe;
        camZ += Math.cos(r) * forward - Math.sin(r) * strafe;
        camY += up;
    }

    /** Free camera: turn by these many degrees. */
    public void lookFree(float yaw, float pitch) {
        if (mode != CameraMode.FREE) {
            return;
        }
        camYaw += yaw;
        camPitch = Math.max(-90f, Math.min(90f, camPitch + pitch));
    }

    /** Test hook: where the camera is. */
    public double[] cameraPosForTest() {
        Entity e = Minecraft.getInstance().getCameraEntity();
        return e == null ? null : new double[]{e.getX(), e.getY(), e.getZ()};
    }

    public boolean cameraIsOwnViewForTest() {
        return Minecraft.getInstance().getCameraEntity() == ghosts.get(clip.self);
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
            return new ItemStack(BuiltInRegistries.ITEM.getValue(ResourceLocation.parse(id)));
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
