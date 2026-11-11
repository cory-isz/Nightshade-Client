package com.nightshade.module.setting;

import com.nightshade.module.ModuleSetting;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

public final class BooleanSetting extends ModuleSetting<Boolean> {
    public BooleanSetting(String name, String description, boolean defaultValue) {
        super(name, description, defaultValue);
    }

    public void toggle() {
        set(!value);
    }

    @Override
    protected Boolean validate(Boolean input) {
        return input;
    }

    @Override
    public JsonElement toJson() {
        return new JsonPrimitive(value);
    }

    @Override
    public boolean fromJson(JsonElement element) {
        if (element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isBoolean()) {
            load(element.getAsBoolean());
            return true;
        }
        return false;
    }
}
