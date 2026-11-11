package com.nightshade.render;

import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;

/**
 * Typography helper. Draws with the font chosen in ClickGUI settings: the crisp vanilla Minecraft font (default)
 * or the bundled Rubik TTF (assets/nightshade/font/rubik.json), which falls back to vanilla for missing glyphs. Styled {@link Component} instances are cached in a small LRU map so per-frame HUD rendering
 * does not allocate new text objects for unchanged strings.
 */
public final class Fonts {
    public static final String[] CHOICES = {"Minecraft", "Inter", "Poppins", "Rounded"};
    private static final Style ROUNDED_STYLE = Style.EMPTY.withFont(new FontDescription.Resource(Identifier.fromNamespaceAndPath("nightshade", "rubik")));
    /** Inter and Poppins (both SIL Open Font License, see assets/nightshade/licenses): the clean sans look of modern launchers. */
    private static final Style INTER_STYLE = Style.EMPTY.withFont(new FontDescription.Resource(Identifier.fromNamespaceAndPath("nightshade", "inter")));
    private static final Style POPPINS_STYLE = Style.EMPTY.withFont(new FontDescription.Resource(Identifier.fromNamespaceAndPath("nightshade", "poppins")));
    private static final int CACHE_SIZE = 512;
    private static final Map<String, Component> CACHE = new LinkedHashMap<>(CACHE_SIZE, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Component> eldest) {
            return size() > CACHE_SIZE;
        }
    };

    private Fonts() {
    }

    private static Font renderer() {
        return Minecraft.getInstance().font;
    }

    public static void clearCache() {
        synchronized (CACHE) {
            CACHE.clear();
        }
    }

    public static Component text(String string) {
        synchronized (CACHE) {
            Component cached = CACHE.get(string);
            if (cached == null) {
                MutableComponent literal = Component.literal(string);
                cached = switch (Theme.font()) {
                    case "Rounded" -> literal.setStyle(ROUNDED_STYLE);
                    case "Inter" -> literal.setStyle(INTER_STYLE);
                    case "Poppins" -> literal.setStyle(POPPINS_STYLE);
                    default -> literal;
                };
                CACHE.put(string, cached);
            }
            return cached;
        }
    }

    public static int width(String string) {
        return renderer().width(text(string));
    }

    public static int height() {
        return renderer().lineHeight;
    }

    public static int draw(GuiGraphics context, String string, int x, int y, int color, boolean shadow) {
        if (ColorUtil.alpha(color) < 8) {
            return x;
        }
        Component text = text(string);
        context.drawString(renderer(), text, x, y, color, shadow);
        return x + renderer().width(text) + (shadow ? 1 : 0);
    }

    public static void drawCentered(GuiGraphics context, String string, int centerX, int y, int color, boolean shadow) {
        draw(context, string, centerX - width(string) / 2, y, color, shadow);
    }

    /** Trims a string to fit the given pixel width, appending an ellipsis when shortened. */
    public static String trim(String string, int maxWidth) {
        if (width(string) <= maxWidth) {
            return string;
        }
        String ellipsis = "...";
        int ellipsisWidth = width(ellipsis);
        int end = string.length();
        while (end > 0 && width(string.substring(0, end)) + ellipsisWidth > maxWidth) {
            end--;
        }
        return string.substring(0, end) + ellipsis;
    }
}
