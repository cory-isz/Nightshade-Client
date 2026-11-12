package com.nightshade.module.performance;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.nightshade.NightshadeClient;
import com.nightshade.gui.menu.SetupWizardScreen;
import com.nightshade.module.Module;
import com.nightshade.module.ModuleCategory;
import com.nightshade.module.setting.ActionSetting;
import com.nightshade.module.setting.BooleanSetting;
import com.nightshade.util.Log;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Options;
import net.minecraft.server.level.ParticleStatus;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * One click for more FPS: turns down the vanilla video settings that cost the most for the least visual difference
 * (clouds, entity shadows, improved transparency, chunk fade-in, biome blending, far entities) and turns on
 * Nightshade's performance modules. Your previous settings are saved, so Undo puts everything back exactly.
 */
public final class FpsBoost extends Module {
    private final BooleanSetting capDistance = add(new BooleanSetting("Cap Render Distance", "Lower render distance to 12 chunks if it is higher", true));
    private final BooleanSetting fewerParticles = add(new BooleanSetting("Fewer Particles", "Set particles to Decreased", true));
    private final ActionSetting apply = add(new ActionSetting("Boost", "Apply the FPS settings (your current settings are saved first)", "Apply", this::boost));
    private final ActionSetting undo = add(new ActionSetting("Undo", "Put your video settings back to how they were", "Restore", this::restore));

    public FpsBoost() {
        super("FPS Boost", "One click: faster video settings and all performance modules, with undo", ModuleCategory.PERFORMANCE);
    }

    @Override
    public boolean isToggleable() {
        return false;
    }

    @Override
    public void onAction() {
        if (Files.exists(undoFile())) {
            restore();
        } else {
            boost();
        }
    }

    private static Path undoFile() {
        return FabricLoader.getInstance().getConfigDir().resolve("nightshade").resolve("fps_boost_undo.json");
    }

    public boolean isBoosted() {
        return Files.exists(undoFile());
    }

    public void boost() {
        Options o = mc.options;
        if (!isBoosted()) {
            JsonObject saved = new JsonObject();
            saved.addProperty("renderDistance", o.renderDistance().get());
            saved.addProperty("entityDistanceScaling", o.entityDistanceScaling().get());
            saved.addProperty("cloudStatus", o.cloudStatus().get().name());
            saved.addProperty("entityShadows", o.entityShadows().get());
            saved.addProperty("improvedTransparency", o.improvedTransparency().get());
            saved.addProperty("chunkSectionFadeInTime", o.chunkSectionFadeInTime().get());
            saved.addProperty("biomeBlendRadius", o.biomeBlendRadius().get());
            saved.addProperty("particles", o.particles().get().name());
            saved.addProperty("enableVsync", o.enableVsync().get());
            try {
                Files.createDirectories(undoFile().getParent());
                Files.writeString(undoFile(), saved.toString(), StandardCharsets.UTF_8);
            } catch (Exception e) {
                Log.warn("FPS Boost: could not save your settings: {}", e.getMessage());
                NightshadeClient.client().notifications().info("FPS Boost", "Couldn't save your current settings, nothing changed");
                return;
            }
        }
        if (capDistance.get() && o.renderDistance().get() > 12) {
            o.renderDistance().set(12);
        }
        o.entityDistanceScaling().set(Math.min(o.entityDistanceScaling().get(), 0.75));
        o.cloudStatus().set(CloudStatus.OFF);
        o.entityShadows().set(false);
        o.improvedTransparency().set(false);
        o.chunkSectionFadeInTime().set(0.0);
        o.biomeBlendRadius().set(Math.min(o.biomeBlendRadius().get(), 1));
        if (fewerParticles.get() && o.particles().get() == ParticleStatus.ALL) {
            o.particles().set(ParticleStatus.DECREASED);
        }
        o.enableVsync().set(false);
        o.save();
        SetupWizardScreen.applyPerformance(1);
        com.nightshade.util.EffectProbe.hit("FPS Boost");
        NightshadeClient.client().config().saveDirty();
        NightshadeClient.client().notifications().success("FPS Boost", "Applied - use Undo to put your settings back");
    }

    public void restore() {
        if (!isBoosted()) {
            NightshadeClient.client().notifications().info("FPS Boost", "Nothing to undo");
            return;
        }
        Options o = mc.options;
        try {
            JsonObject s = JsonParser.parseString(Files.readString(undoFile(), StandardCharsets.UTF_8)).getAsJsonObject();
            o.renderDistance().set(s.get("renderDistance").getAsInt());
            o.entityDistanceScaling().set(s.get("entityDistanceScaling").getAsDouble());
            o.cloudStatus().set(CloudStatus.valueOf(s.get("cloudStatus").getAsString()));
            o.entityShadows().set(s.get("entityShadows").getAsBoolean());
            o.improvedTransparency().set(s.get("improvedTransparency").getAsBoolean());
            o.chunkSectionFadeInTime().set(s.get("chunkSectionFadeInTime").getAsDouble());
            o.biomeBlendRadius().set(s.get("biomeBlendRadius").getAsInt());
            o.particles().set(ParticleStatus.valueOf(s.get("particles").getAsString()));
            o.enableVsync().set(s.get("enableVsync").getAsBoolean());
            o.save();
            Files.deleteIfExists(undoFile());
            com.nightshade.util.EffectProbe.hit("FPS Boost");
            NightshadeClient.client().notifications().success("FPS Boost", "Your video settings are back");
        } catch (Exception e) {
            Log.warn("FPS Boost: could not restore settings: {}", e.getMessage());
            NightshadeClient.client().notifications().info("FPS Boost", "Couldn't read the saved settings");
        }
    }
}
