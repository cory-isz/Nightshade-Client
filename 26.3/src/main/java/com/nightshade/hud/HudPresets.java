package com.nightshade.hud;

import com.google.gson.JsonObject;
import com.nightshade.NightshadeClient;
import com.nightshade.module.HudModule;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Ready-made HUD layouts. Applying one turns on the HUD components it uses (and off the others), and places and
 * sizes them. Positions are fractions of the free space (0 = left/top edge, 1 = right/bottom edge), like
 * {@link HudModule} stores them, so a layout fits every resolution. The middle of the right edge is left free for
 * server scoreboards.
 */
public final class HudPresets {
    /** One component in a layout. */
    public record Spot(double x, double y, double scale) {
    }

    /** A layout's description and its components by HUD module name. */
    public record Preset(String description, Map<String, Spot> spots) {
    }

    public static final Map<String, Preset> PRESETS = new LinkedHashMap<>();

    private static Preset preset(String description, Object... spots) {
        Map<String, Spot> map = new LinkedHashMap<>();
        for (int i = 0; i < spots.length; i += 4) {
            map.put((String) spots[i], new Spot(((Number) spots[i + 1]).doubleValue(), ((Number) spots[i + 2]).doubleValue(),
                    ((Number) spots[i + 3]).doubleValue()));
        }
        return new Preset(description, map);
    }

    static {
        PRESETS.put("Minimal", preset("Just the essentials, out of the way",
                "FPS & Ping", 0.0, 0.0, 1.0,
                "Potion Effects", 1.0, 0.3, 1.0,
                "Armor Status", 1.0, 1.0, 1.0));
        PRESETS.put("PvP", preset("Keystrokes, CPS, armour and your target where you look",
                "Watermark", 0.0, 0.0, 1.0,
                "FPS & Ping", 0.0, 0.075, 1.0,
                "Keystrokes", 0.0, 0.40, 1.0,
                "CPS Counter", 0.0, 0.64, 1.0,
                "Combo Counter", 0.5, 0.62, 1.0,
                "Target HUD", 0.5, 0.74, 1.0,
                "Armor Status", 1.0, 1.0, 1.0,
                "Potion Effects", 1.0, 0.18, 1.0,
                "Cooldown HUD", 0.5, 0.86, 1.0));
        PRESETS.put("Streamer", preset("Big, readable stats for viewers; hides coordinates and the server address",
                "Watermark", 0.0, 0.0, 1.25,
                "Clock", 1.0, 0.0, 1.25,
                "Session Stats", 1.0, 0.1, 1.15,
                "Keystrokes", 0.0, 0.32, 1.1,
                "CPS Counter", 0.0, 0.58, 1.1,
                "Potion Effects", 1.0, 0.72, 1.0,
                "Armor Status", 1.0, 1.0, 1.0));
        PRESETS.put("Survival", preset("Where you are, what time it is and what's around you",
                "Watermark", 0.0, 0.0, 1.0,
                "FPS & Ping", 0.0, 0.075, 1.0,
                "Coordinates", 0.0, 0.15, 1.0,
                "Biome", 0.0, 0.25, 1.0,
                "Day Counter", 0.0, 0.32, 1.0,
                "Light Level", 0.0, 0.39, 1.0,
                "Compass", 0.5, 0.0, 1.0,
                "Waypoints", 1.0, 0.25, 1.0,
                "Potion Effects", 1.0, 0.75, 1.0,
                "Armor Status", 1.0, 1.0, 1.0));
    }

    private HudPresets() {
    }

    /** A copy of every HUD component's state, so a layout can be undone. */
    public record Snapshot(List<Entry> entries) {
        record Entry(HudModule module, boolean enabled, JsonObject hud, double scale) {
        }
    }

    public static Snapshot snapshot() {
        List<Snapshot.Entry> entries = new ArrayList<>();
        for (HudModule m : NightshadeClient.client().modules().getHudModules()) {
            entries.add(new Snapshot.Entry(m, m.isEnabled(), m.writeHud(), m.scaleSetting().get()));
        }
        return new Snapshot(entries);
    }

    public static void restore(Snapshot snapshot) {
        var modules = NightshadeClient.client().modules();
        for (Snapshot.Entry e : snapshot.entries()) {
            if (e.module().isEnabled() != e.enabled()) {
                modules.setEnabled(e.module(), e.enabled());
            }
            e.module().readHud(e.hud());
            e.module().setScale(e.scale());
            e.module().markDirty();
        }
    }

    /** Applies a layout by name; returns false when there is no such layout. */
    public static boolean apply(String name) {
        Preset preset = PRESETS.get(name);
        if (preset == null) {
            return false;
        }
        var modules = NightshadeClient.client().modules();
        for (HudModule m : modules.getHudModules()) {
            Spot spot = preset.spots().get(m.getName());
            if (spot == null) {
                if (m.isEnabled() && m.isToggleable()) {
                    modules.setEnabled(m, false);
                }
                continue;
            }
            if (!m.isEnabled()) {
                modules.setEnabled(m, true);
            }
            m.setScale(spot.scale());
            m.setFractionPosition(spot.x(), spot.y());
        }
        return true;
    }
}
