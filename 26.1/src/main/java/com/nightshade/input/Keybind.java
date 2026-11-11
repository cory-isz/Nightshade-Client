package com.nightshade.input;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;

/** Immutable key or mouse-button binding. */
public record Keybind(Type type, int code) {
    public static final Keybind NONE = new Keybind(Type.NONE, -1);

    public enum Type {
        NONE, KEY, MOUSE
    }

    public static Keybind key(int glfwKey) {
        return glfwKey == GLFW.GLFW_KEY_UNKNOWN ? NONE : new Keybind(Type.KEY, glfwKey);
    }

    public static Keybind mouse(int button) {
        return new Keybind(Type.MOUSE, button);
    }

    public boolean isBound() {
        return type != Type.NONE;
    }

    public boolean matchesKey(int key) {
        return type == Type.KEY && code == key;
    }

    public boolean matchesMouse(int button) {
        return type == Type.MOUSE && code == button;
    }

    public String displayName() {
        return switch (type) {
            case NONE -> "None";
            case MOUSE -> switch (code) {
                case GLFW.GLFW_MOUSE_BUTTON_LEFT -> "LMB";
                case GLFW.GLFW_MOUSE_BUTTON_RIGHT -> "RMB";
                case GLFW.GLFW_MOUSE_BUTTON_MIDDLE -> "MMB";
                default -> "Mouse " + (code + 1);
            };
            case KEY -> {
                String name = InputConstants.Type.KEYSYM.getOrCreate(code).getDisplayName().getString();
                yield name.length() == 1 ? name.toUpperCase(Locale.ROOT) : name;
            }
        };
    }

    public JsonObject toJson() {
        JsonObject object = new JsonObject();
        object.addProperty("type", type.name());
        object.addProperty("code", code);
        // The key name ("key.keyboard.z") is portable across GLFW (<= 26.2) and SDL (26.3+) key codes.
        String name = keyName();
        if (name != null) {
            object.addProperty("name", name);
        }
        return object;
    }

    /** Vanilla key name for this bind, e.g. "key.keyboard.right.shift" or "key.mouse.4"; null for NONE. */
    public String keyName() {
        try {
            return switch (type) {
                case NONE -> null;
                case KEY -> InputConstants.Type.KEYSYM.getOrCreate(code).getName();
                case MOUSE -> InputConstants.Type.MOUSE.getOrCreate(code).getName();
            };
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** Parses a keybind; returns null when the element is malformed. */
    public static Keybind fromJson(JsonElement element) {
        if (element == null || !element.isJsonObject()) {
            return null;
        }
        JsonObject object = element.getAsJsonObject();
        try {
            Type type = Type.valueOf(object.get("type").getAsString());
            if (type == Type.NONE) {
                return NONE;
            }
            if (object.has("name")) {
                try {
                    InputConstants.Key key = InputConstants.getKey(object.get("name").getAsString());
                    boolean keyboard = key.getType() == InputConstants.Type.KEYSYM;
                    if ((type == Type.KEY && keyboard) || (type == Type.MOUSE && key.getType() == InputConstants.Type.MOUSE)) {
                        return new Keybind(type, key.getValue());
                    }
                } catch (RuntimeException ignored) {
                    // fall back to the numeric code
                }
            }
            int code = object.get("code").getAsInt();
            if (code < 0 || (type == Type.MOUSE && code > GLFW.GLFW_MOUSE_BUTTON_LAST)
                    || (type == Type.KEY && code > GLFW.GLFW_KEY_LAST)) {
                return null;
            }
            return new Keybind(type, code);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
