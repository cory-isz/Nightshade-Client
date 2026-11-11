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

    private final NumberSetting distance = add(new NumberSetting("Max Distance", "Render distance for block entities", 48, 8, 128, 4, " blocks"));
    private final BooleanSetting signsSeparately = add(new BooleanSetting("Limit Signs More", "Use a shorter distance for signs", true));
    private final NumberSetting signDistance = add(new NumberSetting("Sign Distance", "Render distance for signs", 24, 4, 64, 2, " blocks")).visibleWhen(signsSeparately::get);
    private final BooleanSetting containers = add(new BooleanSetting("Containers", "Cull chests, ender chests and shulker boxes", true));
    private final BooleanSetting decorations = add(new BooleanSetting("Decorations", "Cull banners and heads", true));
    private final BooleanSetting others = add(new BooleanSetting("Other Block Entities", "Cull everything else (beds, bells, lecterns...)", true));

    public BlockEntityCulling() {
        super("Block Entity Culling", "Skips rendering distant chests, signs and banners", ModuleCategory.PERFORMANCE);
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
        boolean skip = blockEntity.getBlockPos().distToCenterSqr(camera) > max * max;
        if (skip) {
            EffectProbe.hit("Block Entity Culling");
            SKIPPED.incrementAndGet();
        }
        return skip;
    }

    /** Block entities skipped since launch (shown by Frame Stats). */
    public static long skippedTotal() {
        return SKIPPED.get();
    }
}
