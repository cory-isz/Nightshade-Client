package com.nightshade.module;

import com.google.gson.JsonElement;

import java.util.function.BooleanSupplier;

/**
 * Base class for every configurable module value. Settings know their owning module so that any change is
 * persisted automatically through the ConfigManager's dirty tracking.
 */
public abstract class ModuleSetting<T> {
    private final String name;
    private final String description;
    private final T defaultValue;
    protected T value;
    private Module owner;
    private BooleanSupplier visibility = () -> true;
    private Runnable onChange = () -> {
    };

    protected ModuleSetting(String name, String description, T defaultValue) {
        this.name = name;
        this.description = description;
        this.defaultValue = defaultValue;
        this.value = defaultValue;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public T get() {
        return value;
    }

    public T getDefault() {
        return defaultValue;
    }

    /** Sets the value after validation; persists and notifies only on actual change. */
    public void set(T newValue) {
        T validated = validate(newValue);
        if (validated == null || validated.equals(value)) {
            return;
        }
        value = validated;
        onChange.run();
        if (owner != null) {
            owner.markDirty();
        }
    }

    public void reset() {
        set(defaultValue);
    }

    public boolean isDefault() {
        return value.equals(defaultValue);
    }

    public boolean isVisible() {
        return visibility.getAsBoolean();
    }

    @SuppressWarnings("unchecked")
    public <S extends ModuleSetting<T>> S visibleWhen(BooleanSupplier supplier) {
        this.visibility = supplier;
        return (S) this;
    }

    @SuppressWarnings("unchecked")
    public <S extends ModuleSetting<T>> S onChange(Runnable runnable) {
        this.onChange = runnable;
        return (S) this;
    }

    void setOwner(Module owner) {
        this.owner = owner;
    }

    /** Returns a valid value derived from the input, or null if it must be rejected. */
    protected abstract T validate(T input);

    public abstract JsonElement toJson();

    /** Reads a value from JSON. Invalid input leaves the current value untouched and returns false. */
    public abstract boolean fromJson(JsonElement element);

    /** Applies a loaded value without marking the module dirty (used while loading configuration). */
    protected void load(T newValue) {
        T validated = validate(newValue);
        if (validated != null) {
            value = validated;
            onChange.run();
        }
    }
}
