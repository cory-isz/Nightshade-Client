package com.nightshade.cosmetic;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.PlayerRenderState;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

/** Draws the equipped 3D cosmetics on the local player (and on the Wardrobe's preview model). */
public final class CosmeticLayer extends RenderLayer<PlayerRenderState, PlayerModel> {
    private static final ResourceLocation WHITE = ResourceLocation.fromNamespaceAndPath("nightshade", "textures/cosmetic/white.png");
    /** Test counters: quads submitted per category since start. */
    public static final long[] SUBMITTED = new long[Catalog.Category.values().length];
    public static float lastWingAngle;

    public CosmeticLayer(RenderLayerParent<PlayerRenderState, PlayerModel> parent) {
        super(parent);
    }

    private static RenderType type() {
        return RenderType.entityCutoutNoCull(WHITE);
    }

    /** The cosmetic render type with another texture (the painted capes). */
    private static RenderType type(ResourceLocation texture) {
        return RenderType.entityCutoutNoCull(texture);
    }

    /** See-through and full-bright: the glowing parts of a painted cape. */
    private static RenderType glowType(ResourceLocation texture) {
        return RenderType.entityTranslucent(texture);
    }

    /** The outfit another Nightshade player wears (from the cosmetics service), or null for you / the Wardrobe model / non-users. */
    static java.util.Map<Catalog.Category, String> remote(PlayerRenderState state) {
        Minecraft mc = Minecraft.getInstance();
        if (state.id == Wardrobe.PREVIEW_ID || mc.level == null || (mc.player != null && state.id == mc.player.getId())) {
            return null;
        }
        return CloudSync.outfitOf(mc.level.getEntity(state.id));
    }

    private static String shown(java.util.Map<Catalog.Category, String> remote, Catalog.Category c, boolean preview) {
        return remote != null ? remote.get(c) : Wardrobe.shown(c, preview);
    }

    public static boolean applies(PlayerRenderState state) {
        if (state.id == Wardrobe.PREVIEW_ID) {
            return true;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && state.id == mc.player.getId()) {
            return !Wardrobe.hidden() && !state.isInvisible && !state.isSpectator;
        }
        return remote(state) != null && !state.isInvisible && !state.isSpectator;
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource out, int light, PlayerRenderState state, float yRot, float xRot) {
        if (!applies(state)) {
            return;
        }
        boolean preview = state.id == Wardrobe.PREVIEW_ID;
        java.util.Map<Catalog.Category, String> remote = remote(state);
        int overlay = LivingEntityRenderer.getOverlayCoords(state, 0f);
        float t = state.ageInTicks;
        String wing = shown(remote, Catalog.Category.WINGS, preview);
        if (wing != null && Catalog.byId(wing) != null) {
            float open = remote != null ? (state.isFallFlying ? 1f : 0f) : openness(state, preview);
            WingModel w = Models.wing(wing);
            float clear = WingPose.clearance(w);
            wings(pose, out, light, overlay, state, w, t, open, 0f, 1f, clear);
            if (w.lower != null) {
                wings(pose, out, light, overlay, state, w.lower, t + 6f, open, 5.5f, 0.78f, clear);
            }
        }
        String cape = shown(remote, Catalog.Category.CAPES, preview);
        if (cape != null && cape.startsWith("cape_") && !state.isFallFlying) {
            cape(pose, out, light, overlay, state, Models.cape(cape), cape.substring("cape_".length()));
        }
        String hat = shown(remote, Catalog.Category.HATS, preview);
        if (hat != null && Catalog.byId(hat) != null) {
            hat(pose, out, light, overlay, state, hat, t);
        }
        String aura = shown(remote, Catalog.Category.AURAS, preview);
        if (aura != null && Catalog.byId(aura) != null) {
            aura(pose, out, aura, t, overlay);
        }
        String pet = shown(remote, Catalog.Category.COMPANIONS, preview);
        if (pet != null && Catalog.byId(pet) != null) {
            companion(pose, out, light, overlay, pet, t, preview || remote != null);
        }
    }

    private static void draw(PoseStack pose, MultiBufferSource out, VoxelModel m, int light, int overlay, float unit, Catalog.Category cat) {
        if (m == null || m.quadCount() == 0) {
            return;
        }
        SUBMITTED[cat.ordinal()] += m.quadCount();
        m.render(pose.last(), out.getBuffer(type()), light, overlay, unit, 1f);
    }

    // ------------------------------------------------------------------ wings

    private static float localOpen;
    private static float previewOpen = 1f;
    private static long lastNanos;

    /**
     * 0 = folded down the back, 1 = spread. Wings open while jumping, falling, sprinting, flying or gliding and
     * fold again when you stand still or walk. The Wardrobe model opens and closes slowly to show both poses.
     */
    private static float openness(PlayerRenderState s, boolean preview) {
        long now = System.nanoTime();
        if (preview) {
            float dt = previewNanos == 0 ? 0f : Math.min(0.25f, (now - previewNanos) / 1e9f);
            previewNanos = now;
            float target = Wardrobe.previewWings == 1 ? 0f : Wardrobe.previewWings == 2 ? 1f : Math.sin(s.ageInTicks * 0.025f) > -0.35f ? 1f : 0f;
            previewOpen = step(previewOpen, target, dt);
            return previewOpen;
        }
        float dt = lastNanos == 0 ? 0f : Math.min(0.25f, (now - lastNanos) / 1e9f);
        lastNanos = now;
        Minecraft mc = Minecraft.getInstance();
        boolean airborne = mc.player != null && (!mc.player.onGround() || mc.player.isSprinting() || mc.player.getAbilities().flying);
        if ((airborne || s.isFallFlying) && !s.isCrouching) {
            lastAirborne = now;
        }
        // stay open a moment after landing, so small hops and bumps don't flick the wings shut and open again
        float target = !s.isCrouching && now - lastAirborne < HOLD_OPEN_NANOS ? 1f : 0f;
        localOpen = step(localOpen, target, dt);
        return localOpen;
    }

    private static final long HOLD_OPEN_NANOS = 450_000_000L;
    private static long lastAirborne;
    private static long previewNanos;

    /** Moves at a steady rate (opening a little faster than closing); the pose eases the start and end. */
    private static float step(float cur, float target, float dt) {
        float rate = target > cur ? 2.4f : 1.8f;
        return cur < target ? Math.min(target, cur + rate * dt) : Math.max(target, cur - rate * dt);
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    private void wings(PoseStack pose, MultiBufferSource out, int light, int overlay, PlayerRenderState s, WingModel w, float t, float open, float dropPx, float scale, float clear) {
        float speed = Math.min(1f, s.walkAnimationSpeed);
        float freq = 0.08f + 0.18f * open + 0.12f * speed;
        WingPose wp = WingPose.of(w, open, speed, (float) Math.sin(t * freq), (float) Math.sin(t * freq - 0.7f), s.isFallFlying);
        float sweep = wp.sweep, drop = wp.drop, bend = wp.bend;
        lastWingAngle = sweep + drop;
        float unit = WingModel.UNIT * w.size * scale;
        float unitZ = WingModel.UNIT_Z * scale;
        pose.pushPose();
        getParentModel().body.translateAndRotate(pose);
        pose.translate(0f, (WingPose.ROOT_Y + dropPx + clear) / 16f, WingPose.zOffset(open) / 16f);
        for (int side = 0; side < 2; side++) {
            pose.pushPose();
            if (side == 1) {
                pose.scale(-1f, 1f, 1f);
            }
            pose.translate(WingPose.ROOT_X / 16f, 0f, 0f);
            pose.mulPose(Axis.YP.rotationDegrees(-sweep));
            pose.mulPose(Axis.ZP.rotationDegrees(drop));
            pose.translate(0f, -w.anchorY * unit, 0f);
            for (int seg = 0; seg < w.base.length; seg++) {
                if (seg > 0) {
                    pose.translate((w.start[seg] - w.start[seg - 1]) * unit, 0f, 0f);
                    pose.mulPose(Axis.YP.rotationDegrees(-bend * (seg == 1 ? 0.45f : 0.6f)));
                }
                VoxelModel b = w.base[seg];
                VoxelModel g = w.glow[seg];
                if (b.quadCount() > 0) {
                    SUBMITTED[Catalog.Category.WINGS.ordinal()] += b.quadCount();
                    b.render(pose.last(), out.getBuffer(type()), light, overlay, unit, unitZ, 1f);
                }
                if (g.quadCount() > 0) {
                    SUBMITTED[Catalog.Category.WINGS.ordinal()] += g.quadCount();
                    g.render(pose.last(), out.getBuffer(type()), LightTexture.FULL_BRIGHT, overlay, unit, unitZ, 1f);
                }
            }
            pose.popPose();
        }
        pose.popPose();
    }

    // ------------------------------------------------------------------ capes

    private void cape(PoseStack pose, MultiBufferSource out, int light, int overlay, PlayerRenderState s, Designs.Cape c, String capeStyle) {
        pose.pushPose();
        getParentModel().body.translateAndRotate(pose);
        pose.translate(0f, 0f, 2.05f / 16f + (s.isCrouching ? 0.08f : 0f));
        boolean preview = s.id == Wardrobe.PREVIEW_ID;
        boolean own = !preview && remote(s) == null;
        if (own) {
            CapeSway.update(s.ageInTicks); // cape physics: a spring that follows your speed, falls, sprints and turns
        }
        float lift = own ? CapeSway.lift() : preview ? CapeSway.previewLift(s.ageInTicks) : 6f + s.capeLean / 2f + s.capeFlap + (s.isCrouching ? 20f : 0f);
        pose.mulPose(Axis.XP.rotationDegrees(lift));
        pose.mulPose(Axis.ZP.rotationDegrees(own ? CapeSway.side() : preview ? 0f : s.capeLean2 / 2f));
        if (FlatCape.has(capeStyle)) { // flat cloth with a painted picture
            SUBMITTED[Catalog.Category.CAPES.ordinal()] += 6;
            FlatCape.render(pose.last(), out.getBuffer(type(FlatCape.texture(capeStyle))), light, overlay, false);
            if (FlatCape.hasGlow(capeStyle)) {
                FlatCape.render(pose.last(), out.getBuffer(glowType(FlatCape.glow(capeStyle))), LightTexture.FULL_BRIGHT, overlay, true);
            }
        } else {
            draw(pose, out, c.model, light, overlay, c.unit, Catalog.Category.CAPES);
            draw(pose, out, c.glow, LightTexture.FULL_BRIGHT, overlay, c.unit, Catalog.Category.CAPES);
        }
        pose.popPose();
    }

    // ------------------------------------------------------------------ hats

    private void hat(PoseStack pose, MultiBufferSource out, int light, int overlay, PlayerRenderState s, String id, float t) {
        pose.pushPose();
        getParentModel().head.translateAndRotate(pose);
        for (Hats.Part part : Models.hat(id).parts) {
            pose.pushPose();
            float u = Hats.UNIT;
            switch (part.anim) {
                case 1 -> { // spin around a vertical axis
                    pose.translate(part.px * u, 0f, part.pz * u);
                    pose.mulPose(Axis.YP.rotation(t * 0.55f));
                    pose.translate(-part.px * u, 0f, -part.pz * u);
                }
                case 2 -> { // orbit the head
                    double a = t * 0.05;
                    pose.translate((float) Math.cos(a) * 15f * u, (-21f + (float) Math.sin(t * 0.09f) * 1.5f) * u, (float) Math.sin(a) * 15f * u);
                    pose.mulPose(Axis.YP.rotation((float) (-a * 2)));
                }
                case 3 -> pose.translate(0f, (float) Math.sin(t * 0.08f) * 1.2f * u, 0f);
                default -> {
                }
            }
            draw(pose, out, part.model, part.glow ? LightTexture.FULL_BRIGHT : light, overlay, u, Catalog.Category.HATS);
            pose.popPose();
        }
        pose.popPose();
    }

    // ------------------------------------------------------------------ companions

    /** A small buddy floating beside your right shoulder: it bobs, sways and flaps or spins its parts. */
    private void companion(PoseStack pose, MultiBufferSource out, int light, int overlay, String id, float t0, boolean preview) {
        float sleep = 0f, hop = 0f, spin = 0f, recoil = 0f, trail = 0f;
        if (!preview) { // your own pet reacts to you; the Wardrobe one just floats
            PetMood.update();
            sleep = PetMood.sleep();
            hop = PetMood.cheerHop();
            spin = PetMood.hurtSpin();
            recoil = PetMood.hurtRecoil();
            trail = CapeSway.trail();
        }
        float t = preview ? t0 : PetMood.clock();
        Companions.Companion c = Models.companion(id);
        float u = Companions.UNIT * 1.25f;
        pose.pushPose();
        float bob = (float) Math.sin(t * 0.08f) * 0.06f * (1f - 0.6f * sleep);
        pose.mulPose(Axis.YP.rotationDegrees(trail)); // trails behind you a little when you turn
        pose.translate(-0.78f, -0.5f + bob + 0.2f * sleep - hop, 0.12f + recoil); // sinks while asleep, hops when happy
        pose.mulPose(Axis.YP.rotationDegrees((float) Math.sin(t * 0.03f) * 18f * (1f - sleep) + spin));
        pose.mulPose(Axis.ZP.rotationDegrees((float) Math.sin(t * 0.05f) * 4f + 22f * sleep));
        for (Companions.Part part : c.parts) {
            pose.pushPose();
            switch (part.anim) {
                case Companions.SPIN -> {
                    pose.translate(part.px * u, 0f, part.pz * u);
                    pose.mulPose(Axis.YP.rotation(t * 0.12f));
                    pose.translate(-part.px * u, 0f, -part.pz * u);
                }
                case Companions.BOB -> pose.translate(0f, (float) Math.abs(Math.sin(t * 0.15f)) * -2.5f * u, 0f);
                case Companions.FLAP -> {
                    float angle = (float) Math.sin(t * 0.45f * c.flapSpeed) * 35f * (part.px < 0 ? 1f : -1f);
                    pose.translate(part.px * u, part.py * u, 0f);
                    pose.mulPose(Axis.ZP.rotationDegrees(angle));
                    pose.translate(-part.px * u, -part.py * u, 0f);
                }
                default -> {
                }
            }
            draw(pose, out, part.model, part.glow ? LightTexture.FULL_BRIGHT : light, overlay, u, Catalog.Category.COMPANIONS);
            pose.popPose();
        }
        pose.popPose();
    }

    // ------------------------------------------------------------------ auras (orbiting 3D specks)

    /** The movements an aura can use; downloaded auras pick one of these by name. */
    public static final java.util.Set<String> AURA_MOTIONS = java.util.Set.of("petals", "embers", "frost_motes", "fireflies", "hearts",
            "rainbow", "bubbles", "notes", "void_rift", "orbit", "snowfall", "twinkle");

    private static String motion(String id) {
        RemoteContent.Aura r = RemoteContent.aura(id);
        return r != null ? r.motion() : id;
    }

    /** How many specks an aura has. */
    public static int auraCount(String id) {
        RemoteContent.Aura r = RemoteContent.aura(id);
        if (r != null) {
            return r.count();
        }
        return switch (id) {
            case "petals" -> 22;
            case "frost_motes" -> 20;
            case "embers" -> 26;
            case "fireflies" -> 14;
            case "hearts" -> 16;
            case "rainbow" -> 28;
            case "void_rift" -> 30;
            case "bubbles" -> 16;
            case "notes" -> 12;
            default -> 22;
        };
    }

    /** An aura's colours (also used for its Wardrobe thumbnail). */
    public static int[] auraColors(String id) {
        RemoteContent.Aura r = RemoteContent.aura(id);
        if (r != null) {
            return r.colors();
        }
        return switch (id) {
            case "petals" -> new int[]{0xFFFF8FC8, 0xFFFFB8DC, 0xFFFF6FB0};
            case "frost_motes" -> new int[]{0xFFBFE4FF, 0xFFE6F6FF, 0xFF8EC5FF};
            case "embers" -> new int[]{0xFFFFD27A, 0xFFFF9F43, 0xFFE8592A};
            case "fireflies" -> new int[]{0xFFD8FF6A, 0xFFFFF27A};
            case "hearts" -> new int[]{0xFFFF4D7A, 0xFFFF8FB0, 0xFFFF2E5E};
            case "rainbow" -> new int[]{0xFFFF4D6D, 0xFFFF9F43, 0xFFFFE08A, 0xFF2EE59D, 0xFF3DA5FF, 0xFFB794FF};
            case "void_rift" -> new int[]{0xFF7C3AFF, 0xFF2A0E5A, 0xFFE6D8FF, 0xFF4A1EA0};
            case "bubbles" -> new int[]{0xFFBFE9FF, 0xFFE6F6FF, 0xFFD6C8FF};
            case "notes" -> new int[]{0xFFFF4D6D, 0xFF3DA5FF, 0xFF2EE59D, 0xFFFFC857};
            default -> new int[]{0xFFFFFFFF, 0xFFD9CCFF, 0xFFFFE08A};
        };
    }

    private void aura(PoseStack pose, MultiBufferSource out, String id, float t, int overlay) {
        int n = auraCount(id);
        int[] colors = auraColors(id);
        for (int i = 0; i < n; i++) {
            float phase = i * 2.399f;
            float x;
            float y;
            float z;
            float size;
            switch (motion(id)) {
                case "snowfall" -> {
                    // flakes drifting down all around you, swaying as they fall
                    float fall = ((t * 0.006f + i * 0.113f) % 1f);
                    float r = 0.5f + (i % 4) * 0.16f;
                    float a = phase + (float) Math.sin(t * 0.01f + i) * 0.4f;
                    x = (float) Math.cos(a) * r + (float) Math.sin(t * 0.04f + i * 1.3f) * 0.1f;
                    z = (float) Math.sin(a) * r + (float) Math.cos(t * 0.035f + i) * 0.1f;
                    y = -0.9f + fall * 2.6f;
                    size = 0.45f + (i % 3) * 0.15f; // small flakes, a few slightly bigger
                }
                case "twinkle" -> {
                    // little stars hanging still around you, each fading in and out
                    float a = phase + t * 0.004f;
                    float r = 0.55f + (i % 3) * 0.17f;
                    x = (float) Math.cos(a) * r;
                    z = (float) Math.sin(a) * r;
                    y = -0.6f + (i * 0.37f % 1f) * 2.0f;
                    size = Math.max(0f, (float) Math.sin(t * 0.07f + i * 1.9f)) * 1.4f;
                }
                case "petals" -> {
                    float fall = ((t * 0.02f + i * 0.137f) % 1f);
                    float r = 0.55f + (i % 3) * 0.12f;
                    x = (float) Math.cos(phase + t * 0.01f) * r + (float) Math.sin(t * 0.05f + i) * 0.08f;
                    z = (float) Math.sin(phase + t * 0.01f) * r;
                    y = -0.6f + fall * 2.0f;
                    size = 1.4f;
                }
                case "embers" -> {
                    float rise = ((t * 0.025f + i * 0.173f) % 1f);
                    float r = 0.35f + (i % 4) * 0.1f;
                    x = (float) Math.cos(phase) * r * (1f - rise * 0.4f);
                    z = (float) Math.sin(phase) * r * (1f - rise * 0.4f);
                    y = 1.45f - rise * 2.1f;
                    size = 1.2f * (1f - rise * 0.6f);
                }
                case "frost_motes" -> {
                    float a = phase + t * 0.015f;
                    x = (float) Math.cos(a) * 0.7f;
                    z = (float) Math.sin(a) * 0.7f;
                    y = -0.2f + (float) Math.sin(t * 0.03f + i) * 0.6f + (i % 3) * 0.3f;
                    size = 1.3f;
                }
                case "fireflies" -> {
                    // slow wandering, blinking on and off
                    x = (float) (Math.sin(t * 0.013f + i * 1.7f) * 0.8f);
                    z = (float) (Math.cos(t * 0.011f + i * 2.3f) * 0.8f);
                    y = 0.2f + (float) Math.sin(t * 0.02f + i) * 0.7f;
                    size = Math.max(0f, (float) Math.sin(t * 0.09f + i * 0.9f)) * 1.3f;
                }
                case "hearts" -> {
                    float rise = ((t * 0.012f + i * 0.21f) % 1f);
                    float r = 0.55f + (i % 2) * 0.15f;
                    x = (float) Math.cos(phase) * r + (float) Math.sin(t * 0.06f + i) * 0.06f;
                    z = (float) Math.sin(phase) * r;
                    y = 0.9f - rise * 2.4f;
                    size = 1.5f * (float) Math.sin(rise * Math.PI);
                }
                case "rainbow" -> {
                    // a slow spiral climbing round you
                    float u = ((t * 0.006f + i / (float) n) % 1f);
                    float a = u * 12.566f + t * 0.02f;
                    x = (float) Math.cos(a) * 0.65f;
                    z = (float) Math.sin(a) * 0.65f;
                    y = 1.4f - u * 2.6f;
                    size = 1.1f;
                }
                case "bubbles" -> {
                    // bob slowly upward with a gentle wobble, then pop and start again at your feet
                    float rise = ((t * 0.008f + i * 0.0625f) % 1f);
                    float r = 0.55f + (i % 3) * 0.12f;
                    x = (float) Math.cos(phase) * r + (float) Math.sin(t * 0.07f + i) * 0.07f;
                    z = (float) Math.sin(phase) * r + (float) Math.cos(t * 0.06f + i) * 0.07f;
                    y = 1.4f - rise * 2.6f;
                    size = 1.3f + (i % 3) * 0.35f;
                }
                case "notes" -> {
                    // circle your head, each note hopping in turn
                    float a = phase + t * 0.025f;
                    x = (float) Math.cos(a) * 0.65f;
                    z = (float) Math.sin(a) * 0.65f;
                    y = -0.35f - (float) Math.abs(Math.sin(t * 0.12f + i * 0.8f)) * 0.35f;
                    size = 1.25f;
                }
                case "void_rift" -> {
                    // pulled in towards you and reborn outside
                    float u = ((t * 0.01f + i * 0.0331f) % 1f);
                    float r = 1.1f * (1f - u) + 0.15f;
                    float a = phase + u * 6f;
                    x = (float) Math.cos(a) * r;
                    z = (float) Math.sin(a) * r;
                    y = 0.1f + (float) Math.sin(phase * 3 + t * 0.03f) * 0.5f;
                    size = 0.6f + 1.2f * (1f - u);
                }
                default -> {
                    float a = phase + t * 0.04f * (i % 2 == 0 ? 1 : -1);
                    float r = 0.6f + (i % 3) * 0.1f;
                    x = (float) Math.cos(a) * r;
                    z = (float) Math.sin(a) * r;
                    y = -0.3f + (i % 5) * 0.32f;
                    size = 0.8f + 0.6f * (float) Math.abs(Math.sin(t * 0.15f + i));
                }
            }
            size *= 1.7f;
            pose.pushPose();
            pose.translate(x, y, z);
            pose.mulPose(Axis.YP.rotation(t * 0.05f + i));
            pose.mulPose(Axis.XP.rotation(t * 0.04f + i));
            pose.translate(-size / 32f, -size / 32f, -size / 32f);
            draw(pose, out, Models.speck(colors[i % colors.length]), LightTexture.FULL_BRIGHT, overlay, size / 16f, Catalog.Category.AURAS);
            pose.popPose();
        }
    }
}
