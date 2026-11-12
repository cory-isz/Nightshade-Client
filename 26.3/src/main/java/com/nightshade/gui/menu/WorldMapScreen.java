package com.nightshade.gui.menu;

import com.mojang.blaze3d.platform.NativeImage;
import com.nightshade.NightshadeClient;
import com.nightshade.gui.widget.NsButton;
import com.nightshade.map.MapSampler;
import com.nightshade.module.interfaces.Notifications;
import com.nightshade.render.ColorUtil;
import com.nightshade.render.Fonts;
import com.nightshade.render.RenderUtil;
import com.nightshade.render.Theme;
import com.nightshade.waypoint.Waypoint;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;

/**
 * Full-screen world map of everything loaded around you: drag to look around, scroll to zoom, right click to drop a
 * waypoint there. The map fills in a few rows per frame so opening it is instant.
 */
public final class WorldMapScreen extends NsMenuScreen {
    private static final int TEX = 256;
    private static final int[] ZOOMS = {1, 2, 4, 8};
    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath("nightshade", "world_map");

    private final MapSampler sampler = new MapSampler();
    private DynamicTexture texture;
    private int zoomIndex = 1;
    private double centerX, centerZ;
    private int sampledCX = Integer.MIN_VALUE, sampledCZ, sampledStep;
    private int row = TEX;
    private final int[] heights = new int[TEX];
    private boolean dragging;
    private int mapX1, mapY1, mapSize;
    private final NsButton done = new NsButton("Done", NsButton.Style.PRIMARY, this::onClose);
    private final NsButton recenter = new NsButton("Center on me", NsButton.Style.SECONDARY, this::recenter);

    public WorldMapScreen() {
        super(Component.literal("World Map"));
        recenter();
    }

    private void recenter() {
        if (minecraft != null && minecraft.player != null) {
            centerX = minecraft.player.getX();
            centerZ = minecraft.player.getZ();
        } else if (net.minecraft.client.Minecraft.getInstance().player != null) {
            centerX = net.minecraft.client.Minecraft.getInstance().player.getX();
            centerZ = net.minecraft.client.Minecraft.getInstance().player.getZ();
        }
    }

    private int step() {
        return ZOOMS[zoomIndex];
    }

    /** Starts filling the map again when the view moved or the zoom changed. */
    private void restartIfMoved() {
        int cx = (int) Math.floor(centerX);
        int cz = (int) Math.floor(centerZ);
        if (cx != sampledCX || cz != sampledCZ || step() != sampledStep) {
            sampledCX = cx;
            sampledCZ = cz;
            sampledStep = step();
            row = 0;
            if (texture != null && texture.getPixels() != null) {
                texture.getPixels().fillRect(0, 0, TEX, TEX, 0xFF15121E);
            }
        }
    }

    private void sampleRows(int count) {
        Level level = minecraft.level;
        if (level == null || texture == null || row >= TEX) {
            return;
        }
        NativeImage image = texture.getPixels();
        int s = sampledStep;
        int originX = sampledCX - TEX / 2 * s;
        int originZ = sampledCZ - TEX / 2 * s;
        int caveTop = level.dimensionType().hasCeiling() && minecraft.player != null ? minecraft.player.getBlockY() + 1 : Integer.MIN_VALUE;
        if (row == 0) {
            for (int gx = 0; gx < TEX; gx++) {
                heights[gx] = sampler.height(level, originX + gx * s, originZ - s, caveTop);
            }
        }
        for (int n = 0; n < count && row < TEX; n++, row++) {
            int z = originZ + row * s;
            for (int gx = 0; gx < TEX; gx++) {
                int x = originX + gx * s;
                int h = sampler.height(level, x, z, caveTop);
                int color = sampler.color(level, x, z, h, heights[gx]);
                heights[gx] = h;
                image.setPixel(gx, row, color == 0 ? 0xFF15121E : color);
            }
        }
        texture.upload();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        if (texture == null) {
            texture = new DynamicTexture(() -> "nightshade world map", new NativeImage(TEX, TEX, true));
            texture.getPixels().fillRect(0, 0, TEX, TEX, 0xFF15121E);
            minecraft.getTextureManager().register(TEXTURE, texture);
        }
        if (!dragging) {
            restartIfMoved();
        }
        sampleRows(16);
        mapSize = Math.min(width - 40, height - 70);
        mapX1 = (width - mapSize) / 2;
        mapY1 = 30;
        RenderUtil.rect(ctx, mapX1 - 2, mapY1 - 2, mapX1 + mapSize + 2, mapY1 + mapSize + 2, 0xFF000000);
        RenderUtil.roundedOutline(ctx, mapX1 - 3, mapY1 - 3, mapX1 + mapSize + 3, mapY1 + mapSize + 3, 3, ColorUtil.withAlpha(Theme.accent(), 0xA0));
        // while dragging, the old picture slides with the mouse until the new area is sampled
        double shiftX = (sampledCX - centerX) / sampledStep * mapSize / TEX;
        double shiftZ = (sampledCZ - centerZ) / sampledStep * mapSize / TEX;
        ctx.enableScissor(mapX1, mapY1, mapX1 + mapSize, mapY1 + mapSize);
        ctx.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, mapX1 + (int) shiftX, mapY1 + (int) shiftZ, 0f, 0f, mapSize, mapSize, TEX, TEX, TEX, TEX);
        float perBlock = mapSize / (float) (TEX * step());
        float cx = mapX1 + mapSize / 2f;
        float cy = mapY1 + mapSize / 2f;
        for (Waypoint w : NightshadeClient.client().waypoints().inDimension()) {
            int x = Math.round(cx + (float) (w.x() + 0.5 - centerX) * perBlock);
            int y = Math.round(cy + (float) (w.z() + 0.5 - centerZ) * perBlock);
            RenderUtil.rect(ctx, x - 3, y - 3, x + 4, y + 4, 0xFF000000);
            RenderUtil.rect(ctx, x - 2, y - 2, x + 3, y + 3, 0xFF000000 | w.color());
            Fonts.draw(ctx, w.name(), x + 6, y - 4, 0xFFFFFFFF, true);
        }
        if (minecraft.player != null) {
            int x = Math.round(cx + (float) (minecraft.player.getX() - centerX) * perBlock);
            int y = Math.round(cy + (float) (minecraft.player.getZ() - centerZ) * perBlock);
            ctx.pose().pushMatrix();
            ctx.pose().translate(x, y);
            ctx.pose().rotate((float) Math.toRadians(minecraft.player.getYRot() + 180f));
            for (int r = 0; r < 7; r++) {
                int half = r / 2 + 1;
                RenderUtil.rect(ctx, -half - 1, r - 4 - 1, half + 1, r - 4 + 1, 0xFF000000);
            }
            for (int r = 0; r < 7; r++) {
                int half = r / 2;
                RenderUtil.rect(ctx, -half, r - 4, half + 1, r - 3, Theme.accentLight());
            }
            ctx.pose().popMatrix();
        }
        ctx.disableScissor();

        Fonts.draw(ctx, "World Map", mapX1, 12, Theme.TEXT, false);
        String help = "Drag to move  |  Scroll to zoom (" + step() + " block" + (step() == 1 ? "" : "s") + "/px)  |  Right click: waypoint";
        Fonts.draw(ctx, Fonts.trim(help, mapSize - Fonts.width("World Map") - 12), mapX1 + Fonts.width("World Map") + 12, 12, Theme.TEXT_DIM, false);
        if (RenderUtil.isHovered(mouseX, mouseY, mapX1, mapY1, mapX1 + mapSize, mapY1 + mapSize)) {
            int bx = (int) Math.floor(centerX + (mouseX - cx) / perBlock);
            int bz = (int) Math.floor(centerZ + (mouseY - cy) / perBlock);
            Fonts.draw(ctx, "X " + bx + "  Z " + bz, mapX1, mapY1 + mapSize + 8, Theme.TEXT_MUTED, false);
        }
        int by = mapY1 + mapSize + 5;
        done.render(ctx, mapX1 + mapSize - 70, by, 70, 16, mouseX, mouseY);
        recenter.render(ctx, mapX1 + mapSize - 70 - 6 - 96, by, 96, 16, mouseX, mouseY);
        Notifications.renderIfEnabled(ctx);
    }

    @Override
    protected boolean onMouseClicked(double mouseX, double mouseY, int button) {
        if (done.click(mouseX, mouseY, button) || recenter.click(mouseX, mouseY, button)) {
            return true;
        }
        if (!RenderUtil.isHovered(mouseX, mouseY, mapX1, mapY1, mapX1 + mapSize, mapY1 + mapSize)) {
            return false;
        }
        if (button == 0) {
            dragging = true;
            return true;
        }
        if (button == 1 && minecraft.level != null) {
            float perBlock = mapSize / (float) (TEX * step());
            int bx = (int) Math.floor(centerX + (mouseX - (mapX1 + mapSize / 2f)) / perBlock);
            int bz = (int) Math.floor(centerZ + (mouseY - (mapY1 + mapSize / 2f)) / perBlock);
            int caveTop = minecraft.level.dimensionType().hasCeiling() && minecraft.player != null ? minecraft.player.getBlockY() + 1 : Integer.MIN_VALUE;
            int y = sampler.height(minecraft.level, bx, bz, caveTop);
            Waypoint w = NightshadeClient.client().waypoints().addAt(null, bx, y == Integer.MIN_VALUE ? 64 : y + 1, bz);
            if (w != null) {
                NightshadeClient.client().notifications().success("Waypoints", w.name() + " added at " + bx + ", " + bz);
            }
            return true;
        }
        return false;
    }

    @Override
    protected boolean onMouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (dragging) {
            float perBlock = mapSize / (float) (TEX * step());
            centerX -= deltaX / perBlock;
            centerZ -= deltaY / perBlock;
            return true;
        }
        return false;
    }

    @Override
    protected boolean onMouseReleased(double mouseX, double mouseY, int button) {
        if (dragging) {
            dragging = false;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int next = Math.max(0, Math.min(ZOOMS.length - 1, zoomIndex + (verticalAmount < 0 ? 1 : verticalAmount > 0 ? -1 : 0)));
        zoomIndex = next;
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** Test hook: rows of the map filled so far. */
    public int sampledRowsForTest() {
        return row;
    }

    @Override
    public void removed() {
        if (texture != null) {
            minecraft.getTextureManager().release(TEXTURE);
            texture = null;
        }
        super.removed();
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(null);
    }
}
