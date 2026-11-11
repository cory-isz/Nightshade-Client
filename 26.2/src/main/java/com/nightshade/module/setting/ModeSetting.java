package com.nightshade.module.setting;

import com.nightshade.module.ModuleSetting;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

import java.util.List;

/** One-of-many choice setting. */
public final class ModeSetting extends ModuleSetting<String> {
    private final List<String> modes;

    public ModeSetting(String name, String description, String defaultValue, String... modes) {
        super(name, description, defaultValue);
        this.modes = List.of(modes);
        if (!this.modes.contains(defaultValue)) {
            throw new IllegalArgumentException("Default mode " + defaultValue + " not in " + this.modes);
        }
    }

    public List<String> getModes() {
        return modes;
    }

    public boolean is(String mode) {
        return value.equals(mode);
    }

    public void cycle(int direction) {
        int index = modes.indexOf(value);
        int next = Math.floorMod(index + direction, modes.size());
        set(modes.get(next));
    }

    @Override
    protected String validate(String input) {
        return input != null && modes.contains(input) ? input : null;
    }

    @Override
    public JsonElement toJson() {
        return new JsonPrimitive(value);
    }

    @Override
    public boolean fromJson(JsonElement element) {
        if (element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()
                && modes.contains(element.getAsString())) {
            load(element.getAsString());
            return true;
        }
        return false;
    }
}
