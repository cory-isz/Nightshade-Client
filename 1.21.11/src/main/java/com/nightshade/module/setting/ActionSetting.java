package com.nightshade.module.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.nightshade.module.ModuleSetting;

/** A button row in the settings panel (e.g. "Open Editor"). Holds no value and is never saved. */
public final class ActionSetting extends ModuleSetting<Boolean> {
    private final String buttonLabel;
    private final Runnable action;

    public ActionSetting(String name, String description, String buttonLabel, Runnable action) {
        super(name, description, Boolean.FALSE);
        this.buttonLabel = buttonLabel;
        this.action = action;
    }

    public String buttonLabel() {
        return buttonLabel;
    }

    public void run() {
        action.run();
    }

    @Override
    public boolean persistent() {
        return false;
    }

    @Override
    protected Boolean validate(Boolean input) {
        return Boolean.FALSE;
    }

    @Override
    public JsonElement toJson() {
        return JsonNull.INSTANCE;
    }

    @Override
    public boolean fromJson(JsonElement element) {
        return true;
    }
}
