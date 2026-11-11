package com.nightshade.replay;

import com.nightshade.NightshadeClient;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Keeps a rolling buffer of the last N seconds of nearby players; {@link #save()} writes it as a clip. */
public final class ReplayRecorder {
    private static final EquipmentSlot[] ARMOR = {EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD};
    private static final Deque<ReplayClip.Frame> BUFFER = new ArrayDeque<>();
    private static final Map<String, String> NAMES = new LinkedHashMap<>();
    private static final Map<String, Float> LAST_SWING = new LinkedHashMap<>();

    private ReplayRecorder() {
    }

    public static Path folder() {
        return FabricLoader.getInstance().getGameDir().resolve("nightshade").resolve("replays");
    }

    public static void clear() {
        BUFFER.clear();
        NAMES.clear();
        LAST_SWING.clear();
    }

    public static int bufferedTicks() {
        return BUFFER.size();
    }

    /** Called every client tick while Fight Replay is on. */
    public static void tick(int seconds, double radius) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || ReplayPlayer.active()) {
            if (mc.level == null) {
                clear();
            }
            return;
        }
        ReplayClip.Frame frame = new ReplayClip.Frame();
        double r2 = radius * radius;
        for (Player p : mc.level.players()) {
            if (p != mc.player && p.distanceToSqr(mc.player) > r2) {
                continue;
            }
            String id = p.getUUID().toString();
            NAMES.put(id, p.getGameProfile().name());
            ReplayClip.Pose pose = new ReplayClip.Pose();
            pose.id = id;
            pose.x = p.getX();
            pose.y = p.getY();
            pose.z = p.getZ();
            pose.yaw = p.getYRot();
            pose.pitch = p.getXRot();
            pose.head = p.getYHeadRot();
            pose.body = p.yBodyRot;
            pose.sneak = p.isShiftKeyDown();
            // A new swing starts when the arm animation restarts (also catches fast repeated clicks).
            float anim = p.getSwingAnimation(1f);
            Float last = LAST_SWING.put(id, anim);
            pose.swing = anim > 0f && (last == null || anim < last || last == 0f);
            pose.hurt = p.hurtTime;
            pose.main = item(p.getMainHandItem());
            pose.off = item(p.getOffhandItem());
            pose.armor = new String[4];
            for (int i = 0; i < 4; i++) {
                pose.armor[i] = item(p.getItemBySlot(ARMOR[i]));
            }
            frame.players.add(pose);
        }
        BUFFER.addLast(frame);
        while (BUFFER.size() > seconds * 20) {
            BUFFER.removeFirst();
        }
    }

    private static String item(ItemStack stack) {
        return stack.isEmpty() ? null : BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }

    /** Writes the buffer as a clip; returns the file, or null when there is nothing to save. */
    public static Path save() throws java.io.IOException {
        Minecraft mc = Minecraft.getInstance();
        if (BUFFER.isEmpty() || mc.player == null || mc.level == null) {
            return null;
        }
        ReplayClip clip = new ReplayClip();
        clip.client = NightshadeClient.VERSION;
        clip.server = mc.getCurrentServer() != null ? mc.getCurrentServer().ip : "singleplayer";
        clip.dimension = mc.level.dimension().toString();
        clip.recordedAt = System.currentTimeMillis();
        clip.self = mc.player.getUUID().toString();
        clip.names.putAll(NAMES);
        clip.frames = new ArrayList<>(BUFFER);
        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH.mm.ss"));
        String where = clip.server.replaceAll("[^A-Za-z0-9.-]", "_").toLowerCase(Locale.ROOT);
        Path file = folder().resolve(stamp + "_" + where + ".nsr");
        clip.write(file);
        return file;
    }
}
