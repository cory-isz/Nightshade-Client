package com.nightshade.module;

import com.nightshade.NightshadeClient;
import com.nightshade.event.EventManager;
import com.nightshade.event.Listener;
import com.nightshade.input.Keybind;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Shared base for every client feature.
 * <p>
 * Modules declare their event listeners in the constructor through {@link #listen}. The listeners are subscribed
 * only while the module is enabled and are always unsubscribed on disable, so a disabled module costs nothing at
 * runtime. State transitions are performed exclusively by the {@link com.nightshade.manager.ModuleManager}.
 */
public abstract class Module {
    protected static final Minecraft mc = Minecraft.getInstance();

    private final String id;
    private final String name;
    private final String description;
    private final ModuleCategory category;

    private final List<ModuleSetting<?>> settings = new ArrayList<>();
    private final List<DeclaredListener<?>> declaredListeners = new ArrayList<>();
    private final List<EventManager.Subscription<?>> activeSubscriptions = new ArrayList<>();
    private final Set<Class<? extends Module>> requires = new LinkedHashSet<>();
    private final Set<Class<? extends Module>> conflicts = new LinkedHashSet<>();
    private final Set<String> incompatibleMods = new LinkedHashSet<>();

    private boolean enabled;
    private boolean enabledByDefault;
    private Keybind keybind = Keybind.NONE;
    private Keybind defaultKeybind = Keybind.NONE;
    private BindMode bindMode = BindMode.TOGGLE;
    private BindMode defaultBindMode = BindMode.TOGGLE;

    protected Module(String name, String description, ModuleCategory category) {
        this.id = toId(name);
        this.name = name;
        this.description = description;
        this.category = category;
    }

    private static String toId(String name) {
        return name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_").replaceAll("^_|_$", "");
    }

    // ------------------------------------------------------------------ declaration helpers

    protected final <S extends ModuleSetting<?>> S add(S setting) {
        for (ModuleSetting<?> existing : settings) {
            if (existing.getName().equals(setting.getName())) {
                throw new IllegalStateException("Duplicate setting '" + setting.getName() + "' in " + name);
            }
        }
        setting.setOwner(this);
        settings.add(setting);
        return setting;
    }

    protected final <E> void listen(Class<E> type, Listener<E> listener) {
        declaredListeners.add(new DeclaredListener<>(type, listener));
    }

    protected final void defaultKeybind(Keybind bind) {
        this.defaultKeybind = bind;
        this.keybind = bind;
    }

    protected final void defaultBindMode(BindMode mode) {
        this.defaultBindMode = mode;
        this.bindMode = mode;
    }

    /** Marks a module as enabled on first launch. */
    protected final void enabledByDefault() {
        this.enabledByDefault = true;
    }

    public boolean isEnabledByDefault() {
        return enabledByDefault;
    }

    protected final void requires(Class<? extends Module> module) {
        requires.add(module);
    }

    protected final void conflictsWith(Class<? extends Module> module) {
        conflicts.add(module);
    }

    protected final void incompatibleWithMod(String modId) {
        incompatibleMods.add(modId);
    }

    // ------------------------------------------------------------------ lifecycle (ModuleManager only)

    /** Subscribes declared listeners then runs {@link #onEnable()}. Rolls back subscriptions on failure. */
    public final void activate(EventManager events) {
        for (DeclaredListener<?> declared : declaredListeners) {
            activeSubscriptions.add(declared.subscribe(events, this));
        }
        try {
            onEnable();
        } catch (RuntimeException e) {
            unsubscribeAll(events);
            throw e;
        }
        enabled = true;
    }

    /** Runs {@link #onDisable()} and always unsubscribes every listener, even if onDisable throws. */
    public final void deactivate(EventManager events) {
        enabled = false;
        try {
            onDisable();
        } finally {
            unsubscribeAll(events);
        }
    }

    private void unsubscribeAll(EventManager events) {
        for (EventManager.Subscription<?> subscription : activeSubscriptions) {
            events.unsubscribe(subscription);
        }
        activeSubscriptions.clear();
    }

    protected void onEnable() {
    }

    protected void onDisable() {
    }

    /** Action modules (e.g. "open GUI") are not toggled; their bind runs {@link #onAction()} instead. */
    public boolean isToggleable() {
        return true;
    }

    public void onAction() {
    }

    /**
     * Returns a human readable reason why this module cannot currently be enabled, or null when compatible.
     * Subclasses may extend this with runtime checks.
     */
    @Nullable
    public String getCompatibilityIssue() {
        for (String modId : incompatibleMods) {
            if (FabricLoader.getInstance().isModLoaded(modId)) {
                return "Incompatible with installed mod '" + modId + "'";
            }
        }
        return null;
    }

    public void resetSettings() {
        for (ModuleSetting<?> setting : settings) {
            setting.reset();
        }
        markDirty();
    }

    public final void markDirty() {
        NightshadeClient.client().config().markDirty(this);
    }

    protected static boolean inGame() {
        return mc.player != null && mc.level != null && mc.gameMode != null;
    }

    // ------------------------------------------------------------------ accessors

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public ModuleCategory getCategory() {
        return category;
    }

    @Nullable

    public boolean isEnabled() {
        return enabled;
    }

    public List<ModuleSetting<?>> getSettings() {
        return Collections.unmodifiableList(settings);
    }

    public Keybind getKeybind() {
        return keybind;
    }

    public Keybind getDefaultKeybind() {
        return defaultKeybind;
    }

    /** Raw setter used by KeybindManager and ConfigManager; does not perform conflict handling. */
    public void setKeybindRaw(Keybind keybind) {
        this.keybind = keybind == null ? Keybind.NONE : keybind;
    }

    public BindMode getBindMode() {
        return bindMode;
    }

    public BindMode getDefaultBindMode() {
        return defaultBindMode;
    }

    public void setBindMode(BindMode mode) {
        if (mode != null && mode != bindMode) {
            bindMode = mode;
            markDirty();
        }
    }

    public Set<Class<? extends Module>> getRequirements() {
        return Collections.unmodifiableSet(requires);
    }

    public Set<Class<? extends Module>> getConflicts() {
        return Collections.unmodifiableSet(conflicts);
    }

    @Override
    public String toString() {
        return name;
    }

    private record DeclaredListener<E>(Class<E> type, Listener<E> listener) {
        EventManager.Subscription<E> subscribe(EventManager events, Module owner) {
            return events.subscribe(type, listener, owner);
        }
    }
}
