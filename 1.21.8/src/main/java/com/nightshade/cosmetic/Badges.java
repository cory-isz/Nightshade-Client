package com.nightshade.cosmetic;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

/**
 * The little Nightshade logo in front of the names of other Nightshade players (name tags and the tab list).
 * Only Nightshade players see it; players who hide their cosmetics are not marked.
 */
public final class Badges {
    /** A private-use character drawn from assets/nightshade/font/badge.json (the client logo). */
    public static final String GLYPH = "";
    private static final ResourceLocation FONT = ResourceLocation.fromNamespaceAndPath("nightshade", "badge");

    private Badges() {
    }

    /** The name with the logo in front when this player is a Nightshade player; otherwise the name unchanged. */
    public static Component decorate(UUID player, Component name) {
        if (name == null || player == null || !com.nightshade.module.visual.Cosmetics.badges() || !CloudSync.isUser(player)) {
            return name;
        }
        if (name.getString().startsWith(GLYPH)) {
            return name; // already marked
        }
        return Component.empty().append(icon()).append(Component.literal(" ")).append(name);
    }

    public static Component icon() {
        return Component.literal(GLYPH).withStyle(s -> s.withFont(FONT).withColor(0xFFFFFF));
    }
}
