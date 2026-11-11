package com.nightshade.module.visual;

import com.nightshade.util.EffectProbe;
import com.nightshade.module.Module;
import com.nightshade.module.ModuleCategory;
import com.nightshade.module.setting.BooleanSetting;
import com.nightshade.module.setting.ColorSetting;
import com.nightshade.module.setting.ModeSetting;
import com.nightshade.module.setting.NumberSetting;
import com.nightshade.render.ColorUtil;
import com.nightshade.render.RenderUtil;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.HitResult;

/**
 * Replaces the vanilla crosshair (see InGameHudMixin#renderCrosshair). Vanilla is kept when the debug screen is
 * open or the camera is not in first person, matching vanilla visibility rules.
 */
public final class CustomCrosshair extends Module {
    private static volatile CustomCrosshair active;
    private static boolean registered;

    private final ModeSetting style = add(new ModeSetting("Style", "Crosshair shape (Vanilla / Pack keeps the vanilla or resource pack crosshair)",
            "Cross", "Cross", "Dot", "Cross + Dot", "Square", "T-Shape", "Custom", "Vanilla / Pack"));
    // "Custom": a pixel crosshair drawn in the Crosshair Editor, stored as hex (one bit per cell).
    private final com.nightshade.module.setting.TextSetting pattern = add(new com.nightshade.module.setting.TextSetting("Pattern",
            "The custom crosshair's pixels", com.nightshade.render.CrosshairPattern.preset("Gap Cross").toHex(), 64)).visibleWhen(() -> false);
    private final com.nightshade.module.setting.ActionSetting editor = add(new com.nightshade.module.setting.ActionSetting("Crosshair Editor",
            "Draw your own crosshair pixel by pixel, pick a preset or paste a code", "Open Editor",
            () -> mc.gui.setScreen(new com.nightshade.gui.menu.CrosshairEditorScreen(mc.gui.screen()))));
    private final NumberSetting pixelSize = add(new NumberSetting("Pixel Size", "Size of each pixel of the custom crosshair, in screen pixels", 2, 1, 6, 1, "px"))
            .visibleWhen(() -> style.is("Custom"));
    private final NumberSetting size = add(new NumberSetting("Size", "Arm length", 5, 1, 15, 1, "px")).visibleWhen(this::shaped);
    private final NumberSetting gap = add(new NumberSetting("Gap", "Distance from the centre", 2, 0, 10, 1, "px")).visibleWhen(this::shaped);
    private final NumberSetting thickness = add(new NumberSetting("Thickness", "Line thickness", 1, 1, 4, 1, "px")).visibleWhen(this::shaped);
    private final ColorSetting color = add(new ColorSetting("Color", "Crosshair colour", 0xFFFFFFFF));
    private final BooleanSetting outline = add(new BooleanSetting("Outline", "Draw a dark outline for contrast", true));
    private final ColorSetting outlineColor = add(new ColorSetting("Outline Color", "Outline colour", 0xC0000000)).visibleWhen(outline::get);
    private final BooleanSetting targetColor = add(new BooleanSetting("Target Highlight", "Change colour when aiming at an entity", true));
    private final ColorSetting targetColorValue = add(new ColorSetting("Target Color", "Colour when aiming at an entity", 0xFFFF5C7A))
            .visibleWhen(targetColor::get);

    // Cooldown bars (the vanilla attack indicator is part of the crosshair this module replaces)
    private final ModeSetting cooldown = add(new ModeSetting("Cooldown", "Attack cooldown indicator under the crosshair", "Bar", "Bar", "Arc", "Dots", "Line", "Off"));
    private final NumberSetting barWidth = add(new NumberSetting("Bar Width", "Length of the cooldown bar (Arc: radius)", 16, 6, 60, 1, "px"))
            .visibleWhen(() -> !cooldown.is("Off"));
    private final NumberSetting barHeight = add(new NumberSetting("Bar Thickness", "Thickness of the cooldown bar", 2, 1, 6, 1, "px"))
            .visibleWhen(() -> !cooldown.is("Off") && !cooldown.is("Dots"));
    private final NumberSetting barOffset = add(new NumberSetting("Bar Offset", "Distance below the crosshair", 9, 2, 40, 1, "px"))
            .visibleWhen(() -> !cooldown.is("Off") && !cooldown.is("Arc"));
    private final ColorSetting barFill = add(new ColorSetting("Bar Fill", "Colour while charging", 0xFFB794FF))
            .visibleWhen(() -> !cooldown.is("Off"));
    private final ColorSetting barEmpty = add(new ColorSetting("Bar Empty", "Colour of the empty part", 0x70000000))
            .visibleWhen(() -> !cooldown.is("Off"));
    private final ColorSetting barReady = add(new ColorSetting("Bar Ready", "Colour when fully charged", 0xFF5BE49B))
            .visibleWhen(() -> !cooldown.is("Off"));
    private final BooleanSetting barGradient = add(new BooleanSetting("Bar Gradient", "Fade the fill from red to green as it charges", false))
            .visibleWhen(() -> !cooldown.is("Off"));
    private final BooleanSetting barRounded = add(new BooleanSetting("Bar Rounded", "Rounded bar ends", true))
            .visibleWhen(() -> cooldown.is("Bar"));
    private final BooleanSetting barHideReady = add(new BooleanSetting("Hide When Ready", "Only show the bar while charging", true))
            .visibleWhen(() -> !cooldown.is("Off"));
    private final BooleanSetting barPercent = add(new BooleanSetting("Show Percent", "Show the charge as a number next to the bar", false))
            .visibleWhen(() -> !cooldown.is("Off"));
    private final BooleanSetting itemBar = add(new BooleanSetting("Item Cooldown Bar", "A second bar for your held item's cooldown (pearl, shield, wind charge...)", true))
            .visibleWhen(() -> !cooldown.is("Off"));
    private final BooleanSetting barThirdPerson = add(new BooleanSetting("Bars In Third Person", "Keep the cooldown bars in F5", true))
            .visibleWhen(() -> !cooldown.is("Off"));

    public CustomCrosshair() {
        super("Custom Crosshair", "Replaces the crosshair with a configurable one", ModuleCategory.VISUAL);
        registerOnce();
    }

    @Override
    protected void onEnable() {
        active = this;
    }

    @Override
    protected void onDisable() {
        active = null;
    }

    /**
     * The cooldown bars are their own HUD layer (after the crosshair), so they keep working whatever crosshair is
     * drawn: any shape, Vanilla / Pack, the F3 crosshair, or with Vanilla HUD's "Hide Vanilla Crosshair" on.
     */
    private static synchronized void registerOnce() {
        if (registered) {
            return;
        }
        registered = true;
        HudElementRegistry.attachElementAfter(VanillaHudElements.CROSSHAIR,
                Identifier.fromNamespaceAndPath("nightshade", "cooldown_bars"), (context, tickCounter) -> {
                    CustomCrosshair module = active;
                    if (module != null && !mc.gui.hud.isHidden() && mc.player != null
                            && (mc.options.getCameraType().isFirstPerson() || module.barThirdPerson.get())) {
                        module.drawCooldowns(context, context.guiWidth() / 2, context.guiHeight() / 2);
                    }
                });
    }

    /** Draws the custom crosshair and returns true if vanilla rendering should be skipped. */
    public static boolean render(GuiGraphicsExtractor context) {
        CustomCrosshair module = active;
        if (module == null || module.style.is("Vanilla / Pack") || !mc.options.getCameraType().isFirstPerson()
                || mc.debugEntries.isOverlayVisible()) {
            return false;
        }
        module.draw(context);
        EffectProbe.hit("Custom Crosshair");
        return true;
    }

    private boolean shaped() {
        return !style.is("Custom") && !style.is("Vanilla / Pack");
    }

    /** GUI pixels to real screen pixels. */
    public static float guiScale() {
        int scaled = mc.getWindow().getGuiScaledWidth();
        return scaled <= 0 ? 1f : (float) mc.getWindow().getWidth() / scaled;
    }

    public com.nightshade.render.CrosshairPattern pattern() {
        com.nightshade.render.CrosshairPattern p = com.nightshade.render.CrosshairPattern.fromHex(pattern.get());
        return p == null ? com.nightshade.render.CrosshairPattern.preset("Gap Cross") : p;
    }

    /** Saves a pattern from the editor and switches the style to Custom. */
    public void setPattern(com.nightshade.render.CrosshairPattern p) {
        pattern.set(p.toHex());
        style.set("Custom");
    }

    public ColorSetting colorSetting() {
        return color;
    }

    public BooleanSetting outlineSetting() {
        return outline;
    }

    public ColorSetting outlineColorSetting() {
        return outlineColor;
    }

    public NumberSetting pixelSizeSetting() {
        return pixelSize;
    }

    private void draw(GuiGraphicsExtractor context) {
        int cx = context.guiWidth() / 2;
        int cy = context.guiHeight() / 2;
        int col = targetColor.get() && mc.hitResult != null && mc.hitResult.getType() == HitResult.Type.ENTITY
                ? targetColorValue.argb() : color.argb();
        if (style.is("Custom")) {
            pattern().draw(context, context.guiWidth() / 2f, context.guiHeight() / 2f, pixelSize.intValue(), col, outline.get(), outlineColor.argb());
            return;
        }
        int s = size.intValue();
        int g = gap.intValue();
        int t = thickness.intValue();
        int half = t / 2;
        String shape = style.get();
        if (shape.equals("Cross") || shape.equals("Cross + Dot") || shape.equals("T-Shape")) {
            if (!shape.equals("T-Shape")) {
                bar(context, cx - half, cy - g - s, cx - half + t, cy - g, col);
            }
            bar(context, cx - half, cy + g + (t % 2 == 0 ? 0 : 1), cx - half + t, cy + g + s + (t % 2 == 0 ? 0 : 1), col);
            bar(context, cx - g - s, cy - half, cx - g, cy - half + t, col);
            bar(context, cx + g + (t % 2 == 0 ? 0 : 1), cy - half, cx + g + s + (t % 2 == 0 ? 0 : 1), cy - half + t, col);
        }
        if (shape.equals("Dot") || shape.equals("Cross + Dot")) {
            bar(context, cx - half, cy - half, cx - half + t, cy - half + t, col);
        }
        if (shape.equals("Square")) {
            int r = g + s;
            bar(context, cx - r, cy - r, cx + r + 1, cy - r + t, col);
            bar(context, cx - r, cy + r + 1 - t, cx + r + 1, cy + r + 1, col);
            bar(context, cx - r, cy - r + t, cx - r + t, cy + r + 1 - t, col);
            bar(context, cx + r + 1 - t, cy - r + t, cx + r + 1, cy + r + 1 - t, col);
        }
    }

    // ------------------------------------------------------------------ cooldown bars

    private void drawCooldowns(GuiGraphicsExtractor context, int cx, int cy) {
        if (cooldown.is("Off") || mc.player == null) {
            return;
        }
        float attack = Math.min(1f, mc.player.getAttackStrengthScale(0f));
        int y = cy + barOffset.intValue();
        if (!(barHideReady.get() && attack >= 1f)) {
            drawIndicator(context, cx, cy, y, attack, 0);
            y += barHeight.intValue() + 3;
        }
        if (itemBar.get()) {
            float item = 0f;
            for (net.minecraft.world.item.ItemStack stack : new net.minecraft.world.item.ItemStack[]{mc.player.getMainHandItem(), mc.player.getOffhandItem()}) {
                if (!stack.isEmpty()) {
                    item = Math.max(item, mc.player.getCooldowns().getCooldownPercent(stack, 0f));
                }
            }
            if (item > 0f) {
                drawIndicator(context, cx, cy, y, 1f - item, 1);
            }
        }
    }

    private int fillColor(float progress) {
        if (progress >= 1f) {
            return barReady.argb();
        }
        if (barGradient.get()) {
            return ColorUtil.lerp(0xFFFF5C7A, barReady.argb(), progress);
        }
        return barFill.argb();
    }

    /** One indicator; {@code ring} 0 = attack, 1 = item (drawn as a second, outer arc in Arc style). */
    private void drawIndicator(GuiGraphicsExtractor context, int cx, int cy, int y, float progress, int ring) {
        int w = barWidth.intValue();
        int h = barHeight.intValue();
        int fill = fillColor(progress);
        switch (cooldown.get()) {
            case "Arc" -> {
                int radius = Math.max(4, w / 2) + ring * (h + 2);
                int segments = Math.max(16, radius * 3);
                for (int i = 0; i < segments; i++) {
                    float a = i / (float) segments;
                    // the arc runs clockwise from the top
                    double ang = Math.PI * 2 * a - Math.PI / 2;
                    int px = cx + (int) Math.round(Math.cos(ang) * radius);
                    int py = cy + (int) Math.round(Math.sin(ang) * radius);
                    RenderUtil.rect(context, px - h / 2, py - h / 2, px - h / 2 + h, py - h / 2 + h, a <= progress ? fill : barEmpty.argb());
                }
            }
            case "Dots" -> {
                int dots = 5;
                int size = Math.max(2, h + 1);
                int gapPx = 2;
                int total = dots * size + (dots - 1) * gapPx;
                int x = cx - total / 2;
                for (int i = 0; i < dots; i++) {
                    boolean on = progress >= (i + 1) / (float) dots - 0.001f;
                    RenderUtil.roundedRect(context, x, y, x + size, y + size, size / 2, on ? fill : barEmpty.argb());
                    x += size + gapPx;
                }
            }
            case "Line" -> {
                // fills outwards from the centre in both directions
                int half = w / 2;
                RenderUtil.rect(context, cx - half, y, cx + half, y + h, barEmpty.argb());
                int f = Math.round(half * progress);
                RenderUtil.rect(context, cx - f, y, cx + f, y + h, fill);
            }
            default -> {
                int x1 = cx - w / 2;
                int x2 = x1 + w;
                int r = barRounded.get() ? Math.max(0, h / 2) : 0;
                RenderUtil.roundedRect(context, x1, y, x2, y + h, r, barEmpty.argb());
                int fx = x1 + Math.max(r * 2, Math.round(w * progress));
                if (progress > 0f) {
                    RenderUtil.roundedRect(context, x1, y, Math.min(x2, fx), y + h, r, fill);
                }
            }
        }
        if (barPercent.get() && !cooldown.is("Arc")) {
            String pct = Math.round(progress * 100) + "%";
            com.nightshade.render.Fonts.draw(context, pct, cx + w / 2 + 3, y + h / 2 - 4, fill, true);
        }
    }

    private void bar(GuiGraphicsExtractor context, int x1, int y1, int x2, int y2, int col) {
        if (outline.get()) {
            RenderUtil.rect(context, x1 - 1, y1 - 1, x2 + 1, y2 + 1, outlineColor.argb());
        }
        RenderUtil.rect(context, x1, y1, x2, y2, col);
    }
}
