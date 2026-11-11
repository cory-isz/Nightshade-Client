package com.nightshade.event.events;

import net.minecraft.world.entity.player.Player;

/** A player used a Totem of Undying (the server broadcast entity status 35, which plays the totem animation). */
public record TotemPopEvent(Player player) {
}
