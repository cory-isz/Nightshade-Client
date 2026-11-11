package com.nightshade.gui.menu;

import com.nightshade.NightshadeClient;
import com.nightshade.gui.widget.NsButton;
import com.nightshade.module.interfaces.Notifications;
import com.nightshade.module.visual.Cosmetics;
import com.nightshade.render.Animation;
import com.nightshade.render.ColorUtil;
import com.nightshade.render.Fonts;
import com.nightshade.render.Fx;
import com.nightshade.render.RenderUtil;
import com.nightshade.render.Theme;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/** Cosmetics: pick your cape. In a world your character is shown next to the cards, wearing the choice. */
public final class CosmeticsScreen extends NsMenuScreen {
    @Nullable
    private final Screen parent;
    private final Map<String, Animation> hover = new HashMap<>();
    private final NsButton done = new NsButton("Done", NsButton.Style.PRIMARY, this::onClose);
    private int px1, py1, px2, py2;
    private final int[][] cards = new int[Cosmetics.CAPES.length][4];

    public CosmeticsScreen(@Nullable Screen parent) {
        super(Component.literal("Cosmetics"));
        this.parent = parent;
    }

    private Cosmetics module() {
        return NightshadeClient.client().modules().get(Cosmetics.class);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        int w = Math.min(width - 24, 520);
        int h = Math.min(height - 24, 300);
        px1 = (width - w) / 2;
        px2 = px1 + w;
        py1 = (height - h) / 2;
        py2 = py1 + h;
        Fx.shadow(ctx, px1, py1 + 3, px2, py2 + 3, 12, 0xA0000000);
        RenderUtil.roundedRect(ctx, px1, py1, px2, py2, 8, ColorUtil.withAlpha(Theme.BACKGROUND, 0xD0));
        RenderUtil.roundedOutline(ctx, px1, py1, px2, py2, 8, Theme.BORDER);
        int mid = (px1 + px2) / 2;
        RenderUtil.horizontalGradient(ctx, px1 + 8, py1, mid, py1 + 1, 0x00000000, ColorUtil.withAlpha(Theme.accentLight(), 0xB0));
        RenderUtil.horizontalGradient(ctx, mid, py1, px2 - 8, py1 + 1, ColorUtil.withAlpha(Theme.accentLight(), 0xB0), 0x00000000);
        Fonts.draw(ctx, "Cosmetics", px1 + 14, py1 + 11, Theme.TEXT, false);
        Fonts.draw(ctx, "Capes and elytra skins - client-side, only you see them", px1 + 14, py1 + 22, Theme.TEXT_DIM, false);

        Cosmetics module = module();
        String chosen = module == null || !module.isEnabled() ? "None" : module.capeName();
        int gridX1 = px1 + 14;
        int gridX2 = px2 - 14;
        boolean preview = minecraft.player != null && w >= 380;
        if (preview) {
            // Character preview on the left, turned so the cape shows.
            int pw = 120;
            int cx1 = px1 + 14;
            RenderUtil.roundedRect(ctx, cx1, py1 + 40, cx1 + pw, py2 - 34, 6, 0x40000000);
            Fx.glow(ctx, cx1 + pw / 2f, py1 + 40 + (py2 - py1 - 74) * 0.6f, 60, ColorUtil.withAlpha(Theme.accent(), 0x50));
            int cx = cx1 + pw / 2;
            // a mouse position far to the side makes the model look away, showing its back and cape
            InventoryScreen.extractEntityInInventoryFollowsMouse(ctx, cx1, py1 + 44, cx1 + pw, py2 - 38, 52, 0.0625f,
                    cx + 400, py1 + 60, minecraft.player);
            Fonts.drawCentered(ctx, "Turn around in F5 to see it", cx, py2 - 30 - 10, Theme.TEXT_DIM, false);
            gridX1 = cx1 + pw + 12;
        }
        int cols = Math.max(3, Math.min(4, (gridX2 - gridX1) / 70));
        int gap = 8;
        int cw = (gridX2 - gridX1 - gap * (cols - 1)) / cols;
        int ch = Math.min(cw * 8 / 5 + 14, (py2 - py1 - 80 - gap) / 2);
        for (int i = 0; i < Cosmetics.CAPES.length; i++) {
            String name = Cosmetics.CAPES[i];
            int x = gridX1 + (i % cols) * (cw + gap);
            int y = py1 + 40 + (i / cols) * (ch + gap);
            cards[i] = new int[]{x, y, x + cw, y + ch};
            boolean hovered = RenderUtil.isHovered(mouseX, mouseY, x, y, x + cw, y + ch);
            float hp = hover.computeIfAbsent(name, k -> new Animation(16f, 0f)).setTarget(hovered ? 1f : 0f).update();
            boolean sel = name.equals(chosen);
            if (hp > 0.01f || sel) {
                Fx.glowRect(ctx, x - 10, y - 8, x + cw + 10, y + ch + 8, ColorUtil.withAlpha(Theme.accent(), Math.round((sel ? 0x40 : 0x28) * Math.max(hp, sel ? 1f : 0f))));
            }
            RenderUtil.roundedRect(ctx, x, y, x + cw, y + ch, 6, sel ? ColorUtil.withAlpha(Theme.accent(), 0x55) : ColorUtil.lerp(0x40000000, 0x30FFFFFF, hp));
            RenderUtil.roundedOutline(ctx, x, y, x + cw, y + ch, 6, sel ? ColorUtil.withAlpha(Theme.accent(), 0xE0) : ColorUtil.lerp(0x1CFFFFFF, ColorUtil.withAlpha(Theme.accent(), 0x90), hp));
            int artH = ch - 18;
            int artW = Math.min(cw - 16, artH * 10 / 16);
            artH = artW * 16 / 10;
            int ax = x + (cw - artW) / 2;
            int ay = y + 5;
            if ("None".equals(name)) {
                RenderUtil.roundedOutline(ctx, ax, ay, ax + artW, ay + artH, 3, 0x40FFFFFF);
                Fonts.drawCentered(ctx, "∅", x + cw / 2, ay + artH / 2 - 4, Theme.TEXT_DIM, false);
            } else {
                // the outside panel of the cape texture (10x16 at 12,1 of the 64x32 image), scaled up
                ctx.blit(RenderPipelines.GUI_TEXTURED, Cosmetics.capeTexture(name), ax, ay, 12f, 1f, artW, artH, 10, 16, 64, 32);
                RenderUtil.roundedOutline(ctx, ax - 1, ay - 1, ax + artW + 1, ay + artH + 1, 2, 0x30FFFFFF);
            }
            Fonts.drawCentered(ctx, name, x + cw / 2, y + ch - 11, sel ? 0xFFFFFFFF : Theme.TEXT_MUTED, false);
        }
        done.render(ctx, px2 - 14 - 80, py2 - 25, 80, 17, mouseX, mouseY);
        Notifications.renderIfEnabled(ctx);
    }

    @Override
    protected boolean onMouseClicked(double mouseX, double mouseY, int button) {
        if (done.click(mouseX, mouseY, button)) {
            return true;
        }
        if (button != 0) {
            return false;
        }
        for (int i = 0; i < Cosmetics.CAPES.length; i++) {
            int[] r = cards[i];
            if (RenderUtil.isHovered(mouseX, mouseY, r[0], r[1], r[2], r[3])) {
                Cosmetics module = module();
                if (module != null) {
                    if (!module.isEnabled()) {
                        NightshadeClient.client().modules().setEnabled(module, true);
                    }
                    module.setCape(Cosmetics.CAPES[i]);
                    NightshadeClient.client().config().saveDirty();
                }
                return true;
            }
        }
        return false;
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(parent);
    }
}
