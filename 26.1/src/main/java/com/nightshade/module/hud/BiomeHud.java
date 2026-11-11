package com.nightshade.module.hud;

/** The biome you are standing in. */
public final class BiomeHud extends SimpleTextHud {
    public BiomeHud() {
        super("Biome", "Shows the biome you are in", "Biome ", 0.0, 0.42);
    }

    @Override
    protected void refresh() {
        if (mc.player == null || mc.level == null) {
            return;
        }
        String id = mc.level.getBiome(mc.player.blockPosition()).getRegisteredName();
        String path = id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;
        StringBuilder out = new StringBuilder();
        for (String word : path.split("_")) {
            if (!word.isEmpty()) {
                out.append(out.isEmpty() ? "" : " ").append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
            }
        }
        value = out.toString();
    }
}
