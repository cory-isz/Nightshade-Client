package com.nightshade.render;

/**
 * Global palette used by the click GUI, HUD editor and HUD defaults.
 * Inspired by the Nightshade dark-violet UI style. The accent and font are user configurable via the ClickGUI
 * module; everything else is derived from the base palette.
 */
public final class Theme {
    // Base Nightshade (violet) values; the live fields below are these recoloured into the chosen client colour.
    private static final int BASE_BACKGROUND = 0xE60E081A;
    private static final int BASE_SIDEBAR = 0xF0140B24;
    private static final int BASE_PANEL = 0xC81A102E;
    private static final int BASE_PANEL_HOVER = 0xD8231740;
    private static final int BASE_SELECTED = 0xFF362260;
    private static final int BASE_SELECTED_HOVER = 0xFF3A2668;
    private static final int BASE_TEXT_MUTED = 0xFFA497C4;
    private static final int BASE_TEXT_DIM = 0xFF6E5A9E;
    private static final int BASE_SWITCH_OFF = 0xFF2A1E45;
    private static final int BASE_SCREEN_DIM = 0x88050210;

    public static int BACKGROUND = BASE_BACKGROUND;
    public static int SIDEBAR = BASE_SIDEBAR;
    public static int PANEL = BASE_PANEL;
    public static int PANEL_HOVER = BASE_PANEL_HOVER;
    public static int SELECTED = BASE_SELECTED;
    public static int SELECTED_HOVER = BASE_SELECTED_HOVER;
    public static final int BORDER = 0x26FFFFFF;
    public static final int BORDER_STRONG = 0x40FFFFFF;
    public static final int TEXT = 0xFFE2E6F0;
    public static int TEXT_MUTED = BASE_TEXT_MUTED;
    public static int TEXT_DIM = BASE_TEXT_DIM;
    public static final int SUCCESS = 0xFF5BE49B;
    public static final int WARNING = 0xFFFFC857;
    public static final int DANGER = 0xFFFF5C7A;
    public static int SWITCH_OFF = BASE_SWITCH_OFF;
    public static int SCREEN_DIM = BASE_SCREEN_DIM;

    /** Hue of the Nightshade violet the client was designed in; tint() rotates colours from it to the chosen one. */
    private static final float BASE_HUE = 0.69f;
    private static volatile float targetHue = BASE_HUE;
    private static volatile float saturationScale = 1f;
    private static volatile boolean identity = true;
    private static final java.util.concurrent.ConcurrentHashMap<Integer, Integer> TINT_CACHE = new java.util.concurrent.ConcurrentHashMap<>();
    private static volatile MenuBackground.Palette palette = MenuBackground.Palette.NIGHTSHADE;

    private static volatile int accent = 0xFF7C6CFF;
    private static volatile int accentLight = 0xFFB794FF;
    /** "Minecraft" (vanilla font) or "Rounded" (Rubik). */
    private static volatile String font = "Inter";
    private static volatile float animationSpeed = 1.0f;

    private Theme() {
    }

    public static int accent() {
        return accent;
    }

    public static int accentLight() {
        return accentLight;
    }

    public static void setAccent(int argb) {
        accent = argb | 0xFF000000;
        accentLight = ColorUtil.lerp(accent, 0xFFFFFFFF, 0.35f);
    }

    /** The chosen client colour palette (menus, sky, GUI, HUD). */
    public static MenuBackground.Palette palette() {
        return palette;
    }

    /**
     * Applies a client colour: the accent becomes {@code accentArgb} and every themed colour in the client is
     * recoloured to its hue (see {@link #tint}).
     */
    public static void applyPalette(MenuBackground.Palette newPalette, int accentArgb) {
        palette = newPalette;
        setAccent(accentArgb);
        float[] hsb = ColorUtil.rgbToHsb(ColorUtil.red(accent), ColorUtil.green(accent), ColorUtil.blue(accent), null);
        targetHue = hsb[0];
        saturationScale = Math.min(1.6f, hsb[1] / 0.58f); // the violet accent has ~0.58 saturation
        identity = Math.abs(targetHue - BASE_HUE) < 0.004f && Math.abs(saturationScale - 1f) < 0.02f;
        TINT_CACHE.clear();
        BACKGROUND = tint(BASE_BACKGROUND);
        SIDEBAR = tint(BASE_SIDEBAR);
        PANEL = tint(BASE_PANEL);
        PANEL_HOVER = tint(BASE_PANEL_HOVER);
        SELECTED = tint(BASE_SELECTED);
        SELECTED_HOVER = tint(BASE_SELECTED_HOVER);
        TEXT_MUTED = tint(BASE_TEXT_MUTED);
        TEXT_DIM = tint(BASE_TEXT_DIM);
        SWITCH_OFF = tint(BASE_SWITCH_OFF);
        SCREEN_DIM = tint(BASE_SCREEN_DIM);
    }

    /**
     * Recolours a colour designed in the Nightshade violet into the chosen client colour: coloured pixels keep their
     * brightness and their hue offset from the violet, greys stay grey. Alpha is preserved. Cached.
     */
    public static int tint(int argb) {
        if (identity) {
            return argb;
        }
        Integer cached = TINT_CACHE.get(argb);
        if (cached != null) {
            return cached;
        }
        float[] hsb = ColorUtil.rgbToHsb(ColorUtil.red(argb), ColorUtil.green(argb), ColorUtil.blue(argb), null);
        int out = argb;
        // Only the violet family the client was designed in is recoloured; reds, greens, yellows (health, danger,
        // success...) keep their meaning.
        if (hsb[1] > 0.06f && hsb[0] >= 0.58f && hsb[0] <= 0.88f) {
            float hue = targetHue + (hsb[0] - BASE_HUE);
            hue -= (float) Math.floor(hue);
            float sat = Math.min(1f, hsb[1] * saturationScale);
            out = (argb & 0xFF000000) | (ColorUtil.hsbToRgb(hue, sat, hsb[2]) & 0xFFFFFF);
        }
        if (TINT_CACHE.size() < 4096) {
            TINT_CACHE.put(argb, out);
        }
        return out;
    }

    private static volatile String hudStyle = "Accent";
    private static volatile boolean universalFont = true;

    /** Whether the client font replaces Minecraft's font everywhere (ClickGUI > Universal Font). */
    public static boolean universalFont() {
        return universalFont;
    }

    public static void setUniversalFont(boolean value) {
        universalFont = value;
    }

    /** "Accent", "Glass", "Minimal" or "Classic" (ClickGUI > HUD Style). */
    public static String hudStyle() {
        return hudStyle;
    }

    public static void setHudStyle(String value) {
        hudStyle = value == null ? "Accent" : value;
    }

    public static String font() {
        return font;
    }

    public static void setFont(String value) {
        if (!font.equals(value)) {
            font = value;
            Fonts.clearCache();
        }
    }

    public static float animationSpeed() {
        return animationSpeed;
    }

    public static void setAnimationSpeed(float speed) {
        animationSpeed = Math.max(0.1f, speed);
    }
}
