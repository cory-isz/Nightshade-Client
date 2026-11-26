package com.nightshade.module.pvp;

import com.nightshade.event.events.TickEvent;
import com.nightshade.module.Module;
import com.nightshade.module.ModuleCategory;
import com.nightshade.util.EffectProbe;

/** Shows entity hitboxes (Minecraft's own F3+B view) while enabled, so you can see exactly where to hit. */
public final class Hitboxes extends Module {
    public Hitboxes() {
        super("Hitboxes", "Shows entity hitboxes so you can see exactly where to hit", ModuleCategory.PVP);
        listen(TickEvent.Post.class, event -> {
            if (!mc.getEntityRenderDispatcher().shouldRenderHitBoxes()) {
                mc.getEntityRenderDispatcher().setRenderHitBoxes(true);
            }
            if (mc.level != null) {
                EffectProbe.hit("Hitboxes");
            }
        });
    }

    @Override
    protected void onEnable() {
        mc.getEntityRenderDispatcher().setRenderHitBoxes(true);
    }

    @Override
    protected void onDisable() {
        mc.getEntityRenderDispatcher().setRenderHitBoxes(false);
    }
}
