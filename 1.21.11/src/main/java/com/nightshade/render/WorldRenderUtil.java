package com.nightshade.render;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
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
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withCull(false)
            .build();
    private static final RenderType OVERLAY_LAYER = RenderType.create("nightshade_overlay_lines", RenderSetup.builder(OVERLAY_LINES).sortOnUpload().createRenderSetup());

    private WorldRenderUtil() {
    }

    /** Line layer without depth testing (visible through blocks). */
    public static RenderType throughWallsLines() {
        return OVERLAY_LAYER;
    }

    /** Collects line segments into one buffer; call {@link #draw} once per frame. */
    public static final class LineBatch {
        private final Vec3 camera;
        private final PoseStack.Pose entry;
        private final MultiBufferSource.BufferSource consumers;
        private final VertexConsumer buffer;
        private final Vector3f normal = new Vector3f();
        private final float width;
        private boolean empty = true;

        private LineBatch(WorldRenderContext context, float width) {
            this.camera = Minecraft.getInstance().gameRenderer.getMainCamera().position();
            PoseStack stack = context.matrices() != null ? context.matrices() : new PoseStack();
            this.entry = stack.last();
            this.consumers = context.consumers() instanceof MultiBufferSource.BufferSource immediate
                    ? immediate : Minecraft.getInstance().renderBuffers().bufferSource();
            this.buffer = consumers.getBuffer(OVERLAY_LAYER);
            this.width = width;
        }

        private void segment(float x1, float y1, float z1, float x2, float y2, float z2, int color) {
            normal.set(x2 - x1, y2 - y1, z2 - z1);
            if (normal.lengthSquared() < 1e-12f) {
                return;
            }
            normal.normalize();
            buffer.addVertex(entry, x1, y1, z1).setColor(color).setNormal(entry, normal).setLineWidth(width);
            buffer.addVertex(entry, x2, y2, z2).setColor(color).setNormal(entry, normal).setLineWidth(width);
            empty = false;
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

        /** Submits the batch. The width was fixed when the batch began (1.21.11 stores it per vertex). */
        public void draw(float ignoredWidth) {
            consumers.endBatch(OVERLAY_LAYER);
        }

        public boolean isEmpty() {
            return empty;
        }
    }

    public static LineBatch begin(WorldRenderContext context, float lineWidth) {
        return new LineBatch(context, lineWidth);
    }
}
