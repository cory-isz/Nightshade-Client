package com.nightshade.module;

import com.nightshade.util.EffectProbe;
import com.nightshade.module.setting.BooleanSetting;
import com.nightshade.module.setting.ColorSetting;
import com.nightshade.module.setting.NumberSetting;
import com.nightshade.render.ColorUtil;
import com.nightshade.render.RenderUtil;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import com.google.gson.JsonObject;

/**
 * Base for draggable HUD components.
 * <p>
 * Position is stored as a fraction (0..1) of the free space on each axis, so a component at 1.0 always hugs the
 * right/bottom edge regardless of resolution and can never leave the screen. Expensive value formatting happens in
 * {@link #refresh()}, at most 20 times a second (built in; it used to be the HUD Optimizer module); {@link #renderElement} draws cached values.
 */
public abstract class HudModule extends Module {
    protected final NumberSetting scale;
    protected final BooleanSetting background;
    protected final ColorSetting backgroundColor;
    protected final ColorSetting textColor;
    protected final BooleanSetting textShadow;

    private final double defaultX;
    private final double defaultY;
    private double posX;
    private double posY;
    /** True once the player has placed the component; otherwise {@link com.nightshade.hud.HudLayout} positions it. */
    private boolean custom;
    private int layoutX = Integer.MIN_VALUE;
    private int layoutY;
    private long lastRefreshNanos;
    private boolean refreshedOnce;

    protected HudModule(String name, String description, ModuleCategory category, double defaultX, double defaultY) {
        this(name, description, category, defaultX, defaultY, true);
    }

    protected HudModule(String name, String description, ModuleCategory category, double defaultX, double defaultY,
                        boolean defaultBackground) {
        super(name, description, category);
        this.scale = add(new NumberSetting("Scale", "Size of this HUD component", 1.0, 0.5, 2.5, 0.05, "x"));
        this.background = add(new BooleanSetting("Background", "Draw a translucent panel behind the component", defaultBackground));
        this.backgroundColor = add(new ColorSetting("Background Color", "Panel colour", 0x9A140B24));
        this.backgroundColor.visibleWhen(background::get);
        this.textColor = add(new ColorSetting("Text Color", "Primary text colour", 0xFFE2E6F0));
        this.textShadow = add(new BooleanSetting("Text Shadow", "Draw a drop shadow under text", true));
        this.defaultX = defaultX;
        this.defaultY = defaultY;
        this.posX = defaultX;
        this.posY = defaultY;
    }

    /** Unscaled width in GUI pixels. */
    public abstract int getWidth();

    /** Unscaled height in GUI pixels. */
    public abstract int getHeight();

    /** Recomputes cached display values. Called at most at the HUD refresh rate. */
    protected void refresh() {
    }

    /** Draws the element at local origin (0,0), unscaled. {@code preview} is true inside the HUD editor. */
    protected abstract void renderElement(GuiGraphicsExtractor context, float tickDelta, boolean preview);

    /** Whether there is something to draw outside the editor (e.g. Armor Status needs a player). */
    public boolean shouldRender() {
        return mc.player != null;
    }

    /** HUD values (text, counts) are recomputed 20 times a second; drawing still happens every frame. */
    private static final long REFRESH_INTERVAL_NANOS = 50_000_000L;

    public final void render(GuiGraphicsExtractor context, float tickDelta, boolean preview) {
        long now = System.nanoTime();
        long interval = preview ? 0L : REFRESH_INTERVAL_NANOS;
        if (!refreshedOnce || interval == 0L || now - lastRefreshNanos >= interval) {
            refresh();
            refreshedOnce = true;
            lastRefreshNanos = now;
        }
        int screenW = context.guiWidth();
        int screenH = context.guiHeight();
        float s = scale.floatValue();
        context.pose().pushMatrix();
        context.pose().translate(getScreenX(screenW), getScreenY(screenH));
        context.pose().scale(s, s);
        try {
            renderElement(context, tickDelta, preview);
            if (!preview) {
                EffectProbe.hit(getName());
            }
        } finally {
            context.pose().popMatrix();
        }
    }

    protected void drawBackground(GuiGraphicsExtractor context, int width, int height) {
        if (background.get()) {
            panel(context, 0, 0, width, height, backgroundColor.argb());
        }
    }

    /** Whether text gets a drop shadow: not needed on top of a panel in the modern styles. */
    protected boolean shadow() {
        return textShadow.get() && (!background.get() || "Minimal".equals(com.nightshade.render.Theme.hudStyle())
                || "Classic".equals(com.nightshade.render.Theme.hudStyle()));
    }

    /**
     * Draws one HUD panel in the chosen HUD Style. Kept to a few flat fills (no textures) because it runs for every
     * HUD component every frame.
     */
    public static void panel(GuiGraphicsExtractor context, int x1, int y1, int x2, int y2, int color) {
        String style = com.nightshade.render.Theme.hudStyle();
        int w = x2 - x1;
        int h = y2 - y1;
        switch (style) {
            case "Minimal" -> {
            }
            case "Classic" -> {
                RenderUtil.roundedRect(context, x1, y1, x2, y2, 4, color);
                RenderUtil.roundedOutline(context, x1, y1, x2, y2, 4, 0x18FFFFFF);
            }
            default -> {
                int r = Math.min(5, Math.min(w, h) / 2);
                // soft drop shadow
                RenderUtil.roundedRect(context, x1, y1 + 1, x2, y2 + 2, r, 0x38000000);
                // frosted body: the panel colour, a touch more opaque, with a light sheen on the upper half
                RenderUtil.roundedRect(context, x1, y1, x2, y2, r, ColorUtil.withAlpha(color, Math.min(255, ColorUtil.alpha(color) + 0x18)));
                if (h > 6 && w > 6) {
                    context.fillGradient(x1 + 2, y1 + 1, x2 - 2, y1 + h / 2, 0x12FFFFFF, 0x00FFFFFF);
                }
                RenderUtil.roundedOutline(context, x1, y1, x2, y2, r, 0x24FFFFFF);
                if ("Accent".equals(style) && h > 6) {
                    RenderUtil.roundedRect(context, x1, y1 + 3, x1 + 2, y2 - 3, 1, com.nightshade.render.Theme.accent());
                }
            }
        }
    }

    // ------------------------------------------------------------------ positioning

    public int getScaledWidth() {
        return (int) Math.ceil(getWidth() * scale.get());
    }

    public int getScaledHeight() {
        return (int) Math.ceil(getHeight() * scale.get());
    }

    public int getScreenX(int screenWidth) {
        if (!custom && layoutX != Integer.MIN_VALUE) {
            return Math.max(0, Math.min(layoutX, screenWidth - getScaledWidth()));
        }
        return (int) Math.round(posX * Math.max(0, screenWidth - getScaledWidth()));
    }

    public int getScreenY(int screenHeight) {
        if (!custom && layoutX != Integer.MIN_VALUE) {
            return Math.max(0, Math.min(layoutY, screenHeight - getScaledHeight()));
        }
        return (int) Math.round(posY * Math.max(0, screenHeight - getScaledHeight()));
    }

    public boolean hasCustomPosition() {
        return custom;
    }

    public double defaultX() {
        return defaultX;
    }

    public double defaultY() {
        return defaultY;
    }

    public void clearLayout() {
        layoutX = Integer.MIN_VALUE;
    }

    public void setLayoutPosition(int x, int y) {
        layoutX = x;
        layoutY = y;
    }

    /** Moves the element so its top-left corner is at (x, y), clamped inside the screen. */
    public void setScreenPosition(double x, double y, int screenWidth, int screenHeight) {
        double freeX = Math.max(1, screenWidth - getScaledWidth());
        double freeY = Math.max(1, screenHeight - getScaledHeight());
        double newX = clamp01(x / freeX);
        double newY = clamp01(y / freeY);
        if (newX != posX || newY != posY || !custom) {
            posX = newX;
            posY = newY;
            custom = true;
            markDirty();
        }
    }

    /** Places the element by fraction of the free space (0 = left/top, 1 = right/bottom), as a HUD layout does. */
    public void setFractionPosition(double x, double y) {
        posX = clamp01(x);
        posY = clamp01(y);
        custom = true;
        markDirty();
    }

    public void setScale(double value) {
        scale.set(value);
    }

    public NumberSetting scaleSetting() {
        return scale;
    }

    public void resetPosition() {
        posX = defaultX;
        posY = defaultY;
        custom = false;
        markDirty();
    }

    @Override
    public void resetSettings() {
        super.resetSettings();
        resetPosition();
    }

    private static double clamp01(double v) {
        return Double.isNaN(v) ? 0.0 : Math.max(0.0, Math.min(1.0, v));
    }

    public JsonObject writeHud() {
        JsonObject object = new JsonObject();
        object.addProperty("x", posX);
        object.addProperty("y", posY);
        object.addProperty("custom", custom);
        return object;
    }

    public void readHud(JsonObject object) {
        if (object.has("x") && object.get("x").isJsonPrimitive()) {
            posX = clamp01(object.get("x").getAsDouble());
        }
        if (object.has("y") && object.get("y").isJsonPrimitive()) {
            posY = clamp01(object.get("y").getAsDouble());
        }
        if (object.has("custom") && object.get("custom").isJsonPrimitive()) {
            custom = object.get("custom").getAsBoolean();
        } else {
            // Configs from before 1.12.3: a component counts as placed if it is not at its default position.
            custom = Math.abs(posX - defaultX) > 1e-4 || Math.abs(posY - defaultY) > 1e-4;
        }
    }
}
