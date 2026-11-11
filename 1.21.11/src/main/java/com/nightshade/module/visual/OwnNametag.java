package com.nightshade.module.visual;

import com.nightshade.util.EffectProbe;
import com.nightshade.module.Module;
import com.nightshade.module.ModuleCategory;

/** Shows your own nametag in third person (see PlayerEntityRendererMixin). */
public final class OwnNametag extends Module {
    private static volatile OwnNametag active;

    public OwnNametag() {
        super("Own Nametag", "Shows your own name above you in third person", ModuleCategory.VISUAL);
    }

    @Override
    protected void onEnable() {
        active = this;
    }

    @Override
    protected void onDisable() {
        active = null;
    }

    public static boolean shouldShow(Object entity) {
        boolean show = active != null && entity == mc.player && !mc.options.getCameraType().isFirstPerson();
        if (show) {
            EffectProbe.hit("Own Nametag");
        }
        return show;
    }
}
