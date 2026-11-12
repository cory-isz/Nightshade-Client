package com.nightshade.module.performance;

import com.nightshade.util.EffectProbe;
import com.nightshade.module.Module;
import com.nightshade.module.ModuleCategory;
import com.nightshade.module.setting.BooleanSetting;
import com.nightshade.module.setting.NumberSetting;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.world.level.block.entity.BannerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.EnderChestBlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SkullBlockEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Skips drawing distant block entities - chests, signs, banners, heads - which are rendered every frame like
 * entities and are a common FPS sink in bases, lobbies and spawn areas. Signs are the most expensive (text).
 */
public final class BlockEntityCulling extends Module {
    private static BlockEntityCulling instance;
    private static final AtomicLong SKIPPED = new AtomicLong();

    private final NumberSetting distance = add(new NumberSetting("Max Distance", "Render distance for block entities (64 = like vanilla)", 64, 8, 128, 4, " blocks"));
    private final BooleanSetting signsSeparately = add(new BooleanSetting("Limit Signs More", "Use a shorter distance for signs", false));
    private final NumberSetting signDistance = add(new NumberSetting("Sign Distance", "Render distance for signs", 24, 4, 64, 2, " blocks")).visibleWhen(signsSeparately::get);
    private final BooleanSetting behindWalls = add(new BooleanSetting("Hide Behind Walls", "Skip block entities completely hidden behind solid blocks (you cannot see them anyway)", true));
    /** block pos -> (game time of the test << 1) | visible */
    private final it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap visibility = new it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap();
    private long visibilityTime = -1;
    private int testsThisTick;
    private final BooleanSetting containers = add(new BooleanSetting("Containers", "Cull chests, ender chests and shulker boxes", true));
    private final BooleanSetting decorations = add(new BooleanSetting("Decorations", "Cull banners and heads", true));
    private final BooleanSetting others = add(new BooleanSetting("Other Block Entities", "Cull everything else (beds, bells, lecterns...)", true));

    public BlockEntityCulling() {
        super("Block Entity Culling", "Skips chests, signs and banners that are hidden behind walls or far away", ModuleCategory.PERFORMANCE);
        enabledByDefault(); // with the default settings it only skips what is hidden behind walls
        instance = this;
    }

    public static boolean shouldSkip(BlockEntity blockEntity, Vec3 camera) {
        BlockEntityCulling culling = instance;
        if (culling == null || !culling.isEnabled()) {
            return false;
        }
        double max;
        if (blockEntity instanceof SignBlockEntity) {
            max = culling.signsSeparately.get() ? culling.signDistance.get() : culling.distance.get();
        } else if (blockEntity instanceof ChestBlockEntity || blockEntity instanceof EnderChestBlockEntity
                || blockEntity instanceof ShulkerBoxBlockEntity) {
            if (!culling.containers.get()) {
                return false;
            }
            max = culling.distance.get();
        } else if (blockEntity instanceof BannerBlockEntity || blockEntity instanceof SkullBlockEntity) {
            if (!culling.decorations.get()) {
                return false;
            }
            max = culling.distance.get();
        } else {
            if (!culling.others.get()) {
                return false;
            }
            max = culling.distance.get();
        }
        boolean skip = blockEntity.getBlockPos().distToCenterSqr(camera) > max * max
                || culling.behindWalls.get() && culling.hidden(blockEntity, camera);
        if (skip) {
            EffectProbe.hit("Block Entity Culling");
            SKIPPED.incrementAndGet();
        }
        return skip;
    }

    /** Whether the block entity is completely hidden behind solid blocks; cached for a few ticks per block. */
    private boolean hidden(BlockEntity blockEntity, Vec3 camera) {
        net.minecraft.world.level.Level level = blockEntity.getLevel();
        if (level == null) {
            return false;
        }
        long now = level.getGameTime();
        if (now != visibilityTime) {
            visibilityTime = now;
            testsThisTick = 0;
            if (visibility.size() > 8192) {
                visibility.clear();
            }
        }
        long key = blockEntity.getBlockPos().asLong();
        long cached = visibility.getOrDefault(key, Long.MIN_VALUE);
        if (cached != Long.MIN_VALUE && now - (cached >> 1) < 4) {
            return (cached & 1L) == 0;
        }
        if (testsThisTick++ > 400) {
            return false; // out of budget this tick: draw it, test it next tick
        }
        net.minecraft.core.BlockPos pos = blockEntity.getBlockPos();
        boolean visible = com.nightshade.util.Occlusion.isVisible(level, new net.minecraft.world.phys.AABB(pos).inflate(0.05), camera.x, camera.y, camera.z);
        visibility.put(key, (now << 1) | (visible ? 1L : 0L));
        return !visible;
    }

    /** Block entities skipped since launch (shown by Frame Stats). */
    public static long skippedTotal() {
        return SKIPPED.get();
    }
}
