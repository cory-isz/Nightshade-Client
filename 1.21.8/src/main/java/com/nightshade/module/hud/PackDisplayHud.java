package com.nightshade.module.hud;

import net.minecraft.server.packs.repository.Pack;

/** The resource pack you are using (the top one, plus how many more are on). */
public final class PackDisplayHud extends SimpleTextHud {
    public PackDisplayHud() {
        super("Pack Display", "Shows your active resource pack", "Pack ", 0.0, 0.66);
    }

    @Override
    public boolean shouldRender() {
        return true;
    }

    @Override
    protected void refresh() {
        Pack top = null;
        int extra = 0;
        for (Pack pack : mc.getResourcePackRepository().getSelectedPacks()) {
            String id = pack.getId();
            // only real resource packs (from the resourcepacks folder, a server or a world), not built-in mod resources
            if (!id.startsWith("file/") && !id.equals("server") && !id.equals("world")) {
                continue;
            }
            if (top != null) {
                extra++;
            }
            top = pack; // the last selected pack is on top
        }
        value = top == null ? "Default" : top.getTitle().getString().replaceAll("§.", "") + (extra > 0 ? "  +" + extra : "");
    }
}
