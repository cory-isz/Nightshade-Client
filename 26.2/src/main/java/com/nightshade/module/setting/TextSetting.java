package com.nightshade.module.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import com.nightshade.module.ModuleSetting;

/** A short line of text typed in the settings panel (e.g. a nickname). */
public final class TextSetting extends ModuleSetting<String> {
    private final int maxLength;

    public TextSetting(String name, String description, String defaultValue, int maxLength) {
        super(name, description, defaultValue);
        this.maxLength = maxLength;
    }

    public int maxLength() {
        return maxLength;
    }

    @Override
    protected String validate(String input) {
        if (input == null) {
            return null;
        }
        String clean = input.replaceAll("[\\p{Cntrl}§]", "");
        return clean.length() > maxLength ? clean.substring(0, maxLength) : clean;
    }

    @Override
    public JsonElement toJson() {
        return new JsonPrimitive(value);
    }

    @Override
    public boolean fromJson(JsonElement element) {
        if (element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
            String v = validate(element.getAsString());
            if (v != null) {
                load(v);
                return true;
            }
        }
        return false;
    }
}
