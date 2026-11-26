package com.nightshade.mods;

import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/** One row in the mod manager: a loaded mod, a nested library, or a disabled jar in the mods folder. */
public record ModEntry(
        String id,
        String name,
        String version,
        String description,
        List<String> authors,
        List<String> licenses,
        Map<String, String> contact,
        String environment,
        @Nullable String iconPath,
        @Nullable Path jar,
        boolean loaded,
        boolean builtin,
        boolean library,
        State state,
        List<String> dependents) {

    public enum State {
        /** Loaded and will stay enabled. */
        ENABLED("Enabled", 0xFF5BE49B),
        /** Not loaded (jar renamed to .jar.disabled). */
        DISABLED("Disabled", 0xFF8A8FA3),
        /** Loaded now, disabled after a restart. */
        PENDING_DISABLE("Disabled after restart", 0xFFFFC857),
        /** Not loaded now, enabled after a restart. */
        PENDING_ENABLE("Enabled after restart", 0xFFFFC857),
        /** Deleted (or queued for deletion when the game exits). */
        PENDING_DELETE("Deleted after restart", 0xFFFF5C7A);

        private final String label;
        private final int color;

        State(String label, int color) {
            this.label = label;
            this.color = color;
        }

        public String label() {
            return label;
        }

        public int color() {
            return color;
        }
    }

    /** Only top-level jars in the instance's mods folder can be enabled, disabled or deleted. */
    public boolean editable() {
        return jar != null && !builtin && !library && !"nightshade".equals(id);
    }
}
