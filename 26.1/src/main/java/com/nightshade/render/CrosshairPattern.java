package com.nightshade.render;

import com.nightshade.module.visual.CustomCrosshair;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.BiPredicate;

/**
 * A pixel crosshair on a {@value #SIZE}x{@value #SIZE} grid. The middle cell is the screen centre. Patterns are stored
 * as hex (one bit per cell) and shared as codes like {@code NSC1-<pattern>-<AARRGGBB>-<outline>}.
 */
public final class CrosshairPattern {
    public static final int SIZE = 15;
    public static final int HALF = SIZE / 2;
    private static final String CODE_PREFIX = "NSC1";

    private final boolean[] cells;

    private CrosshairPattern(boolean[] cells) {
        this.cells = cells;
    }

    public static CrosshairPattern empty() {
        return new CrosshairPattern(new boolean[SIZE * SIZE]);
    }

    public CrosshairPattern copy() {
        return new CrosshairPattern(cells.clone());
    }

    public boolean get(int x, int y) {
        return x >= 0 && y >= 0 && x < SIZE && y < SIZE && cells[y * SIZE + x];
    }

    public void set(int x, int y, boolean on) {
        if (x >= 0 && y >= 0 && x < SIZE && y < SIZE) {
            cells[y * SIZE + x] = on;
        }
    }

    public boolean isEmpty() {
        for (boolean c : cells) {
            if (c) {
                return false;
            }
        }
        return true;
    }

    public int count() {
        int n = 0;
        for (boolean c : cells) {
            if (c) {
                n++;
            }
        }
        return n;
    }

    /** Moves every cell by (dx, dy); cells pushed off the grid are lost. */
    public CrosshairPattern shifted(int dx, int dy) {
        CrosshairPattern out = empty();
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                if (get(x, y)) {
                    out.set(x + dx, y + dy, true);
                }
            }
        }
        return out;
    }

    public CrosshairPattern inverted() {
        CrosshairPattern out = empty();
        for (int i = 0; i < cells.length; i++) {
            out.cells[i] = !cells[i];
        }
        return out;
    }

    // ------------------------------------------------------------------ encoding

    public String toHex() {
        byte[] bytes = new byte[(cells.length + 7) / 8];
        for (int i = 0; i < cells.length; i++) {
            if (cells[i]) {
                bytes[i >> 3] |= (byte) (0x80 >>> (i & 7));
            }
        }
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b & 0xFF));
        }
        return sb.toString();
    }

    /** Parses {@link #toHex()} output; returns null when the text is not a valid pattern. */
    public static CrosshairPattern fromHex(String hex) {
        int needed = (SIZE * SIZE + 7) / 8 * 2;
        if (hex == null || hex.length() != needed || !hex.matches("[0-9a-fA-F]+")) {
            return null;
        }
        boolean[] cells = new boolean[SIZE * SIZE];
        for (int i = 0; i < cells.length; i++) {
            int b = Integer.parseInt(hex.substring((i >> 3) * 2, (i >> 3) * 2 + 2), 16);
            cells[i] = (b & (0x80 >>> (i & 7))) != 0;
        }
        return new CrosshairPattern(cells);
    }

    /** A share code holding the pattern, colour and outline. */
    public String toCode(int argb, boolean outline) {
        return CODE_PREFIX + "-" + toHex() + "-" + String.format("%08X", argb) + "-" + (outline ? "1" : "0");
    }

    /** What a pasted share code holds. */
    public record Code(CrosshairPattern pattern, int argb, boolean outline) {
    }

    /** Parses a share code; returns null when it is not one. Spaces and line breaks around it are ignored. */
    public static Code parseCode(String text) {
        if (text == null) {
            return null;
        }
        String[] parts = text.trim().split("-");
        if (parts.length != 4 || !parts[0].equalsIgnoreCase(CODE_PREFIX) || !parts[2].matches("[0-9a-fA-F]{8}") || !parts[3].matches("[01]")) {
            return null;
        }
        CrosshairPattern pattern = fromHex(parts[1].toLowerCase(Locale.ROOT));
        if (pattern == null) {
            return null;
        }
        return new Code(pattern, (int) Long.parseLong(parts[2], 16), parts[3].equals("1"));
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof CrosshairPattern p && Arrays.equals(cells, p.cells);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(cells);
    }

    // ------------------------------------------------------------------ presets

    private static CrosshairPattern of(BiPredicate<Integer, Integer> cell) {
        CrosshairPattern p = empty();
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                if (cell.test(x - HALF, y - HALF)) {
                    p.set(x, y, true);
                }
            }
        }
        return p;
    }

    private static boolean arm(int a, int b, int from, int to) {
        return a == 0 && Math.abs(b) >= from && Math.abs(b) <= to;
    }

    /** Built-in starting points, in display order. */
    public static final Map<String, CrosshairPattern> PRESETS = new LinkedHashMap<>();

    static {
        PRESETS.put("Classic", of((x, y) -> arm(x, y, 0, 4) || arm(y, x, 0, 4)));
        PRESETS.put("Gap Cross", of((x, y) -> arm(x, y, 2, 5) || arm(y, x, 2, 5)));
        PRESETS.put("Cross + Dot", of((x, y) -> arm(x, y, 2, 5) || arm(y, x, 2, 5) || (x == 0 && y == 0)));
        PRESETS.put("Dot", of((x, y) -> x == 0 && y == 0));
        PRESETS.put("Big Dot", of((x, y) -> Math.abs(x) <= 1 && Math.abs(y) <= 1 && !(Math.abs(x) == 1 && Math.abs(y) == 1)));
        PRESETS.put("Circle", of((x, y) -> {
            double d = Math.sqrt(x * x + y * y);
            return Math.abs(d - 4) < 0.55 || (x == 0 && y == 0);
        }));
        PRESETS.put("T-Shape", of((x, y) -> (x == 0 && y >= 2 && y <= 5) || arm(y, x, 2, 5)));
        PRESETS.put("X", of((x, y) -> Math.abs(x) == Math.abs(y) && Math.abs(x) >= 2 && Math.abs(x) <= 4));
        PRESETS.put("Square", of((x, y) -> Math.max(Math.abs(x), Math.abs(y)) == 4));
        PRESETS.put("Brackets", of((x, y) -> (Math.abs(x) == 4 && Math.abs(y) <= 4 && Math.abs(y) >= 2)
                || (Math.abs(y) == 4 && Math.abs(x) <= 4 && Math.abs(x) >= 3)));
        PRESETS.put("Chevron", of((x, y) -> y >= 1 && y <= 4 && Math.abs(x) == y));
        PRESETS.put("Diamond", of((x, y) -> Math.abs(x) + Math.abs(y) == 4 || (x == 0 && y == 0)));
    }

    public static CrosshairPattern preset(String name) {
        CrosshairPattern p = PRESETS.get(name);
        return p == null ? empty() : p.copy();
    }

    // ------------------------------------------------------------------ drawing

    /**
     * Draws the pattern centred on (cx, cy) in GUI coordinates with each cell {@code pixel} real screen pixels wide, so
     * it stays sharp at any GUI scale. The outline is a 1-pixel border around the filled cells.
     */
    public void draw(net.minecraft.client.gui.GuiGraphicsExtractor ctx, float cx, float cy, int pixel, int color, boolean outline, int outlineColor) {
        float gs = CustomCrosshair.guiScale();
        ctx.pose().pushMatrix();
        ctx.pose().translate(cx, cy);
        ctx.pose().scale(1f / gs, 1f / gs);
        int origin = -HALF * pixel - pixel / 2;
        if (outline) {
            for (int y = 0; y < SIZE; y++) {
                for (int x = 0; x < SIZE; x++) {
                    if (get(x, y)) {
                        int px = origin + x * pixel;
                        int py = origin + y * pixel;
                        RenderUtil.rect(ctx, px - 1, py - 1, px + pixel + 1, py + pixel + 1, outlineColor);
                    }
                }
            }
        }
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                if (get(x, y)) {
                    int px = origin + x * pixel;
                    int py = origin + y * pixel;
                    RenderUtil.rect(ctx, px, py, px + pixel, py + pixel, color);
                }
            }
        }
        ctx.pose().popMatrix();
    }
}
