package com.nightshade.module.setting;

import com.nightshade.module.ModuleSetting;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

import java.util.Locale;

/** A bounded numeric setting snapped to a step. */
public final class NumberSetting extends ModuleSetting<Double> {
    private final double min;
    private final double max;
    private final double step;
    private final String suffix;

    public NumberSetting(String name, String description, double defaultValue, double min, double max, double step) {
        this(name, description, defaultValue, min, max, step, "");
    }

    public NumberSetting(String name, String description, double defaultValue, double min, double max, double step, String suffix) {
        super(name, description, defaultValue);
        if (min >= max || step <= 0) {
            throw new IllegalArgumentException("Invalid number setting bounds for " + name);
        }
        this.min = min;
        this.max = max;
        this.step = step;
        this.suffix = suffix;
    }

    public double min() {
        return min;
    }

    public double max() {
        return max;
    }

    public double step() {
        return step;
    }

    public int intValue() {
        return (int) Math.round(value);
    }

    public float floatValue() {
        return value.floatValue();
    }

    /** Sets from a 0..1 slider fraction. */
    public void setFraction(double fraction) {
        set(min + (max - min) * Math.max(0.0, Math.min(1.0, fraction)));
    }

    public double getFraction() {
        return (value - min) / (max - min);
    }

    public String format() {
        boolean integral = step >= 1.0 && Math.abs(step - Math.rint(step)) < 1e-9;
        String number = integral ? Integer.toString(intValue())
                : String.format(Locale.ROOT, step >= 0.1 ? "%.1f" : "%.2f", value);
        return number + suffix;
    }

    @Override
    protected Double validate(Double input) {
        if (input == null || input.isNaN() || input.isInfinite()) {
            return null;
        }
        double clamped = Math.max(min, Math.min(max, input));
        double snapped = min + Math.round((clamped - min) / step) * step;
        snapped = Math.max(min, Math.min(max, snapped));
        // Remove floating point noise so equality checks and saved JSON stay clean.
        return Math.round(snapped * 1_000_000.0) / 1_000_000.0;
    }

    @Override
    public JsonElement toJson() {
        return new JsonPrimitive(value);
    }

    @Override
    public boolean fromJson(JsonElement element) {
        if (element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) {
            load(element.getAsDouble());
            return true;
        }
        return false;
    }
}
