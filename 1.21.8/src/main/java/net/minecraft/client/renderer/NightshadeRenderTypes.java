package net.minecraft.client.renderer;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;
import java.util.OptionalDouble;

/**
 * Line render types with a chosen width (Minecraft 1.21.8 sets line width per render type, and its factory is package-private,
 * so this helper lives in the same package).
 */
public final class NightshadeRenderTypes {
    private static final RenderPipeline OVERLAY_LINES = RenderPipeline.builder(RenderPipelines.LINES_SNIPPET)
            .withLocation(ResourceLocation.fromNamespaceAndPath("nightshade", "pipeline/overlay_lines"))
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withCull(false)
            .build();
    private static final Map<Long, RenderType> CACHE = new HashMap<>();

    private NightshadeRenderTypes() {
    }

    /** Lines {@code width} pixels wide; {@code throughWalls} draws them without depth testing. */
    public static synchronized RenderType lines(float width, boolean throughWalls) {
        float w = Math.round(Math.max(0.5f, width) * 2f) / 2f;
        long key = (long) (w * 2) << 1 | (throughWalls ? 1 : 0);
        return CACHE.computeIfAbsent(key, k -> RenderType.create(
                throughWalls ? "nightshade_overlay_lines" : "nightshade_lines", 1536,
                throughWalls ? OVERLAY_LINES : RenderPipelines.LINES,
                RenderType.CompositeState.builder()
                        .setLineState(new RenderStateShard.LineStateShard(OptionalDouble.of(w)))
                        .setLayeringState(RenderStateShard.VIEW_OFFSET_Z_LAYERING)
                        .setOutputState(RenderStateShard.ITEM_ENTITY_TARGET)
                        .createCompositeState(false)));
    }
}
