package com.nightshade.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

/** Client-only UI sounds (heard by you alone, never sent to the server). */
public final class Sounds {
    /** Names shown in sound mode settings, in the same order as {@link #event(String)} understands them. */
    public static final String[] NAMES = {"Ding", "Pling", "Bell", "Chime", "Click", "Hat", "Arrow Hit", "Level Up"};

    private Sounds() {
    }

    public static void play(String name, float volume, float pitch) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getSoundManager() == null || volume <= 0f) {
            return;
        }
        try {
            playUnchecked(name, volume, pitch);
        } catch (RuntimeException e) {
            // No audio device (or the sound engine is reloading): skip the sound rather than fail the caller.
        }
    }

    private static void playUnchecked(String name, float volume, float pitch) {
        Minecraft mc = Minecraft.getInstance();
        Object event = event(name);
        SoundEvent sound = event instanceof Holder<?> entry ? (SoundEvent) entry.value() : (SoundEvent) event;
        // ui(sound, pitch, volume)
        mc.getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, volume));
    }

    private static Object event(String name) {
        return switch (name) {
            case "Pling" -> SoundEvents.NOTE_BLOCK_PLING;
            case "Bell" -> SoundEvents.NOTE_BLOCK_BELL;
            case "Chime" -> SoundEvents.NOTE_BLOCK_CHIME;
            case "Click" -> SoundEvents.UI_BUTTON_CLICK;
            case "Hat" -> SoundEvents.NOTE_BLOCK_HAT;
            case "Arrow Hit" -> SoundEvents.ARROW_HIT_PLAYER;
            case "Level Up" -> SoundEvents.PLAYER_LEVELUP;
            default -> SoundEvents.EXPERIENCE_ORB_PICKUP;
        };
    }
}
