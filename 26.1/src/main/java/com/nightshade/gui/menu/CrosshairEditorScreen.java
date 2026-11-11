package com.nightshade.gui.menu;

import com.nightshade.NightshadeClient;
import com.nightshade.gui.widget.NsButton;
import com.nightshade.module.interfaces.Notifications;
import com.nightshade.module.visual.CustomCrosshair;
import com.nightshade.render.ColorUtil;
import com.nightshade.render.Credits;
import com.nightshade.render.CrosshairPattern;
import com.nightshade.render.Fonts;
import com.nightshade.render.Fx;
import com.nightshade.render.RenderUtil;
import com.nightshade.render.Theme;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;

/**
 * Crosshair Editor: draw a pixel crosshair on a 15x15 canvas (left click draws, right click erases), start from a
 * preset, mirror while drawing, and share it as a code. A live preview shows it at real size on three backgrounds.
 */
public final class CrosshairEditorScreen extends NsMenuScreen {
    private static final String[] MIRRORS = {"Off", "Left / Right", "All 4 Sides"};
    private static final int[] SWATCHES = {0xFFFFFFFF, 0xFF000000, 0xFFFF5C7A, 0xFF5BE49B, 0xFF4FD8FF, 0xFFFFE35C, 0xFFB794FF, 0xFFFF8AD8};
    private static final int[][] BACKDROPS = {{0xFF7FB4E6, 0xFFC4DDF2}, {0xFF5D8A36, 0xFF34521F}, {0xFF1A1A24, 0xFF0B0B10}};

    @Nullable
    private final Screen parent;
    private CrosshairPattern pattern;
    private final Deque<CrosshairPattern> undo = new ArrayDeque<>();
    private int mirror = 1;
    private boolean painting;
    private boolean paintValue;
    private String status = "";
    private long statusUntil;

    private final NsButton mirrorButton = new NsButton("", NsButton.Style.SECONDARY, () -> mirror = (mirror + 1) % MIRRORS.length);
    private final NsButton clear = new NsButton("Clear", NsButton.Style.SECONDARY, () -> apply(CrosshairPattern.empty()));
    private final NsButton invert = new NsButton("Invert", NsButton.Style.SECONDARY, () -> apply(pattern.inverted()));
    private final NsButton undoButton = new NsButton("Undo", NsButton.Style.SECONDARY, this::undo);
    private final NsButton outlineButton = new NsButton("", NsButton.Style.SECONDARY, this::toggleOutline);
    private final NsButton smaller = new NsButton("-", NsButton.Style.SECONDARY, () -> changePixel(-1));
    private final NsButton bigger = new NsButton("+", NsButton.Style.SECONDARY, () -> changePixel(1));
    private final NsButton copy = new NsButton("Copy Code", NsButton.Style.SECONDARY, this::copyCode);
    private final NsButton paste = new NsButton("Paste Code", NsButton.Style.SECONDARY, this::pasteCode);
    private final NsButton done = new NsButton("Done", NsButton.Style.PRIMARY, this::onClose);

    // layout from the last frame, for hit-testing
    private int canvasX, canvasY, cell;
    private final int[][] presetRects = new int[CrosshairPattern.PRESETS.size()][4];
    private final int[][] swatchRects = new int[SWATCHES.length][4];

    public CrosshairEditorScreen(@Nullable Screen parent) {
        super(Component.literal("Crosshair Editor"));
        this.parent = parent;
        CustomCrosshair module = module();
        pattern = module == null ? CrosshairPattern.preset("Gap Cross") : module.pattern();
    }

    @Nullable
    private static CustomCrosshair module() {
        return NightshadeClient.client().modules().get(CustomCrosshair.class);
    }

    // ------------------------------------------------------------------ editing

    /** Replaces the pattern (with undo) and saves it to Custom Crosshair, switching it to the Custom style. */
    private void apply(CrosshairPattern next) {
        if (next.equals(pattern)) {
            return;
        }
        pushUndo();
        commit(next);
    }

    private void pushUndo() {
        undo.push(pattern.copy());
        while (undo.size() > 64) {
            undo.removeLast();
        }
    }

    private void commit(CrosshairPattern next) {
        pattern = next;
        CustomCrosshair module = module();
        if (module != null) {
            if (!module.isEnabled()) {
                NightshadeClient.client().modules().setEnabled(module, true);
            }
            module.setPattern(next);
        }
    }

    private void undo() {
        if (!undo.isEmpty()) {
            commit(undo.pop());
        }
    }

    private void paint(int gx, int gy) {
        CrosshairPattern next = pattern.copy();
        int mx = CrosshairPattern.SIZE - 1 - gx;
        int my = CrosshairPattern.SIZE - 1 - gy;
        next.set(gx, gy, paintValue);
        if (mirror >= 1) {
            next.set(mx, gy, paintValue);
        }
        if (mirror >= 2) {
            next.set(gx, my, paintValue);
            next.set(mx, my, paintValue);
        }
        if (!next.equals(pattern)) {
            commit(next);
        }
    }

    private void toggleOutline() {
        CustomCrosshair module = module();
        if (module != null) {
            module.outlineSetting().toggle();
        }
    }

    private void changePixel(int delta) {
        CustomCrosshair module = module();
        if (module != null) {
            module.pixelSizeSetting().set((double) Math.max(1, Math.min(6, module.pixelSizeSetting().intValue() + delta)));
        }
    }

    private void copyCode() {
        CustomCrosshair module = module();
        int color = module == null ? 0xFFFFFFFF : module.colorSetting().get();
        boolean outline = module != null && module.outlineSetting().get();
        minecraft.keyboardHandler.setClipboard(pattern.toCode(color, outline));
        flash("Code copied - share it with your friends");
    }

    private void pasteCode() {
        CrosshairPattern.Code code = CrosshairPattern.parseCode(minecraft.keyboardHandler.getClipboard());
        if (code == null) {
            flash("Your clipboard doesn't hold a crosshair code");
            return;
        }
        apply(code.pattern());
        CustomCrosshair module = module();
        if (module != null) {
            module.colorSetting().set(code.argb());
            module.outlineSetting().set(code.outline());
        }
        flash("Crosshair pasted");
    }

    private void flash(String message) {
        status = message;
        statusUntil = System.currentTimeMillis() + 2500;
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        CustomCrosshair module = module();
        int color = module == null ? 0xFFFFFFFF : module.colorSetting().argb();
        boolean outline = module != null && module.outlineSetting().get();
        int outlineColor = module == null ? 0xC0000000 : module.outlineColorSetting().argb();
        int pixel = module == null ? 2 : module.pixelSizeSetting().intValue();

        int w = Math.min(width - 24, 580);
        int h = Math.min(height - 24, 330);
        int px1 = (width - w) / 2;
        int py1 = (height - h) / 2;
        int px2 = px1 + w;
        int py2 = py1 + h;
        Fx.shadow(ctx, px1, py1 + 3, px2, py2 + 3, 12, 0xA0000000);
        RenderUtil.roundedRect(ctx, px1, py1, px2, py2, 8, ColorUtil.withAlpha(Theme.BACKGROUND, 0xD8));
        RenderUtil.roundedOutline(ctx, px1, py1, px2, py2, 8, Theme.BORDER);
        int mid = (px1 + px2) / 2;
        RenderUtil.horizontalGradient(ctx, px1 + 8, py1, mid, py1 + 1, 0x00000000, ColorUtil.withAlpha(Theme.accentLight(), 0xB0));
        RenderUtil.horizontalGradient(ctx, mid, py1, px2 - 8, py1 + 1, ColorUtil.withAlpha(Theme.accentLight(), 0xB0), 0x00000000);
        Fonts.draw(ctx, "Crosshair Editor", px1 + 14, py1 + 11, Theme.TEXT, false);
        Fonts.draw(ctx, "Left click draws, right click erases. Arrow keys move it, Ctrl+Z undoes.", px1 + 14, py1 + 22, Theme.TEXT_DIM, false);

        // ---- canvas
        int side = Math.min(h - 96, 15 * 14);
        cell = Math.max(6, side / CrosshairPattern.SIZE);
        side = cell * CrosshairPattern.SIZE;
        canvasX = px1 + 14;
        canvasY = py1 + 40;
        RenderUtil.roundedRect(ctx, canvasX - 4, canvasY - 4, canvasX + side + 4, canvasY + side + 4, 5, 0x50000000);
        for (int gy = 0; gy < CrosshairPattern.SIZE; gy++) {
            for (int gx = 0; gx < CrosshairPattern.SIZE; gx++) {
                int x = canvasX + gx * cell;
                int y = canvasY + gy * cell;
                boolean centre = gx == CrosshairPattern.HALF || gy == CrosshairPattern.HALF;
                int bg = ((gx + gy) & 1) == 0 ? 0xFF201C2C : 0xFF1A1724;
                if (centre) {
                    bg = ColorUtil.lerp(bg, Theme.accent(), 0.16f);
                }
                RenderUtil.rect(ctx, x, y, x + cell, y + cell, bg);
                if (pattern.get(gx, gy)) {
                    RenderUtil.rect(ctx, x + 1, y + 1, x + cell - 1, y + cell - 1, ColorUtil.withAlpha(color, 0xFF));
                }
            }
        }
        int hx = (mouseX - canvasX) / Math.max(1, cell);
        int hy = (mouseY - canvasY) / Math.max(1, cell);
        if (mouseX >= canvasX && mouseY >= canvasY && hx < CrosshairPattern.SIZE && hy < CrosshairPattern.SIZE) {
            int x = canvasX + hx * cell;
            int y = canvasY + hy * cell;
            RenderUtil.roundedOutline(ctx, x, y, x + cell, y + cell, 1, ColorUtil.withAlpha(Theme.accentLight(), 0xE0));
        }
        int cxm = canvasX + CrosshairPattern.HALF * cell + cell / 2;
        int cym = canvasY + CrosshairPattern.HALF * cell + cell / 2;
        RenderUtil.roundedOutline(ctx, cxm - cell / 2 - 1, cym - cell / 2 - 1, cxm + cell / 2 + 2, cym + cell / 2 + 2, 1, 0x60FFFFFF);

        // ---- canvas tools
        int ty = canvasY + side + 10;
        mirrorButton.label("Mirror: " + MIRRORS[mirror]);
        int mw = Math.max(92, mirrorButton.preferredWidth());
        mirrorButton.render(ctx, canvasX - 4, ty, mw, 17, mouseX, mouseY);
        int tx = canvasX - 4 + mw + 4;
        int rest = (canvasX + side + 4 - tx - 8) / 3;
        clear.render(ctx, tx, ty, rest, 17, mouseX, mouseY);
        invert.render(ctx, tx + rest + 4, ty, rest, 17, mouseX, mouseY);
        undoButton.enabled(!undo.isEmpty()).render(ctx, tx + (rest + 4) * 2, ty, rest, 17, mouseX, mouseY);

        // ---- right column
        int rx1 = canvasX + side + 18;
        int rx2 = px2 - 14;
        int rw = rx2 - rx1;
        int y = py1 + 40;
        Fonts.draw(ctx, "Preview", rx1, y, Theme.TEXT_MUTED, false);
        y += 11;
        int tileW = (rw - 8) / 3;
        int tileH = 54;
        for (int i = 0; i < BACKDROPS.length; i++) {
            int x = rx1 + i * (tileW + 4);
            RenderUtil.roundedRect(ctx, x, y, x + tileW, y + tileH, 4, 0xFF000000);
            Fx.verticalGradient(ctx, x + 1, y + 1, x + tileW - 1, y + tileH - 1, BACKDROPS[i][0], BACKDROPS[i][1]);
            pattern.draw(ctx, x + tileW / 2f, y + tileH / 2f, pixel, color, outline, outlineColor);
        }
        y += tileH + 10;

        Fonts.draw(ctx, "Presets", rx1, y, Theme.TEXT_MUTED, false);
        y += 11;
        int cols = 6;
        int pw = (rw - (cols - 1) * 4) / cols;
        int ph = Math.min(pw, 34);
        int i = 0;
        for (Map.Entry<String, CrosshairPattern> preset : CrosshairPattern.PRESETS.entrySet()) {
            int x = rx1 + (i % cols) * (pw + 4);
            int yy = y + (i / cols) * (ph + 4);
            presetRects[i] = new int[]{x, yy, x + pw, yy + ph};
            boolean hovered = RenderUtil.isHovered(mouseX, mouseY, x, yy, x + pw, yy + ph);
            boolean current = preset.getValue().equals(pattern);
            RenderUtil.roundedRect(ctx, x, yy, x + pw, yy + ph, 4, current ? ColorUtil.withAlpha(Theme.accent(), 0x60) : hovered ? 0x40FFFFFF : 0x30000000);
            RenderUtil.roundedOutline(ctx, x, yy, x + pw, yy + ph, 4, current ? ColorUtil.withAlpha(Theme.accent(), 0xE0) : 0x1CFFFFFF);
            drawMini(ctx, preset.getValue(), x + pw / 2, yy + ph / 2, Math.max(1, Math.min(2, (ph - 4) / CrosshairPattern.SIZE)));
            if (hovered) {
                status = preset.getKey();
                statusUntil = System.currentTimeMillis() + 50;
            }
            i++;
        }
        y += 2 * (ph + 4) + 6;

        Fonts.draw(ctx, "Colour", rx1, y + 4, Theme.TEXT_MUTED, false);
        int sx = rx1 + 42;
        int current = module == null ? 0xFFFFFFFF : module.colorSetting().get();
        for (int s = 0; s < SWATCHES.length; s++) {
            int x = sx + s * 17;
            swatchRects[s] = new int[]{x, y, x + 14, y + 14};
            RenderUtil.roundedRect(ctx, x - 1, y - 1, x + 15, y + 15, 3, current == SWATCHES[s] ? Theme.accentLight() : 0x40FFFFFF);
            RenderUtil.roundedRect(ctx, x, y, x + 14, y + 14, 3, SWATCHES[s]);
        }
        y += 22;
        outlineButton.label("Outline: " + (outline ? "On" : "Off"));
        outlineButton.render(ctx, rx1, y, 84, 17, mouseX, mouseY);
        Fonts.draw(ctx, "Pixel size", rx1 + 96, y + 5, Theme.TEXT_MUTED, false);
        int bx = rx1 + 96 + Fonts.width("Pixel size") + 8;
        smaller.render(ctx, bx, y, 17, 17, mouseX, mouseY);
        Fonts.drawCentered(ctx, String.valueOf(pixel), bx + 17 + 11, y + 5, Theme.TEXT, false);
        bigger.render(ctx, bx + 17 + 22, y, 17, 17, mouseX, mouseY);

        // ---- footer
        int fy = py2 - 25;
        copy.render(ctx, px1 + 14, fy, 80, 17, mouseX, mouseY);
        paste.render(ctx, px1 + 14 + 84, fy, 80, 17, mouseX, mouseY);
        done.render(ctx, px2 - 14 - 80, fy, 80, 17, mouseX, mouseY);
        if (System.currentTimeMillis() < statusUntil) {
            Fonts.draw(ctx, status, px1 + 14 + 172, fy + 5, Theme.accentLight(), false);
        } else {
            Fonts.draw(ctx, pattern.count() + " pixels", px1 + 14 + 172, fy + 5, Theme.TEXT_DIM, false);
        }
        Credits.draw(ctx, width / 2, py2 + 4, 0.6f);
        Notifications.renderIfEnabled(ctx);
    }

    /** A small preset thumbnail in GUI pixels. */
    private static void drawMini(GuiGraphicsExtractor ctx, CrosshairPattern p, int cx, int cy, int px) {
        int origin = -CrosshairPattern.HALF * px - px / 2;
        for (int gy = 0; gy < CrosshairPattern.SIZE; gy++) {
            for (int gx = 0; gx < CrosshairPattern.SIZE; gx++) {
                if (p.get(gx, gy)) {
                    int x = cx + origin + gx * px;
                    int y = cy + origin + gy * px;
                    RenderUtil.rect(ctx, x, y, x + px, y + px, 0xFFFFFFFF);
                }
            }
        }
    }

    // ------------------------------------------------------------------ input

    private int[] canvasCell(double mouseX, double mouseY) {
        if (cell <= 0 || mouseX < canvasX || mouseY < canvasY) {
            return null;
        }
        int gx = (int) ((mouseX - canvasX) / cell);
        int gy = (int) ((mouseY - canvasY) / cell);
        return gx < CrosshairPattern.SIZE && gy < CrosshairPattern.SIZE ? new int[]{gx, gy} : null;
    }

    @Override
    protected boolean onMouseClicked(double mouseX, double mouseY, int button) {
        for (NsButton b : new NsButton[]{mirrorButton, clear, invert, undoButton, outlineButton, smaller, bigger, copy, paste, done}) {
            if (b.click(mouseX, mouseY, button)) {
                return true;
            }
        }
        int[] c = canvasCell(mouseX, mouseY);
        if (c != null && (button == 0 || button == 1)) {
            painting = true;
            paintValue = button == 0;
            pushUndo();
            paint(c[0], c[1]);
            return true;
        }
        if (button != 0) {
            return false;
        }
        int i = 0;
        for (CrosshairPattern preset : CrosshairPattern.PRESETS.values()) {
            int[] r = presetRects[i++];
            if (RenderUtil.isHovered(mouseX, mouseY, r[0], r[1], r[2], r[3])) {
                apply(preset.copy());
                return true;
            }
        }
        for (int s = 0; s < SWATCHES.length; s++) {
            int[] r = swatchRects[s];
            if (RenderUtil.isHovered(mouseX, mouseY, r[0], r[1], r[2], r[3])) {
                CustomCrosshair module = module();
                if (module != null) {
                    module.colorSetting().set(SWATCHES[s]);
                }
                return true;
            }
        }
        return false;
    }

    @Override
    protected boolean onMouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (painting) {
            int[] c = canvasCell(mouseX, mouseY);
            if (c != null) {
                paint(c[0], c[1]);
            }
            return true;
        }
        return false;
    }

    @Override
    protected boolean onMouseReleased(double mouseX, double mouseY, int button) {
        if (painting) {
            painting = false;
            // a click that changed nothing should not leave an undo step
            if (!undo.isEmpty() && undo.peek().equals(pattern)) {
                undo.pop();
            }
            return true;
        }
        return false;
    }

    @Override
    protected boolean onKeyPressed(int keyCode, int scanCode, int modifiers) {
        int dx = keyCode == GLFW.GLFW_KEY_LEFT ? -1 : keyCode == GLFW.GLFW_KEY_RIGHT ? 1 : 0;
        int dy = keyCode == GLFW.GLFW_KEY_UP ? -1 : keyCode == GLFW.GLFW_KEY_DOWN ? 1 : 0;
        if (dx != 0 || dy != 0) {
            apply(pattern.shifted(dx, dy));
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_Z && (modifiers & 2) != 0) {
            undo();
            return true;
        }
        return false;
    }

    @Override
    public void onClose() {
        NightshadeClient.client().config().saveDirty();
        minecraft.setScreen(parent);
    }
}
