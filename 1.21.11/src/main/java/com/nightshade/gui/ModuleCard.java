package com.nightshade.gui;

import com.nightshade.NightshadeClient;
import com.nightshade.module.Module;
import com.nightshade.module.ModuleCategory;
import com.nightshade.render.Animation;
import com.nightshade.render.ColorUtil;
import com.nightshade.render.Fonts;
import com.nightshade.render.RenderUtil;
import com.nightshade.render.Theme;
import net.minecraft.client.gui.GuiGraphics;

/** A module tile: status indicator, name, description, toggle, keybind and settings buttons. */
public final class ModuleCard {
    public enum Action {
        NONE, TOGGLE, OPEN_SETTINGS, BIND, FAVOURITE
    }

    /** Favourite modules are listed first and get a filled star (stored as a config flag). */
    public static boolean isFavourite(Module module) {
        return NightshadeClient.client().config().hasFlag("fav:" + module.getId());
    }

    public static void toggleFavourite(Module module) {
        var config = NightshadeClient.client().config();
        if (isFavourite(module)) {
            config.clearFlag("fav:" + module.getId());
        } else {
            config.setFlag("fav:" + module.getId());
        }
        config.saveDirty();
    }

    private String matchHint;

    /** In search results: the setting that matched the query, shown under the name. */
    public void setMatchHint(String hint) {
        this.matchHint = hint;
    }

    private int starX() {
        return switchX() - 16;
    }

    private int starY() {
        return y + 9;
    }

    /** A 7x7 pixel star. */
    private static void star(GuiGraphics ctx, int x, int y, int color, boolean filled) {
        int[][] rows = {{3, 3}, {3, 3}, {0, 6}, {1, 5}, {1, 5}, {0, 2, 4, 6}, {0, 1, 5, 6}};
        for (int r = 0; r < rows.length; r++) {
            int[] seg = rows[r];
            for (int i = 0; i + 1 < seg.length; i += 2) {
                if (filled || r == 2 || i > 0 || seg[i] == seg[i + 1] || r >= 5) {
                    RenderUtil.rect(ctx, x + seg[i], y + r, x + seg[i + 1] + 1, y + r + 1, color);
                } else {
                    RenderUtil.rect(ctx, x + seg[i], y + r, x + seg[i] + 1, y + r + 1, color);
                    RenderUtil.rect(ctx, x + seg[i + 1], y + r, x + seg[i + 1] + 1, y + r + 1, color);
                }
            }
        }
    }

    private final Module module;
    private final Animation hover = new Animation(16f, 0f);
    private final Animation toggle;
    private boolean searchMode;
    private int x;
    private int y;
    private int w;
    private int h;

    public ModuleCard(Module module) {
        this.module = module;
        this.toggle = new Animation(16f, module.isEnabled() ? 1f : 0f);
    }

    /** Search results show each module's category tag. */
    public void setSearchMode(boolean searchMode) {
        this.searchMode = searchMode;
    }

    public Module module() {
        return module;
    }

    private final Animation bindHover = new Animation(16f, 0f);
    private final Animation gearHover = new Animation(16f, 0f);

    private int switchX() {
        return x + w - GuiDraw.SWITCH_W - 8;
    }

    private int switchY() {
        return y + 8;
    }

    private String bindLabel(boolean binding) {
        if (binding) {
            return "Press a key...";
        }
        String key = module.getKeybind().displayName();
        return key.equalsIgnoreCase("None") ? "+ Bind" : key;
    }

    private int bindX() {
        return x + 8;
    }

    private int bottomY() {
        return y + h - 16;
    }

    private static final String GEAR = "Settings \u203A";

    private int gearX() {
        return x + w - 8 - Fonts.width(GEAR) - 6;
    }

    private int bindW(boolean binding) {
        return Fonts.width(bindLabel(binding)) + 10;
    }

    public void render(GuiGraphics ctx, int x, int y, int w, int h, int mouseX, int mouseY, boolean binding,
                       boolean showDescription, boolean showSubCategory, boolean interactive) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
        boolean hovered = interactive && RenderUtil.isHovered(mouseX, mouseY, x, y, x + w, y + h);
        float hp = hover.setTarget(hovered ? 1f : 0f).update();
        boolean suspended = NightshadeClient.client().modules().isSuspended(module);
        int accent = Theme.accent();
        float on = toggle.setTarget(module.isEnabled() ? 1f : 0f).update();

        // Glass card, a soft accent wash from the left when the module is on.
        if (hp > 0.01f) {
            com.nightshade.render.Fx.glowRect(ctx, x - 14, y - 10, x + w + 14, y + h + 10, ColorUtil.withAlpha(accent, Math.round(0x30 * hp)));
        }
        RenderUtil.roundedRect(ctx, x, y, x + w, y + h, 6, ColorUtil.lerp(ColorUtil.withAlpha(Theme.BACKGROUND, 0xB4),
                ColorUtil.withAlpha(Theme.PANEL_HOVER, 0xE0), hp));
        if (on > 0.01f) {
            RenderUtil.horizontalGradient(ctx, x + 2, y + 2, x + w / 2, y + h - 2, ColorUtil.withAlpha(accent, Math.round(0x34 * on)), 0x00000000);
            RenderUtil.roundedRect(ctx, x, y + 6, x + 2, y + h - 6, 1, ColorUtil.withAlpha(accent, Math.round(255 * on)));
        }
        ctx.fill(x + 6, y + 1, x + w - 6, y + 2, ColorUtil.withAlpha(0xFFFFFF, 0x10 + Math.round(0x14 * hp)));
        RenderUtil.roundedOutline(ctx, x, y, x + w, y + h, 6, ColorUtil.lerp(0x1CFFFFFF, ColorUtil.withAlpha(accent, 0x90), Math.max(hp * 0.6f, on * 0.5f)));

        // Name
        int tx = x + 8;
        boolean favourite = isFavourite(module);
        int nameMax = starX() - tx - 4;
        Fonts.draw(ctx, Fonts.trim(module.getName(), nameMax), tx, y + 7, module.isEnabled() ? 0xFFFFFFFF : Theme.TEXT, false);
        String sub;
        int subColor;
        if (suspended) {
            sub = "Suspended (category off)";
            subColor = Theme.WARNING;
        } else if (matchHint != null) {
            sub = "Setting: " + matchHint;
            subColor = Theme.accentLight();
        } else if (showSubCategory) {
            sub = module.getCategory().displayName();
            subColor = Theme.accentLight();
        } else {
            sub = module.isToggleable() ? (module.isEnabled() ? "On" : "Off") : "Action";
            subColor = module.isEnabled() ? Theme.accentLight() : Theme.TEXT_DIM;
        }
        Fonts.draw(ctx, Fonts.trim(sub, nameMax), tx, y + 17, subColor, false);

        // Favourite star
        boolean starHover = interactive && RenderUtil.isHovered(mouseX, mouseY, starX() - 2, starY() - 2, starX() + 9, starY() + 9);
        if (favourite || hp > 0.01f) {
            int starColor = favourite ? Theme.WARNING : ColorUtil.withAlpha(starHover ? 0xFFFFFFFF : Theme.TEXT_DIM, Math.round(0xFF * Math.max(hp, 0.2f)));
            star(ctx, starX(), starY(), starColor, favourite || starHover);
        }

        // Toggle switch or action button
        if (module.isToggleable()) {
            boolean switchHover = interactive && RenderUtil.isHovered(mouseX, mouseY, switchX(), switchY(), switchX() + GuiDraw.SWITCH_W, switchY() + GuiDraw.SWITCH_H);
            GuiDraw.toggle(ctx, switchX(), switchY(), on, switchHover);
        } else {
            String label = "Open";
            int pw = Fonts.width(label) + 12;
            int px = x + w - 8 - pw;
            boolean oh = interactive && RenderUtil.isHovered(mouseX, mouseY, px, y + 6, px + pw, y + 20);
            RenderUtil.roundedRect(ctx, px, y + 6, px + pw, y + 20, 4, oh ? Theme.accentLight() : accent);
            Fonts.draw(ctx, label, px + 6, y + 9, 0xFFFFFFFF, false);
        }

        if (showDescription && y + 30 + Fonts.height() < bottomY() - 1) {
            Fonts.draw(ctx, Fonts.trim(module.getDescription(), w - 16), x + 8, y + 30, Theme.TEXT_MUTED, false);
        }

        // Keybind chip (left) and settings link (right)
        String bind = bindLabel(binding);
        boolean conflict = !binding && !NightshadeClient.client().keybinds().getConflicts(module).isEmpty();
        boolean bound = !module.getKeybind().displayName().equalsIgnoreCase("None");
        int bw = bindW(binding);
        float bhp = bindHover.setTarget(interactive && RenderUtil.isHovered(mouseX, mouseY, bindX(), bottomY(), bindX() + bw, bottomY() + 11) ? 1f : 0f).update();
        if (bound || binding || bhp > 0.01f) {
            RenderUtil.roundedRect(ctx, bindX(), bottomY(), bindX() + bw, bottomY() + 11, 3,
                    binding ? ColorUtil.withAlpha(accent, 0x90) : ColorUtil.lerp(0x30FFFFFF, ColorUtil.withAlpha(accent, 0x70), bhp));
        }
        int bindColor = binding ? 0xFFFFFFFF : conflict ? Theme.WARNING : bound ? Theme.TEXT : ColorUtil.lerp(Theme.TEXT_DIM, Theme.TEXT, bhp);
        Fonts.draw(ctx, bind, bindX() + 5, bottomY() + 2, bindColor, false);
        float ghp = gearHover.setTarget(interactive && RenderUtil.isHovered(mouseX, mouseY, gearX(), bottomY(), x + w - 6, bottomY() + 11) ? 1f : 0f).update();
        Fonts.draw(ctx, GEAR, gearX() + 3 + Math.round(ghp * 2), bottomY() + 2, ColorUtil.lerp(Theme.TEXT_DIM, Theme.accentLight(), ghp), false);
    }

    public boolean isHovered(double mouseX, double mouseY) {
        return RenderUtil.isHovered(mouseX, mouseY, x, y, x + w, y + h);
    }

    /** Hovering the description returns the full text for a tooltip. */
    public String tooltip(double mouseX, double mouseY) {
        if (!isHovered(mouseX, mouseY)) {
            return null;
        }
        if (!NightshadeClient.client().keybinds().getConflicts(module).isEmpty()
                && RenderUtil.isHovered(mouseX, mouseY, bindX(), bottomY(), bindX() + bindW(false), bottomY() + 11)) {
            return "Also bound to: " + NightshadeClient.client().keybinds().getConflicts(module).get(0).getName();
        }
        if (RenderUtil.isHovered(mouseX, mouseY, x, y + 26, x + w, bottomY())) {
            return module.getDescription();
        }
        return null;
    }

    public Action click(double mouseX, double mouseY, int button) {
        if (!isHovered(mouseX, mouseY)) {
            return Action.NONE;
        }
        if (button == 1) {
            return Action.OPEN_SETTINGS;
        }
        if (button != 0) {
            return Action.NONE;
        }
        if (RenderUtil.isHovered(mouseX, mouseY, starX() - 3, starY() - 3, starX() + 10, starY() + 10)) {
            return Action.FAVOURITE;
        }
        if (RenderUtil.isHovered(mouseX, mouseY, bindX(), bottomY() - 1, bindX() + bindW(false), bottomY() + 12)) {
            return Action.BIND;
        }
        if (RenderUtil.isHovered(mouseX, mouseY, gearX(), bottomY() - 1, x + w - 6, bottomY() + 12)) {
            return Action.OPEN_SETTINGS;
        }
        return Action.TOGGLE;
    }
}
