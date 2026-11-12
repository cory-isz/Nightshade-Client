package com.nightshade.gui;

import com.nightshade.render.BrandLogo;
import com.nightshade.NightshadeClient;
import com.nightshade.manager.ModuleManager;
import com.nightshade.module.ModuleCategory;
import com.nightshade.render.Animation;
import com.nightshade.render.ColorUtil;
import com.nightshade.render.Fonts;
import com.nightshade.render.PixelIcons;
import com.nightshade.render.RenderUtil;
import com.nightshade.render.Theme;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Left navigation sidebar with the six main category tabs. */
public final class CategoryPanel {
    public static final int WIDTH = 112;
    /** Icon-only width used on narrow windows. */
    public static final int COMPACT_WIDTH = 34;
    private int width = WIDTH;
    private static final int TAB_H = 22;
    private static final int TOP = 38;

    private final ModuleManager modules;
    private final Map<ModuleCategory, Animation> hover = new EnumMap<>(ModuleCategory.class);
    private final Animation selector = new Animation(18f, 0f);
    private int x;
    private int y;

    public CategoryPanel(ModuleManager modules, ModuleCategory initial) {
        this.modules = modules;
        for (ModuleCategory category : ModuleCategory.values()) {
            hover.put(category, new Animation(16f, 0f));
        }
        selector.snap(initial.ordinal());
    }

    public int width() {
        return width;
    }

    public boolean compact() {
        return width == COMPACT_WIDTH;
    }

    public void setCompact(boolean compact) {
        width = compact ? COMPACT_WIDTH : fullWidth();
    }

    /** Wide enough for the longest tab label plus its count badge in the current font (at least {@link #WIDTH}). */
    private static int fullWidth() {
        int label = 0;
        for (ModuleCategory category : ModuleCategory.values()) {
            label = Math.max(label, Fonts.width(category.displayName()));
        }
        int badge = Fonts.width("00") + 6;
        return Math.max(WIDTH, 27 + label + 6 + badge + 12);
    }

    public void render(GuiGraphicsExtractor ctx, int x, int y, int height, ModuleCategory selected, int mouseX, int mouseY) {
        this.x = x;
        this.y = y;
        int WIDTH = this.width;
        RenderUtil.roundedRect(ctx, x, y, x + WIDTH, y + height, 6, Theme.SIDEBAR);
        RenderUtil.rect(ctx, x + WIDTH - 1, y + 6, x + WIDTH, y + height - 6, Theme.BORDER);
        // Brand mark: pixel nightshade flower + wordmark
        if (compact()) {
            BrandLogo.draw(ctx, x + 9, y + 7, 16);
        } else {
            BrandLogo.draw(ctx, x + 5, y + 7, 16);
            Fonts.draw(ctx, "NIGHTSHADE", x + 25, y + 7, Theme.accentLight(), false);
            Fonts.draw(ctx, "CLIENT", x + 25, y + 16, Theme.TEXT_DIM, false);
        }
        RenderUtil.horizontalGradient(ctx, x + 10, y + 29, x + WIDTH - 10, y + 30, ColorUtil.withAlpha(Theme.accent(), 0xA0), 0x00000000);

        selector.setTarget(selected.ordinal());
        float selectorY = y + TOP + selector.update() * TAB_H;
        int sy = Math.round(selectorY);
        RenderUtil.roundedRect(ctx, x + 6, sy, x + WIDTH - 7, sy + TAB_H - 2, 4, Theme.SELECTED);
        RenderUtil.roundedRect(ctx, x + 6, sy + 4, x + 8, sy + TAB_H - 6, 1, Theme.accent());

        for (ModuleCategory category : ModuleCategory.values()) {
            int ty = y + TOP + category.ordinal() * TAB_H;
            boolean hovered = RenderUtil.isHovered(mouseX, mouseY, x + 6, ty, x + WIDTH - 7, ty + TAB_H - 2);
            float h = hover.get(category).setTarget(hovered && category != selected ? 1f : 0f).update();
            if (h > 0.01f) {
                RenderUtil.roundedRect(ctx, x + 6, ty, x + WIDTH - 7, ty + TAB_H - 2, 4, ColorUtil.fade(Theme.PANEL_HOVER, h));
            }
            int color = category == selected ? Theme.TEXT : ColorUtil.lerp(Theme.TEXT_MUTED, Theme.TEXT, h);
            if (!modules.isCategoryEnabled(category)) {
                color = Theme.TEXT_DIM;
            }
            if (compact()) {
                PixelIcons.category(ctx, category, x + 13, ty + 6, color);
                continue;
            }
            PixelIcons.category(ctx, category, x + 15, ty + 6, color);
            Fonts.draw(ctx, category.displayName(), x + 27, ty + 6, color, false);
            int enabled = modules.countEnabled(category);
            if (!modules.isCategoryEnabled(category)) {
                Fonts.draw(ctx, "off", x + WIDTH - 12 - Fonts.width("off"), ty + 6, Theme.TEXT_DIM, false);
                continue;
            }
            if (enabled > 0) {
                String count = Integer.toString(enabled);
                int cw = Fonts.width(count) + 6;
                int cx = x + WIDTH - 12 - cw;
                RenderUtil.roundedRect(ctx, cx, ty + 4, cx + cw, ty + 15, 3, ColorUtil.withAlpha(Theme.accent(), 0x66));
                Fonts.draw(ctx, count, cx + 3, ty + 6, Theme.TEXT, false);
            }
        }
    }

    // ------------------------------------------------------------------ top bar (Modern style)

    public static final int BAR_H = 26;
    private boolean bar;
    private final int[][] tabRects = new int[ModuleCategory.values().length][4];
    private final Animation barSel = new Animation(18f, -1f);

    public boolean isBar() {
        return bar;
    }

    /**
     * Brand on the left, category tabs in the middle (icon + label, or icons only when narrow) with a sliding
     * highlight. {@code rightReserve} keeps the tabs clear of whatever the caller draws on the right.
     */
    public void renderBar(GuiGraphicsExtractor ctx, int x, int y, int w, int rightReserve, ModuleCategory selected, int mouseX, int mouseY) {
        bar = true;
        this.x = x;
        this.y = y;
        BrandLogo.drawMark(ctx, x + 9, y + 5, 16, Theme.accentLight());
        int brandEnd = x + 28;
        String brand = "NIGHTSHADE";
        int brandW = 0;
        for (char c : brand.toCharArray()) {
            brandW += Fonts.width(String.valueOf(c)) + 1;
        }
        ModuleCategory[] cats = ModuleCategory.values();
        int gap = 4;
        int full = 0;
        for (ModuleCategory c : cats) {
            full += 9 + 4 + Fonts.width(c.displayName()) + 12 + gap;
        }
        full -= gap;
        int avail = x + w - rightReserve - 8;
        boolean showBrand = brandEnd + brandW + 12 + full <= avail;
        if (showBrand) {
            int bx = brandEnd;
            for (char c : brand.toCharArray()) {
                String ch = String.valueOf(c);
                Fonts.draw(ctx, ch, bx, y + 10, Theme.TEXT, false);
                bx += Fonts.width(ch) + 1;
            }
            brandEnd = bx + 12;
        }
        boolean labels = brandEnd + full <= avail;
        int total = labels ? full : cats.length * (9 + 12 + gap) - gap;
        int tx = showBrand ? Math.max(brandEnd, x + (w - total) / 2) : brandEnd + 4;
        if (tx + total > avail) {
            tx = Math.max(brandEnd, avail - total);
        }
        for (ModuleCategory c : cats) {
            int tw = labels ? 9 + 4 + Fonts.width(c.displayName()) + 12 : 9 + 12;
            int[] r = tabRects[c.ordinal()];
            r[0] = tx;
            r[1] = y + 5;
            r[2] = tx + tw;
            r[3] = y + BAR_H - 5;
            tx += tw + gap;
        }
        int[] target = tabRects[selected.ordinal()];
        if (barSel.get() < 0) {
            barSel.snap(target[0]);
        }
        float sx = barSel.setTarget(target[0]).update();
        int selW = target[2] - target[0];
        RenderUtil.roundedRect(ctx, Math.round(sx) + selW / 2 - 7, target[3] + 1, Math.round(sx) + selW / 2 + 7, target[3] + 3, 1, Theme.accent());
        for (ModuleCategory c : cats) {
            int[] r = tabRects[c.ordinal()];
            boolean hovered = RenderUtil.isHovered(mouseX, mouseY, r[0], r[1], r[2], r[3]);
            float h = hover.get(c).setTarget(hovered && c != selected ? 1f : 0f).update();
            if (h > 0.01f) {
                int half = Math.round(5 * h);
                int mid = (r[0] + r[2]) / 2;
                ctx.fill(mid - half, r[3] + 1, mid + half, r[3] + 2, ColorUtil.withAlpha(Theme.accentLight(), Math.round(0x90 * h)));
            }
            int color = c == selected ? 0xFFFFFFFF : ColorUtil.lerp(Theme.TEXT_MUTED, Theme.TEXT, h);
            if (!modules.isCategoryEnabled(c)) {
                color = Theme.TEXT_DIM;
            }
            PixelIcons.category(ctx, c, r[0] + 6, r[1] + 4, color);
            if (labels) {
                Fonts.draw(ctx, c.displayName(), r[0] + 19, r[1] + 5, color, false);
            }
        }
        compactBar = !labels;
    }

    private boolean compactBar;

    public int[] tabRect(ModuleCategory category) {
        return tabRects[category.ordinal()];
    }

    /** Whether the bar shows icons only (tooltips then name the tabs). */
    public boolean barIconsOnly() {
        return bar && compactBar;
    }

    /** Version footer at the bottom of the sidebar. */
    public void renderFooter(GuiGraphicsExtractor ctx, int bottom) {
        if (compact()) {
            return;
        }
        String mc = net.minecraft.SharedConstants.getCurrentVersion().name();
        String version = "v" + NightshadeClient.VERSION + "  |  MC " + mc;
        if (Fonts.width(version) > width - 20) {
            version = "v" + NightshadeClient.VERSION + " | " + mc;
        }
        Fonts.draw(ctx, Fonts.trim(version, width - 20), x + 12, bottom - 14, Theme.TEXT_DIM, false);
    }

    /** Returns the clicked category, or null. */
    @Nullable
    public ModuleCategory categoryAt(double mouseX, double mouseY) {
        if (bar) {
            for (ModuleCategory category : ModuleCategory.values()) {
                int[] r = tabRects[category.ordinal()];
                if (RenderUtil.isHovered(mouseX, mouseY, r[0], r[1], r[2], r[3])) {
                    return category;
                }
            }
            return null;
        }
        for (ModuleCategory category : ModuleCategory.values()) {
            int ty = y + TOP + category.ordinal() * TAB_H;
            if (RenderUtil.isHovered(mouseX, mouseY, x + 6, ty, x + width - 7, ty + TAB_H - 2)) {
                return category;
            }
        }
        return null;
    }
}
