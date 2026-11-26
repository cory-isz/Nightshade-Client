package com.nightshade.event.events;

/** Fired after the server respawns the local player (death respawn or dimension change). */
public final class PlayerRespawnEvent {
    public static final PlayerRespawnEvent INSTANCE = new PlayerRespawnEvent();

    private PlayerRespawnEvent() {
    }
}
