package com.nightshade.render;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * World-space line rendering for overlays (waypoint beams, block outlines), drawn at Fabric's {@code END_MAIN} with a custom
 * line pipeline that has depth testing disabled, so outlines stay visible through terrain.
 * <p>
 * Minecraft 1.21.11 renders through {@link RenderPipeline}s; vertices are submitted camera-relative into the
 * main pass's identity matrix stack, the same convention vanilla uses for block outlines.
 */
public final class WorldRenderUtil {
    private static final RenderPipeline OVERLAY_LINES = RenderPipeline.builder(RenderPipelines.LINES_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath("nightshade", "pipeline/overlay_lines"))
            .withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, false))
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withCull(false)
            .build();
    private static final RenderType OVERLAY_LAYER = RenderType.create("nightshade_overlay_lines", RenderSetup.builder(OVERLAY_LINES).createRenderSetup() /* no sortOnUpload: 26.2+ cannot sort LINES draws */);

    private WorldRenderUtil() {
    }

    /** Line layer without depth testing (visible through blocks). */
    public static RenderType throughWallsLines() {
        return OVERLAY_LAYER;
    }

    /**
     * Collects line segments, then submits them once through 26.2's submit pipeline
     * ({@code SubmitNodeCollector.submitCustomGeometry}), which copies the pose and writes the vertices later.
     */
    public static final class LineBatch {
        private final Vec3 camera;
        private final PoseStack stack;
        private final SubmitNodeCollector collector;
        private final float width;
        private float[] data = new float[7 * 64];
        private int count;

        private LineBatch(LevelRenderContext context, float width) {
            this.camera = Minecraft.getInstance().gameRenderer.mainCamera().position();
            this.stack = context.poseStack() != null ? context.poseStack() : new PoseStack();
            this.collector = context.submitNodeCollector();
            this.width = width;
        }

        private void segment(float x1, float y1, float z1, float x2, float y2, float z2, int color) {
            float dx = x2 - x1, dy = y2 - y1, dz = z2 - z1;
            if (dx * dx + dy * dy + dz * dz < 1e-12f) {
                return;
            }
            if ((count + 1) * 7 > data.length) {
                data = java.util.Arrays.copyOf(data, data.length * 2);
            }
            int o = count * 7;
            data[o] = x1; data[o + 1] = y1; data[o + 2] = z1;
            data[o + 3] = x2; data[o + 4] = y2; data[o + 5] = z2;
            data[o + 6] = Float.intBitsToFloat(color);
            count++;
        }

        public void line(double x1, double y1, double z1, double x2, double y2, double z2, int color) {
            segment((float) (x1 - camera.x), (float) (y1 - camera.y), (float) (z1 - camera.z),
                    (float) (x2 - camera.x), (float) (y2 - camera.y), (float) (z2 - camera.z), color);
        }

        /** Line from a camera-relative start point (e.g. a tracer origin in front of the camera). */
        public void lineFromCamera(Vec3 relativeStart, double x2, double y2, double z2, int color) {
            segment((float) relativeStart.x, (float) relativeStart.y, (float) relativeStart.z,
                    (float) (x2 - camera.x), (float) (y2 - camera.y), (float) (z2 - camera.z), color);
        }

        public void box(AABB box, int color) {
            double x1 = box.minX, y1 = box.minY, z1 = box.minZ;
            double x2 = box.maxX, y2 = box.maxY, z2 = box.maxZ;
            line(x1, y1, z1, x2, y1, z1, color);
            line(x2, y1, z1, x2, y1, z2, color);
            line(x2, y1, z2, x1, y1, z2, color);
            line(x1, y1, z2, x1, y1, z1, color);
            line(x1, y2, z1, x2, y2, z1, color);
            line(x2, y2, z1, x2, y2, z2, color);
            line(x2, y2, z2, x1, y2, z2, color);
            line(x1, y2, z2, x1, y2, z1, color);
            line(x1, y1, z1, x1, y2, z1, color);
            line(x2, y1, z1, x2, y2, z1, color);
            line(x2, y1, z2, x2, y2, z2, color);
            line(x1, y1, z2, x1, y2, z2, color);
        }

        /** Submits the batch (the width was fixed when the batch began; it is stored per vertex). */
        public void draw(float ignoredWidth) {
            if (count == 0 || collector == null) {
                return;
            }
            final float[] segments = java.util.Arrays.copyOf(data, count * 7);
            final int n = count;
            final float lineWidth = width;
            collector.submitCustomGeometry(stack, OVERLAY_LAYER, (pose, buffer) -> {
                Vector3f normal = new Vector3f();
                for (int k = 0; k < n; k++) {
                    int o = k * 7;
                    int color = Float.floatToRawIntBits(segments[o + 6]);
                    normal.set(segments[o + 3] - segments[o], segments[o + 4] - segments[o + 1], segments[o + 5] - segments[o + 2]).normalize();
                    buffer.addVertex(pose, segments[o], segments[o + 1], segments[o + 2]).setColor(color).setNormal(pose, normal).setLineWidth(lineWidth);
                    buffer.addVertex(pose, segments[o + 3], segments[o + 4], segments[o + 5]).setColor(color).setNormal(pose, normal).setLineWidth(lineWidth);
                }
            });
            count = 0;
        }

        public boolean isEmpty() {
            return count == 0;
        }
    }

    public static LineBatch begin(LevelRenderContext context, float lineWidth) {
        return new LineBatch(context, lineWidth);
    }
}
