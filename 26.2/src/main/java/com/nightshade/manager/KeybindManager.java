package com.nightshade.manager;

import com.mojang.blaze3d.platform.InputConstants;
import com.nightshade.event.events.KeyEvent;
import com.nightshade.event.events.MouseButtonEvent;
import com.nightshade.event.events.TickEvent;
import com.nightshade.input.Keybind;
import com.nightshade.module.BindMode;
import com.nightshade.module.Module;
import com.nightshade.module.interfaces.ClickGuiModule;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import net.minecraft.client.Minecraft;

/**
 * Dispatches key and mouse input to module keybinds. Supports toggle and hold modes, keyboard and mouse binds,
 * conflict detection with a configurable policy, clearing and reassignment.
 */
public final class KeybindManager {
    public enum ConflictMode {
        /** Allow duplicate binds and show a warning. */
        WARN,
        /** Assigning a bind removes it from any other module. */
        REPLACE
    }

    private final ClientManager client;
    private final Minecraft mc = Minecraft.getInstance();
    /** HOLD-mode modules that were enabled by pressing their bind (only these are auto-released). */
    private final Set<Module> heldByBind = new HashSet<>();

    public KeybindManager(ClientManager client) {
        this.client = client;
    }

    public void init() {
        client.events().subscribe(KeyEvent.class, this::onKey, this);
        client.events().subscribe(MouseButtonEvent.class, this::onMouse, this);
        client.events().subscribe(TickEvent.Post.class, event -> releaseStaleHolds(), this);
    }

    private void onKey(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_UNKNOWN) {
            return;
        }
        if (event.action() == GLFW.GLFW_PRESS) {
            if (canTrigger() && !InputConstants.isKeyDown(mc.getWindow(), GLFW.GLFW_KEY_F3)) {
                dispatchPress(Keybind.Type.KEY, event.key());
            }
        } else if (event.action() == GLFW.GLFW_RELEASE) {
            dispatchRelease(Keybind.Type.KEY, event.key());
        }
    }

    private void onMouse(MouseButtonEvent event) {
        if (event.action() == GLFW.GLFW_PRESS) {
            if (canTrigger()) {
                dispatchPress(Keybind.Type.MOUSE, event.button());
            }
        } else if (event.action() == GLFW.GLFW_RELEASE) {
            dispatchRelease(Keybind.Type.MOUSE, event.button());
        }
    }

    /** Binds only fire in-game with no screen open, so typing in chat or menus never toggles modules. */
    private boolean canTrigger() {
        return mc.gui.screen() == null && mc.gui.overlay() == null;
    }

    /** Key whose press is being dispatched right now (screens opened by a bind use it to ignore that same press). */
    private static int lastDispatchedKey = -1;
    private static long lastDispatchedAt;

    /** True if {@code key} was dispatched as a bind press within the last half second. */
    public static boolean wasJustPressed(int key) {
        return key == lastDispatchedKey && System.currentTimeMillis() - lastDispatchedAt < 500L;
    }

    private void dispatchPress(Keybind.Type type, int code) {
        if (type == Keybind.Type.KEY) {
            lastDispatchedKey = code;
            lastDispatchedAt = System.currentTimeMillis();
        }
        for (Module module : matching(type, code)) {
            if (!module.isToggleable()) {
                try {
                    module.onAction();
                } catch (Throwable t) {
                    client.modules().handleModuleFailure(module, t);
                }
            } else if (module.getBindMode() == BindMode.HOLD) {
                if (!module.isEnabled() && client.modules().setEnabled(module, true)) {
                    heldByBind.add(module);
                }
            } else {
                client.modules().toggle(module);
            }
        }
    }

    private void dispatchRelease(Keybind.Type type, int code) {
        for (Module module : matching(type, code)) {
            if (heldByBind.remove(module) && module.isEnabled()) {
                client.modules().setEnabled(module, false);
            }
        }
    }

    /**
     * Releases hold binds whose key is no longer physically down, e.g. when the key was released while the window
     * was unfocused and GLFW never delivered the release event.
     */
    private void releaseStaleHolds() {
        if (heldByBind.isEmpty()) {
            return;
        }
        Iterator<Module> iterator = heldByBind.iterator();
        while (iterator.hasNext()) {
            Module module = iterator.next();
            if (!module.isEnabled()) {
                iterator.remove();
            } else if (!isPhysicallyDown(module.getKeybind())) {
                iterator.remove();
                client.modules().setEnabled(module, false);
            }
        }
    }

    public boolean isPhysicallyDown(Keybind bind) {
        long handle = mc.getWindow().handle();
        return switch (bind.type()) {
            case KEY -> InputConstants.isKeyDown(mc.getWindow(), bind.code());
            case MOUSE -> GLFW.glfwGetMouseButton(handle, bind.code()) == GLFW.GLFW_PRESS;
            case NONE -> false;
        };
    }

    private List<Module> matching(Keybind.Type type, int code) {
        List<Module> result = new ArrayList<>(2);
        for (Module module : client.modules().getModules()) {
            Keybind bind = module.getKeybind();
            if (bind.type() == type && bind.code() == code) {
                result.add(module);
            }
        }
        return result;
    }

    // ------------------------------------------------------------------ assignment

    public ConflictMode conflictMode() {
        ClickGuiModule gui = client.modules().get(ClickGuiModule.class);
        return gui != null && gui.replaceConflictingBinds() ? ConflictMode.REPLACE : ConflictMode.WARN;
    }

    /** Modules other than {@code module} that share its current bind. */
    public List<Module> getConflicts(Module module) {
        return getConflicts(module, module.getKeybind());
    }

    public List<Module> getConflicts(Module module, Keybind bind) {
        List<Module> result = new ArrayList<>();
        if (!bind.isBound()) {
            return result;
        }
        for (Module other : client.modules().getModules()) {
            if (other != module && other.getKeybind().equals(bind)) {
                result.add(other);
            }
        }
        return result;
    }

    /**
     * Assigns a bind applying the conflict policy. Returns the modules that still share the bind afterwards
     * (empty in REPLACE mode).
     */
    public List<Module> assign(Module module, Keybind bind) {
        Keybind target = bind == null ? Keybind.NONE : bind;
        List<Module> conflicts = getConflicts(module, target);
        if (!conflicts.isEmpty() && conflictMode() == ConflictMode.REPLACE) {
            for (Module other : conflicts) {
                other.setKeybindRaw(Keybind.NONE);
                other.markDirty();
                client.notifications().info("Keybind", target.displayName() + " removed from " + other.getName());
            }
            conflicts.clear();
        } else if (!conflicts.isEmpty()) {
            client.notifications().warn("Keybind conflict", target.displayName() + " is also bound to " + conflicts.get(0).getName());
        }
        if (!module.getKeybind().equals(target)) {
            module.setKeybindRaw(target);
            module.markDirty();
        }
        return conflicts;
    }

    public void clear(Module module) {
        assign(module, Keybind.NONE);
    }
}
