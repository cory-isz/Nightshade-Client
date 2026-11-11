package com.nightshade.module.utility;

import com.nightshade.util.EffectProbe;
import com.mojang.blaze3d.platform.InputConstants;
import com.nightshade.event.events.MouseScrollEvent;
import com.nightshade.input.Keybind;
import com.nightshade.module.BindMode;
import com.nightshade.module.Module;
import com.nightshade.module.ModuleCategory;
import com.nightshade.module.setting.BooleanSetting;
import com.nightshade.module.setting.NumberSetting;
import com.nightshade.render.Animation;

/** Hold-to-zoom with smooth transition, scroll adjustment and optional cinematic camera. */
public final class Zoom extends Module {
    private static Zoom instance;

    private final NumberSetting factor = add(new NumberSetting("Zoom Factor", "Default magnification", 4, 1.5, 10, 0.5, "x"));
    private final BooleanSetting smooth = add(new BooleanSetting("Smooth", "Animate zooming in and out", true));
    private final BooleanSetting scroll = add(new BooleanSetting("Scroll Adjust", "Use the mouse wheel to change zoom while zoomed", true));
    private final BooleanSetting cinematic = add(new BooleanSetting("Cinematic Camera", "Smooth camera movement while zoomed", false));

    private final Animation fovMultiplier = new Animation(16f, 1f);
    private double currentFactor;
    private boolean restoreSmoothCamera;

    public Zoom() {
        super("Zoom", "Hold the key to zoom in", ModuleCategory.UTILITY);
        instance = this;
        // Z: unbound in vanilla (C is the vanilla "save hotbar" activator).
        defaultKeybind(Keybind.key(InputConstants.KEY_Z));
        defaultBindMode(BindMode.HOLD);
        incompatibleWithMod("ok_zoomer");
        incompatibleWithMod("zoomify");
        listen(MouseScrollEvent.class, event -> {
            if (scroll.get() && event.vertical() != 0) {
                currentFactor = Math.max(1.5, Math.min(50.0, currentFactor * (event.vertical() > 0 ? 1.25 : 0.8)));
                event.cancel();
            }
        });
    }

    @Override
    protected void onEnable() {
        currentFactor = factor.get();
        if (cinematic.get() && !mc.options.smoothCamera) {
            mc.options.smoothCamera = true;
            restoreSmoothCamera = true;
        }
    }

    @Override
    protected void onDisable() {
        if (restoreSmoothCamera) {
            mc.options.smoothCamera = false;
            restoreSmoothCamera = false;
        }
    }

    /** FOV multiplier applied by GameRendererMixin; animates back to 1.0 after release. */
    public static double fovMultiplier() {
        Zoom zoom = instance;
        if (zoom == null) {
            return 1.0;
        }
        float target = zoom.isEnabled() ? (float) (1.0 / zoom.currentFactor) : 1f;
        if (zoom.isEnabled()) {
            EffectProbe.hit("Zoom");
        }
        zoom.fovMultiplier.setTarget(target);
        if (!zoom.smooth.get()) {
            zoom.fovMultiplier.snap(target);
            return target;
        }
        return zoom.fovMultiplier.update();
    }
}
