package com.nightshade.module;

/** The six top-level navigation categories of the client. */
public enum ModuleCategory {
    HUD("HUD", "hud", "On-screen overlays and their layout"),
    VISUAL("Visual", "visual", "Visual customisation of the game"),
    PVP("PvP", "pvp", "Fair-play combat info, feedback and visuals"),
    PERFORMANCE("Performance", "performance", "Rendering optimisations and frame-time tools"),
    UTILITY("Utility", "utility", "Quality-of-life utilities"),
    INTERFACE("Interface", "interface", "Client GUI, editor and notifications");

    private final String displayName;
    private final String id;
    private final String description;

    ModuleCategory(String displayName, String id, String description) {
        this.displayName = displayName;
        this.id = id;
        this.description = description;
    }

    public String displayName() {
        return displayName;
    }

    public String id() {
        return id;
    }

    public String description() {
        return description;
    }
}
