package com.nightshade.module.setting;

import com.nightshade.module.ModuleSetting;
import com.nightshade.render.ColorUtil;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

import java.util.Locale;

/** ARGB colour setting, stored in JSON as "#AARRGGBB". */
public final class ColorSetting extends ModuleSetting<Integer> {
    public ColorSetting(String name, String description, int defaultArgb) {
        super(name, description, defaultArgb);
    }

    /** The colour; an untouched violet default follows the client colour (Theme), a colour you picked is kept. */
    public int argb() {
        return value.equals(getDefault()) ? com.nightshade.render.Theme.tint(value) : value;
    }

    public int alpha() {
        return (value >>> 24) & 0xFF;
    }

    public float[] hsb() {
        return ColorUtil.rgbToHsb((value >> 16) & 0xFF, (value >> 8) & 0xFF, value & 0xFF, new float[3]);
    }

    public void setHsba(float hue, float saturation, float brightness, int alpha) {
        int rgb = ColorUtil.hsbToRgb(hue, saturation, brightness) & 0xFFFFFF;
        set((Math.max(0, Math.min(255, alpha)) << 24) | rgb);
    }

    public String toHex() {
        return String.format(Locale.ROOT, "#%08X", value);
    }

    public static Integer parseHex(String text) {
        if (text == null) {
            return null;
        }
        String hex = text.trim();
        if (hex.startsWith("#")) {
            hex = hex.substring(1);
        }
        try {
            if (hex.length() == 6) {
                return 0xFF000000 | Integer.parseUnsignedInt(hex, 16);
            }
            if (hex.length() == 8) {
                return (int) Long.parseLong(hex, 16);
            }
        } catch (NumberFormatException ignored) {
            return null;
        }
        return null;
    }

    @Override
    protected Integer validate(Integer input) {
        return input;
    }

    @Override
    public JsonElement toJson() {
        return new JsonPrimitive(toHex());
    }

    @Override
    public boolean fromJson(JsonElement element) {
        if (element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
            Integer parsed = parseHex(element.getAsString());
            if (parsed != null) {
                load(parsed);
                return true;
            }
        }
        return false;
    }
}
