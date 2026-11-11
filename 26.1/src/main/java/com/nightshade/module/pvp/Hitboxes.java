package com.nightshade.module.pvp;

import com.nightshade.event.events.TickEvent;
import com.nightshade.module.Module;
import com.nightshade.module.ModuleCategory;
import com.nightshade.util.EffectProbe;
import net.minecraft.client.gui.components.debug.DebugScreenEntries;
import net.minecraft.client.gui.components.debug.DebugScreenEntryStatus;

/** Shows entity hitboxes (Minecraft's own F3+B view) while enabled, so you can see exactly where to hit. */
public final class Hitboxes extends Module {
    public Hitboxes() {
        super("Hitboxes", "Shows entity hitboxes so you can see exactly where to hit", ModuleCategory.PVP);
        listen(TickEvent.Post.class, event -> {
            if (mc.debugEntries != null && !mc.debugEntries.isCurrentlyEnabled(DebugScreenEntries.ENTITY_HITBOXES)) {
                mc.debugEntries.setStatus(DebugScreenEntries.ENTITY_HITBOXES, DebugScreenEntryStatus.ALWAYS_ON);
            }
            if (mc.level != null) {
                EffectProbe.hit("Hitboxes");
            }
        });
    }

    @Override
    protected void onEnable() {
        if (mc.debugEntries != null) {
            mc.debugEntries.setStatus(DebugScreenEntries.ENTITY_HITBOXES, DebugScreenEntryStatus.ALWAYS_ON);
        }
    }

    @Override
    protected void onDisable() {
        if (mc.debugEntries != null) {
            mc.debugEntries.setStatus(DebugScreenEntries.ENTITY_HITBOXES, DebugScreenEntryStatus.NEVER);
        }
    }
}
