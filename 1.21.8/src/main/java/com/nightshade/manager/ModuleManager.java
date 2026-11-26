package com.nightshade.manager;

import com.nightshade.event.events.ModuleToggleEvent;
import com.nightshade.module.HudModule;
import com.nightshade.module.Module;
import com.nightshade.module.ModuleCategory;
import com.nightshade.util.Log;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;

/**
 * Central registry and state machine for modules: registration, activation, dependency/conflict resolution,
 * category master switches, filtering and search.
 */
public final class ModuleManager {
    private final ClientManager client;
    private final Map<Class<? extends Module>, Module> byClass = new LinkedHashMap<>();
    private final Map<String, Module> byId = new LinkedHashMap<>();
    private final List<Module> modules = new ArrayList<>();
    private final List<HudModule> hudModules = new ArrayList<>();
    private final Map<ModuleCategory, Boolean> categoryEnabled = new EnumMap<>(ModuleCategory.class);
    /** Modules that are logically enabled but held inactive because their category switch is off. */
    private final Set<Module> suspended = new HashSet<>();
    private final Set<Module> transitioning = new HashSet<>();
    private boolean locked;

    public ModuleManager(ClientManager client) {
        this.client = client;
        for (ModuleCategory category : ModuleCategory.values()) {
            categoryEnabled.put(category, true);
        }
    }

    // ------------------------------------------------------------------ registration

    public void register(Module module) {
        if (locked) {
            throw new IllegalStateException("Module registration is closed: " + module.getName());
        }
        if (byClass.containsKey(module.getClass())) {
            throw new IllegalStateException("Module class registered twice: " + module.getClass().getName());
        }
        if (byId.containsKey(module.getId())) {
            throw new IllegalStateException("Duplicate module id '" + module.getId() + "'");
        }
        byClass.put(module.getClass(), module);
        byId.put(module.getId(), module);
        modules.add(module);
        if (module instanceof HudModule hud) {
            hudModules.add(hud);
        }
    }

    /** Closes registration and verifies that every declared dependency/conflict refers to a registered module. */
    public void lockAndValidate() {
        for (Module module : modules) {
            for (Class<? extends Module> required : module.getRequirements()) {
                if (!byClass.containsKey(required)) {
                    throw new IllegalStateException(module.getName() + " requires unregistered module " + required.getSimpleName());
                }
            }
            for (Class<? extends Module> conflict : module.getConflicts()) {
                if (!byClass.containsKey(conflict)) {
                    throw new IllegalStateException(module.getName() + " conflicts with unregistered module " + conflict.getSimpleName());
                }
            }
        }
        modules.sort(Comparator.comparing(Module::getName, String.CASE_INSENSITIVE_ORDER));
        locked = true;
        Log.info("Registered {} modules", modules.size());
    }

    // ------------------------------------------------------------------ queries

    public List<Module> getModules() {
        return Collections.unmodifiableList(modules);
    }

    public List<HudModule> getHudModules() {
        return Collections.unmodifiableList(hudModules);
    }

    @SuppressWarnings("unchecked")
    public <T extends Module> T get(Class<T> type) {
        return (T) byClass.get(type);
    }

    @Nullable
    public Module getById(String id) {
        return byId.get(id);
    }

    public List<Module> getByCategory(ModuleCategory category) {
        List<Module> result = new ArrayList<>();
        for (Module module : modules) {
            if (module.getCategory() == category) {
                result.add(module);
            }
        }
        return result;
    }

    /**
     * Filters modules by optional category and a case-insensitive query matched against name
     * and description.
     */
    public List<Module> search(String query, @Nullable ModuleCategory category) {
        String needle = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        List<Module> result = new ArrayList<>();
        for (Module module : modules) {
            if (category != null && module.getCategory() != category) {
                continue;
            }
            if (!needle.isEmpty()
                    && !module.getName().toLowerCase(Locale.ROOT).contains(needle)
                    && !module.getDescription().toLowerCase(Locale.ROOT).contains(needle)) {
                continue;
            }
            result.add(module);
        }
        return result;
    }

    public int countEnabled(@Nullable ModuleCategory category) {
        int count = 0;
        for (Module module : modules) {
            if (module.isEnabled() && (category == null || module.getCategory() == category)) {
                count++;
            }
        }
        return count;
    }

    // ------------------------------------------------------------------ state transitions

    public boolean toggle(Module module) {
        if (suspended.contains(module)) {
            // Toggling a suspended module clears it so it does not come back when its category is re-enabled.
            suspended.remove(module);
            client.config().markDirty(module);
            client.notifications().info(module.getName(), "Will stay off when the category is re-enabled");
            return true;
        }
        return setEnabled(module, !module.isEnabled());
    }

    /**
     * Enables or disables a module, resolving requirements and conflicts. Returns true on success. Failures are
     * reported through notifications and never thrown.
     */
    public boolean setEnabled(Module module, boolean enable) {
        if (!module.isToggleable() || module.isEnabled() == enable) {
            return module.isToggleable();
        }
        if (!transitioning.add(module)) {
            return false; // dependency cycle guard
        }
        try {
            return enable ? enable(module) : disable(module);
        } finally {
            transitioning.remove(module);
        }
    }

    private boolean enable(Module module) {
        if (!isCategoryEnabled(module.getCategory())) {
            client.notifications().warn(module.getName(), module.getCategory().displayName() + " category is switched off");
            return false;
        }
        String issue = module.getCompatibilityIssue();
        if (issue != null) {
            client.notifications().warn(module.getName(), issue);
            return false;
        }
        for (Class<? extends Module> requiredType : module.getRequirements()) {
            Module required = byClass.get(requiredType);
            if (required == null || !(required.isEnabled() || setEnabled(required, true))) {
                client.notifications().warn(module.getName(), "Requires " + (required == null ? requiredType.getSimpleName() : required.getName()));
                return false;
            }
        }
        for (Module other : modules) {
            if (other != module && other.isEnabled()
                    && (module.getConflicts().contains(other.getClass()) || other.getConflicts().contains(module.getClass()))) {
                setEnabled(other, false);
                client.notifications().info(other.getName(), "Disabled (conflicts with " + module.getName() + ")");
            }
        }
        try {
            module.activate(client.events());
        } catch (Throwable t) {
            Log.error("Failed to enable module " + module.getName(), t);
            client.notifications().error(module.getName(), "Failed to enable: " + describe(t));
            return false;
        }
        client.events().post(new ModuleToggleEvent(module, true));
        client.config().markDirty(module);
        return true;
    }

    private boolean disable(Module module) {
        // Disable dependents first so nothing runs without its requirement.
        for (Module other : modules) {
            if (other.isEnabled() && other.getRequirements().contains(module.getClass())) {
                setEnabled(other, false);
                client.notifications().info(other.getName(), "Disabled (requires " + module.getName() + ")");
            }
        }
        try {
            module.deactivate(client.events());
        } catch (Throwable t) {
            Log.error("Error while disabling module " + module.getName(), t);
        }
        client.events().post(new ModuleToggleEvent(module, false));
        client.config().markDirty(module);
        return true;
    }

    /** Called when a module's listener or renderer throws: logs, disables the module and informs the user. */
    public void handleModuleFailure(Module module, Throwable throwable) {
        Log.error("Module " + module.getName() + " threw an exception and will be disabled", throwable);
        Minecraft.getInstance().execute(() -> {
            if (module.isEnabled()) {
                setEnabled(module, false);
                client.notifications().error(module.getName(), "Crashed and was disabled: " + describe(throwable));
            }
        });
    }

    private static String describe(Throwable t) {
        String message = t.getMessage();
        return t.getClass().getSimpleName() + (message == null ? "" : ": " + message);
    }

    // ------------------------------------------------------------------ category switches

    public boolean isCategoryEnabled(ModuleCategory category) {
        return categoryEnabled.getOrDefault(category, true);
    }

    public boolean isSuspended(Module module) {
        return suspended.contains(module);
    }

    public void setCategoryEnabled(ModuleCategory category, boolean enabled) {
        if (isCategoryEnabled(category) == enabled) {
            return;
        }
        if (!enabled) {
            for (Module module : getByCategory(category)) {
                if (module.isEnabled()) {
                    setEnabled(module, false);
                    suspended.add(module);
                    client.config().markDirty(module);
                }
            }
            categoryEnabled.put(category, false);
        } else {
            categoryEnabled.put(category, true);
            for (Module module : getByCategory(category)) {
                if (suspended.remove(module)) {
                    setEnabled(module, true);
                }
            }
        }
        client.config().markClientDirty();
    }

    /** Used by the config loader to restore state without side effects. */
    void restoreCategoryState(ModuleCategory category, boolean enabled) {
        categoryEnabled.put(category, enabled);
    }

    void restoreSuspended(Module module) {
        suspended.add(module);
    }

    /** Releases all module resources at shutdown without changing persisted state. */
    public void shutdown() {
        for (Module module : modules) {
            if (module.isEnabled()) {
                try {
                    module.deactivate(client.events());
                } catch (Throwable t) {
                    Log.error("Error shutting down module " + module.getName(), t);
                }
            }
        }
    }
}
