package com.nightshade.module.interfaces;

import com.mojang.blaze3d.platform.InputConstants;
import com.nightshade.gui.ClickGuiScreen;
import com.nightshade.input.Keybind;
import com.nightshade.module.Module;
import com.nightshade.module.ModuleCategory;
import com.nightshade.module.setting.BooleanSetting;
import com.nightshade.module.setting.ColorSetting;
import com.nightshade.module.setting.ModeSetting;
import com.nightshade.module.setting.NumberSetting;
import com.nightshade.render.Fonts;
import com.nightshade.render.Theme;

/** Opens the client GUI (default Right Shift) and holds GUI/theme preferences. */
public final class ClickGuiModule extends Module {
    private final ModeSetting theme = add(new ModeSetting("Theme", "Client colour: recolours everything - menus, background, GUI, HUD, notifications (Custom uses Accent Color)", "Nightshade",
            themeChoices())).onChange(this::applyTheme);
    private final ColorSetting accent = add(new ColorSetting("Accent Color", "Accent colour used by the Custom theme", 0xFF7C6CFF))
            .onChange(this::applyTheme);
    private final NumberSetting guiScale = add(new NumberSetting("GUI Scale", "Size of the client GUI, independent of Minecraft's GUI scale", 1.0, 0.6, 1.5, 0.05, "x"));
    private final ModeSetting font = add(new ModeSetting("Font", "Typeface for the client GUI and HUD: Minecraft (pixel), Inter or Poppins (clean modern sans, like OneConfig/OneClient), Rounded (Rubik)", "Poppins", Fonts.CHOICES))
            .onChange(this::applyTheme);
    private final BooleanSetting universalFont = add(new BooleanSetting("Universal Font", "Use the client font for all of Minecraft: chat, menus, tooltips, signs and name tags", true))
            .onChange(this::applyTheme);
    private final ModeSetting hudStyle = add(new ModeSetting("HUD Style", "Look of every HUD panel: Accent (glass with a client-colour edge), Glass (soft frosted panel), Minimal (text only), Classic (the old flat box)",
            "Accent", "Accent", "Glass", "Minimal", "Classic")).onChange(this::applyTheme);
    private final NumberSetting animationSpeed = add(new NumberSetting("Animation Speed", "Speed multiplier for interface animations", 1.0, 0.25, 3.0, 0.25, "x"))
            .onChange(this::applyTheme);
    private final NumberSetting backgroundDim = add(new NumberSetting("Background Dim", "How much the game is darkened behind the GUI", 55, 0, 90, 5, "%"));
    private final BooleanSetting showDescriptions = add(new BooleanSetting("Show Descriptions", "Show module descriptions on cards", true));
    private final BooleanSetting replaceBinds = add(new BooleanSetting("Replace Conflicting Binds", "Assigning a used key removes it from the other module", false));

    public ClickGuiModule() {
        super("ClickGUI", "Opens the client interface", ModuleCategory.INTERFACE);
        defaultKeybind(Keybind.key(InputConstants.KEY_RSHIFT));
        accent.visibleWhen(() -> theme.is("Custom"));
        applyTheme();
    }

    private static String[] themeChoices() {
        String[] palettes = com.nightshade.render.MenuBackground.Palette.labels();
        String[] out = java.util.Arrays.copyOf(palettes, palettes.length + 1);
        out[palettes.length] = "Custom";
        return out;
    }

    private void applyTheme() {
        com.nightshade.render.Theme.setHudStyle(hudStyle == null ? "Accent" : hudStyle.get());
        com.nightshade.render.Theme.setUniversalFont(universalFont == null || universalFont.get());
        if (theme.is("Custom")) {
            Theme.applyPalette(nearestPalette(accent.argb()), accent.argb());
        } else {
            var p = com.nightshade.render.MenuBackground.Palette.byLabel(theme.get());
            Theme.applyPalette(p, com.nightshade.render.MenuBackground.accentOf(p));
        }
        Theme.setFont(font.get());
        Theme.setAnimationSpeed(animationSpeed.floatValue());
    }

    /** For a custom accent: the palette whose colour is closest in hue, used for the menu sky. */
    private static com.nightshade.render.MenuBackground.Palette nearestPalette(int argb) {
        float[] want = com.nightshade.render.ColorUtil.rgbToHsb((argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF, null);
        com.nightshade.render.MenuBackground.Palette best = com.nightshade.render.MenuBackground.Palette.NIGHTSHADE;
        float bestD = Float.MAX_VALUE;
        for (var p : com.nightshade.render.MenuBackground.Palette.values()) {
            int c = com.nightshade.render.MenuBackground.accentOf(p);
            float[] hsb = com.nightshade.render.ColorUtil.rgbToHsb((c >> 16) & 0xFF, (c >> 8) & 0xFF, c & 0xFF, null);
            float dh = Math.abs(hsb[0] - want[0]);
            dh = Math.min(dh, 1f - dh) + Math.abs(hsb[1] - want[1]) * 0.3f;
            if (dh < bestD) {
                bestD = dh;
                best = p;
            }
        }
        return best;
    }

    /** Sets the client colour by name (one of the palettes, or "Custom"). */
    public void setClientColor(String name) {
        theme.set(name);
    }

    @Override
    public boolean isToggleable() {
        return false;
    }

    @Override
    public void onAction() {
        if (mc.gui.screen() == null) {
            mc.gui.setScreen(new ClickGuiScreen());
        }
    }

    public float guiScale() {
        return guiScale.floatValue();
    }

    public int backgroundDimAlpha() {
        return (int) Math.round(backgroundDim.get() / 100.0 * 255.0);
    }

    public boolean showDescriptions() {
        return showDescriptions.get();
    }

    public boolean replaceConflictingBinds() {
        return replaceBinds.get();
    }
}
