package com.nightshade.module.visual;

import com.nightshade.util.EffectProbe;
import com.mojang.blaze3d.vertex.PoseStack;
import com.nightshade.event.events.BlockOutlineEvent;
import com.nightshade.module.Module;
import com.nightshade.module.ModuleCategory;
import com.nightshade.module.setting.BooleanSetting;
import com.nightshade.module.setting.ColorSetting;
import com.nightshade.module.setting.ModeSetting;
import com.nightshade.module.setting.NumberSetting;
import com.nightshade.render.WorldRenderUtil;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.BlockOutlineRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Replaces the targeted-block outline with a configurable colour and width. */
public final class BlockOutline extends Module {
    private final ColorSetting color = add(new ColorSetting("Color", "Outline colour", 0xFFB794FF));
    private final NumberSetting width = add(new NumberSetting("Width", "Outline thickness", 2.5, 1, 8, 0.5));
    private final ModeSetting shape = add(new ModeSetting("Shape", "Which block shape to outline", "Outline", "Outline", "Full Block"));
    private final BooleanSetting wholeObject = add(new BooleanSetting("Whole Object", "Outline double chests, beds, doors and tall plants as one object", true));
    private final BooleanSetting throughWalls = add(new BooleanSetting("Through Walls", "Keep the outline visible behind other blocks", false));

    public BlockOutline() {
        super("Block Outline", "Custom colour and thickness for the block you look at", ModuleCategory.VISUAL);
        listen(BlockOutlineEvent.class, this::render);
    }

    private void render(BlockOutlineEvent event) {
        BlockOutlineRenderState outline = event.outline();
        if (outline == null || mc.gameRenderer == null) {
            return;
        }
        PoseStack matrices = event.context().poseStack();
        var collector = event.context().submitNodeCollector();
        if (matrices == null || collector == null) {
            return;
        }
        VoxelShape voxel = shape.is("Full Block") ? net.minecraft.world.phys.shapes.Shapes.block() : outline.shape();
        if (wholeObject.get()) {
            voxel = com.nightshade.render.MultiBlockShapes.whole(mc.level, outline.pos(), voxel, shape.is("Full Block"));
        }
        if (voxel == null || voxel.isEmpty()) {
            return;
        }
        Vec3 camera = mc.gameRenderer.mainCamera().position();
        BlockPos pos = outline.pos();
        RenderType layer = throughWalls.get() ? WorldRenderUtil.throughWallsLines() : RenderTypes.lines();
        matrices.pushPose();
        matrices.translate(pos.getX() - camera.x, pos.getY() - camera.y, pos.getZ() - camera.z);
        collector.submitShapeOutline(matrices, voxel, layer, color.argb(), width.floatValue(), throughWalls.get()); // last arg: translucent phase
        matrices.popPose();
        EffectProbe.hit("Block Outline");
        event.cancel(); // vanilla outline not drawn
    }
}
