package com.nightshade.module.performance;

import com.nightshade.util.EffectProbe;
import com.nightshade.module.Module;
import com.nightshade.module.ModuleCategory;
import com.nightshade.module.setting.BooleanSetting;
import net.minecraft.resources.Identifier;

/** Disables selected full-screen overlay effects (see InGameHudMixin). */
public final class EffectReducer extends Module {
    private static final Identifier PUMPKIN_BLUR = Identifier.withDefaultNamespace("textures/misc/pumpkinblur.png");
    private static final Identifier POWDER_SNOW = Identifier.withDefaultNamespace("textures/misc/powder_snow_outline.png");
    private static volatile EffectReducer active;

    private final BooleanSetting vignette = add(new BooleanSetting("Hide Vignette", "Remove the darkened screen edges", true));
    private final BooleanSetting pumpkin = add(new BooleanSetting("Hide Pumpkin Overlay", "Remove the carved pumpkin overlay", false));
    private final BooleanSetting powderSnow = add(new BooleanSetting("Hide Powder Snow Overlay", "Remove the frozen overlay", false));
    private final BooleanSetting portal = add(new BooleanSetting("Hide Portal Overlay", "Remove the nether portal swirl", true));

    public EffectReducer() {
        super("Effect Reducer", "Disables selected full-screen visual effects", ModuleCategory.PERFORMANCE);
    }

    @Override
    protected void onEnable() {
        active = this;
    }

    @Override
    protected void onDisable() {
        active = null;
    }

    public static boolean hideVignette() {
        EffectReducer module = active;
        boolean hide = module != null && module.vignette.get();
        if (hide) {
            EffectProbe.hit("Effect Reducer");
        }
        return hide;
    }

    public static boolean hidePortal() {
        EffectReducer module = active;
        boolean hide = module != null && module.portal.get();
        if (hide) {
            EffectProbe.hit("Effect Reducer");
        }
        return hide;
    }

    public static boolean hideOverlay(Identifier texture) {
        EffectReducer module = active;
        if (module == null) {
            return false;
        }
        // Since 1.21.4 the pumpkin overlay is the carved pumpkin's equippable camera overlay
        // (textures/misc/pumpkinblur.png after path expansion), so match on the file name.
        boolean hide = (module.pumpkin.get() && (PUMPKIN_BLUR.equals(texture) || texture.getPath().endsWith("/pumpkinblur.png")))
                || (module.powderSnow.get() && POWDER_SNOW.equals(texture));
        if (hide) {
            EffectProbe.hit("Effect Reducer");
        }
        return hide;
    }
}
