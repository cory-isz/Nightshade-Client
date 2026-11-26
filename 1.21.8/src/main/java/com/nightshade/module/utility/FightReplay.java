package com.nightshade.module.utility;

import com.nightshade.NightshadeClient;
import com.nightshade.event.events.TickEvent;
import com.nightshade.module.Module;
import com.nightshade.module.ModuleCategory;
import com.nightshade.module.setting.NumberSetting;
import com.nightshade.replay.ReplayRecorder;
import com.nightshade.util.EffectProbe;

import java.nio.file.Path;

/**
 * Fight Replay: keeps the last seconds of the players around you in memory. Press the "Save Replay" key (or use
 * Replays in the pause menu) to keep a clip, then watch it from the Replays screen.
 */
public final class FightReplay extends Module {
    private final NumberSetting length = add(new NumberSetting("Length", "How many seconds a saved clip covers", 30, 10, 60, 5, "s"));
    private final NumberSetting radius = add(new NumberSetting("Radius", "Record players within this distance", 32, 8, 64, 4, " blocks"));

    public FightReplay() {
        super("Fight Replay", "Records the last seconds of nearby players so you can save and rewatch fights", ModuleCategory.UTILITY);
        listen(TickEvent.Post.class, event -> {
            ReplayRecorder.tick(length.intValue(), radius.get());
            if (ReplayRecorder.bufferedTicks() > 0) {
                EffectProbe.hit("Fight Replay");
            }
        });
    }

    @Override
    protected void onDisable() {
        ReplayRecorder.clear();
    }

    /** Saves the buffer and tells the player where it went. */
    public static void saveClip() {
        try {
            Path file = ReplayRecorder.save();
            if (file == null) {
                NightshadeClient.client().notifications().warn("Fight Replay", "Nothing recorded yet - turn on Fight Replay first");
            } else {
                NightshadeClient.client().notifications().success("Fight Replay", "Saved " + file.getFileName() + " - watch it in Replays");
            }
        } catch (Exception e) {
            NightshadeClient.client().notifications().warn("Fight Replay", "Could not save: " + e.getMessage());
        }
    }
}
