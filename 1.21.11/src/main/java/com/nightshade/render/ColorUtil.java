package com.nightshade.render;

/** Allocation-free ARGB colour helpers. */
public final class ColorUtil {
    private ColorUtil() {
    }

    public static int alpha(int argb) {
        return (argb >>> 24) & 0xFF;
    }

    public static int red(int argb) {
        return (argb >> 16) & 0xFF;
    }

    public static int green(int argb) {
        return (argb >> 8) & 0xFF;
    }

    public static int blue(int argb) {
        return argb & 0xFF;
    }

    public static int argb(int a, int r, int g, int b) {
        return (clamp(a) << 24) | (clamp(r) << 16) | (clamp(g) << 8) | clamp(b);
    }

    public static int withAlpha(int argb, int alpha) {
        return (clamp(alpha) << 24) | (argb & 0xFFFFFF);
    }

    /** Multiplies the existing alpha by a 0..1 factor. */
    public static int fade(int argb, float factor) {
        return withAlpha(argb, Math.round(alpha(argb) * Math.max(0f, Math.min(1f, factor))));
    }

    public static int lerp(int from, int to, float t) {
        float f = Math.max(0f, Math.min(1f, t));
        return argb(
                Math.round(alpha(from) + (alpha(to) - alpha(from)) * f),
                Math.round(red(from) + (red(to) - red(from)) * f),
                Math.round(green(from) + (green(to) - green(from)) * f),
                Math.round(blue(from) + (blue(to) - blue(from)) * f));
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(255, v));
    }

    /** Converts RGB to HSB; writes into {@code out} (length 3) and returns it. */
    public static float[] rgbToHsb(int r, int g, int b, float[] out) {
        int max = Math.max(r, Math.max(g, b));
        int min = Math.min(r, Math.min(g, b));
        float brightness = max / 255f;
        float saturation = max == 0 ? 0f : (max - min) / (float) max;
        float hue;
        if (saturation == 0f) {
            hue = 0f;
        } else {
            float delta = max - min;
            float rc = (max - r) / delta;
            float gc = (max - g) / delta;
            float bc = (max - b) / delta;
            if (r == max) {
                hue = bc - gc;
            } else if (g == max) {
                hue = 2f + rc - bc;
            } else {
                hue = 4f + gc - rc;
            }
            hue /= 6f;
            if (hue < 0f) {
                hue += 1f;
            }
        }
        if (out == null) {
            out = new float[3];
        }
        out[0] = hue;
        out[1] = saturation;
        out[2] = brightness;
        return out;
    }

    /** Converts HSB to an opaque RGB int (0xFFRRGGBB). */
    public static int hsbToRgb(float hue, float saturation, float brightness) {
        int r = 0;
        int g = 0;
        int b = 0;
        if (saturation == 0f) {
            r = g = b = Math.round(brightness * 255f);
        } else {
            float h = (hue - (float) Math.floor(hue)) * 6f;
            float f = h - (float) Math.floor(h);
            float p = brightness * (1f - saturation);
            float q = brightness * (1f - saturation * f);
            float t = brightness * (1f - saturation * (1f - f));
            switch ((int) h) {
                case 0 -> {
                    r = Math.round(brightness * 255f);
                    g = Math.round(t * 255f);
                    b = Math.round(p * 255f);
                }
                case 1 -> {
                    r = Math.round(q * 255f);
                    g = Math.round(brightness * 255f);
                    b = Math.round(p * 255f);
                }
                case 2 -> {
                    r = Math.round(p * 255f);
                    g = Math.round(brightness * 255f);
                    b = Math.round(t * 255f);
                }
                case 3 -> {
                    r = Math.round(p * 255f);
                    g = Math.round(q * 255f);
                    b = Math.round(brightness * 255f);
                }
                case 4 -> {
                    r = Math.round(t * 255f);
                    g = Math.round(p * 255f);
                    b = Math.round(brightness * 255f);
                }
                default -> {
                    r = Math.round(brightness * 255f);
                    g = Math.round(p * 255f);
                    b = Math.round(q * 255f);
                }
            }
        }
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }
}
