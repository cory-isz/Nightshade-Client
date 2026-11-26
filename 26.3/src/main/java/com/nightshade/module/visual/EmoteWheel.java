package com.nightshade.module.visual;

import com.nightshade.cosmetic.Emotes;
import com.nightshade.input.Keybind;
import com.nightshade.module.Module;
import com.nightshade.module.ModuleCategory;
import com.nightshade.module.setting.BooleanSetting;
import com.nightshade.util.EffectProbe;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

/** Keybind action (B by default): opens the emote wheel. Emotes are client-side, so only you see them. */
public final class EmoteWheel extends Module {
    private static EmoteWheel instance;
    private final BooleanSetting thirdPerson = add(new BooleanSetting("Show From The Front", "Switch to the front view while an emote plays, then switch back", true));

    public EmoteWheel() {
        super("Emote Wheel", "Press to pick an emote: wave, clap, cheer, dab and more (only you see them)", ModuleCategory.VISUAL);
        defaultKeybind(Keybind.key(com.mojang.blaze3d.platform.InputConstants.KEY_B));
        instance = this;
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            Emotes.thirdPerson = thirdPerson.get();
            Emotes.tick();
        });
    }

    public static EmoteWheel instance() {
        return instance;
    }

    @Override
    public boolean isToggleable() {
        return false;
    }

    @Override
    public void onAction() {
        if (mc.level != null && mc.player != null && mc.gui.screen() == null) {
            mc.gui.setScreen(new com.nightshade.gui.menu.EmoteWheelScreen());
            EffectProbe.hit("Emote Wheel");
        }
    }
}
