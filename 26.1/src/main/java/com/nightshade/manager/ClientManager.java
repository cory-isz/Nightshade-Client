package com.nightshade.manager;

import com.nightshade.render.BrandLogo;
import com.nightshade.account.AccountManager;
import com.nightshade.event.EventManager;
import com.nightshade.mods.ModFileManager;
import com.nightshade.event.events.BlockOutlineEvent;
import com.nightshade.event.events.HudRenderEvent;
import com.nightshade.event.events.TickEvent;
import com.nightshade.event.events.WorldChangeEvent;
import com.nightshade.event.events.WorldRenderEvent;
import com.nightshade.gui.TitleScreenBadge;
import com.nightshade.hud.HudManager;
import com.nightshade.input.CpsTracker;
import com.nightshade.module.Module;
import com.nightshade.module.ModuleRegistry;
import com.nightshade.util.FrameTimeTracker;
import com.nightshade.util.Log;
import com.nightshade.util.NotificationManager;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

/**
 * Central controller. Owns every subsystem, defines initialisation order and bridges Fabric API callbacks into
 * the client event bus.
 * <p>
 * Initialisation order: events -> services -> module registration -> config load (restores module state)
 * -> keybinds -> Fabric hooks. Shutdown saves configuration and then releases module resources.
 */
public final class ClientManager {
    private final Minecraft mc = Minecraft.getInstance();
    private final HudRenderEvent hudRenderEvent = new HudRenderEvent();
    /** Total time spent drawing the HUD and frames drawn, for the HUD benchmark. */
    public static long hudNanos;
    public static long hudFrames;
    /** Total time Nightshade spends in its end-of-tick work, and ticks counted, for the benchmark. */
    public static long tickNanos;
    public static long ticks;
    private final WorldRenderEvent worldRenderEvent = new WorldRenderEvent();

    private EventManager events;
    private ModuleManager modules;
    private ConfigManager config;
    private KeybindManager keybinds;
    private HudManager hud;
    private NotificationManager notifications;
    private CpsTracker cps;
    private FrameTimeTracker frameTimes;
    private AccountManager accounts;
    private ModFileManager mods;
    private ProfileManager profiles;
    private com.nightshade.waypoint.WaypointManager waypoints;
    private boolean initialized;

    public void initialize() {
        if (initialized) {
            throw new IllegalStateException("ClientManager already initialised");
        }
        long start = System.nanoTime();
        events = new EventManager(this::onListenerError);
        notifications = new NotificationManager();
        cps = new CpsTracker();
        frameTimes = new FrameTimeTracker();
        modules = new ModuleManager(this);
        config = new ConfigManager(this, FabricLoader.getInstance().getConfigDir().resolve("nightshade"));
        keybinds = new KeybindManager(this);
        hud = new HudManager(this);
        accounts = new AccountManager(config.getRoot(), notifications);
        mods = new ModFileManager(config.getRoot(), notifications);
        profiles = new ProfileManager(this, config.getRoot());
        waypoints = new com.nightshade.waypoint.WaypointManager(config, config.getRoot());

        ModuleRegistry.registerAll(modules);
        modules.lockAndValidate();
        config.loadAll();
        keybinds.init();
        profiles.init();
        registerFabricHooks();
        initialized = true;
        Log.info("Nightshade Client initialised in {} ms ({} modules, {} enabled)",
                (System.nanoTime() - start) / 1_000_000, modules.getModules().size(), modules.countEnabled(null));
    }

    private void registerFabricHooks() {
        ClientTickEvents.START_CLIENT_TICK.register(client -> events.post(TickEvent.Pre.INSTANCE));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            long tickStart = System.nanoTime();
            events.post(TickEvent.Post.INSTANCE);
            tickNanos += System.nanoTime() - tickStart;
            ticks++;
            config.tick();
        });
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("nightshade", "hud"), (context, tickCounter) -> {
            frameTimes.onFrame();
            if (mc.options.hideGui) {
                return;
            }
            float tickDelta = tickCounter.getGameTimeDeltaPartialTick(true);
            long start = System.nanoTime();
            hud.render(context, tickDelta);
            events.post(hudRenderEvent.set(context, tickDelta));
            hudNanos += System.nanoTime() - start;
            hudFrames++;
        });
        LevelRenderEvents.END_MAIN.register(context -> events.post(worldRenderEvent.set(context)));
        LevelRenderEvents.BEFORE_BLOCK_OUTLINE.register((context, outline) ->
                !events.hasListeners(BlockOutlineEvent.class) || !events.post(new BlockOutlineEvent(context, outline)).isCancelled());
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> client.execute(() -> {
            com.nightshade.util.LastPlayed.recordJoin(client);
            com.nightshade.module.interfaces.ServerTips.onJoin();
        }));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(() -> {
            cps.reset();
            events.post(new WorldChangeEvent(null));
        }));
        ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
            BrandLogo.applyWindowIcon(client);
            accounts.restoreActive();
        });
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> shutdown());
        TitleScreenBadge.register();
        com.nightshade.gui.ScreenTransitions.register();
    }

    private void onListenerError(Object owner, Throwable throwable) {
        if (owner instanceof Module module) {
            modules.handleModuleFailure(module, throwable);
        } else {
            Log.error("Event listener of " + owner.getClass().getSimpleName() + " failed", throwable);
        }
    }

    private void shutdown() {
        Log.info("Saving configuration and shutting down");
        try {
            config.saveAll();
        } catch (Throwable t) {
            Log.error("Failed to save configuration on shutdown", t);
        }
        modules.shutdown();
        mods.onShutdown();
        notifications.clear();
    }

    public EventManager events() {
        return events;
    }

    public ModuleManager modules() {
        return modules;
    }

    public ConfigManager config() {
        return config;
    }

    public KeybindManager keybinds() {
        return keybinds;
    }

    public HudManager hud() {
        return hud;
    }

    public NotificationManager notifications() {
        return notifications;
    }

    public CpsTracker cps() {
        return cps;
    }

    public AccountManager accounts() {
        return accounts;
    }

    public com.nightshade.waypoint.WaypointManager waypoints() {
        return waypoints;
    }

    public ProfileManager profiles() {
        return profiles;
    }

    public ModFileManager mods() {
        return mods;
    }

    public FrameTimeTracker frameTimes() {
        return frameTimes;
    }

    public boolean isInitialized() {
        return initialized;
    }
}
