package com.nightshade.module.pvp;

import com.nightshade.util.EffectProbe;
import com.nightshade.event.events.HitLandedEvent;
import com.nightshade.event.events.TickEvent;
import com.nightshade.module.Module;
import com.nightshade.module.ModuleCategory;
import com.nightshade.module.setting.BooleanSetting;
import com.nightshade.module.setting.ModeSetting;
import com.nightshade.module.setting.NumberSetting;
import com.nightshade.util.Sounds;
import net.minecraft.world.entity.LivingEntity;

/** Plays a sound (heard only by you) when the server confirms your hit, and optionally another when the target dies. */
public final class HitSounds extends Module {
    private final ModeSetting sound = add(new ModeSetting("Hit Sound", "Sound for a landed hit", "Ding", Sounds.NAMES));
    private final NumberSetting volume = add(new NumberSetting("Volume", "Hit sound volume", 0.6, 0.05, 1.0, 0.05));
    private final NumberSetting pitch = add(new NumberSetting("Pitch", "Hit sound pitch", 1.2, 0.5, 2.0, 0.05));
    private final BooleanSetting playersOnly = add(new BooleanSetting("Players Only", "Only play for hits on players", false));
    private final BooleanSetting killSound = add(new BooleanSetting("Kill Sound", "Play a sound when your target dies", true));
    private final ModeSetting killType = add(new ModeSetting("Kill Sound Type", "Sound when the target dies", "Level Up", Sounds.NAMES)).visibleWhen(killSound::get);

    private LivingEntity lastHit;
    private int killWindow;
    private int hits;

    public HitSounds() {
        super("Hit Sounds", "Plays a sound when your hits land", ModuleCategory.PVP);
        listen(HitLandedEvent.class, event -> {
            LivingEntity target = event.target();
            if (!playersOnly.get() || target instanceof net.minecraft.world.entity.player.Player) {
                Sounds.play(sound.get(), volume.get().floatValue(), pitch.get().floatValue());
                hits++;
                EffectProbe.hit("Hit Sounds");
                lastHit = target;
                killWindow = 30;
            }
        });
        listen(TickEvent.Post.class, event -> tick());
    }

    private void tick() {
        if (lastHit != null) {
            if (killSound.get() && (lastHit.isDeadOrDying() || lastHit.getHealth() <= 0f)) {
                Sounds.play(killType.get(), volume.get().floatValue(), 1f);
                lastHit = null;
            } else if (--killWindow <= 0) {
                lastHit = null;
            }
        }
    }

    /** Number of confirmed hit sounds played (exposed for tests). */
    public int hitsPlayed() {
        return hits;
    }

    @Override
    protected void onDisable() {
        lastHit = null;
    }
}
