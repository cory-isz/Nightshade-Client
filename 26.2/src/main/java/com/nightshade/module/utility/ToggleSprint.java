package com.nightshade.module.utility;

import com.nightshade.util.EffectProbe;
import com.mojang.blaze3d.platform.InputConstants;
import com.nightshade.event.events.KeyEvent;
import com.nightshade.event.events.MouseButtonEvent;
import com.nightshade.event.events.TickEvent;
import com.nightshade.event.events.WorldChangeEvent;
import com.nightshade.input.KeyState;
import com.nightshade.module.Module;
import com.nightshade.module.ModuleCategory;
import com.nightshade.module.setting.BooleanSetting;
import org.lwjgl.glfw.GLFW;

/**
 * Keeps the sprint key logically held and optionally turns the sneak key into a toggle, like holding the keys
 * down yourself. Physical key state is restored when disabled.
 */
public final class ToggleSprint extends Module {
    private final BooleanSetting sprint = add(new BooleanSetting("Toggle Sprint", "Keep sprint held", true));
    private final BooleanSetting sneak = add(new BooleanSetting("Toggle Sneak", "Pressing sneak toggles sneaking", false));
    private boolean sneakToggled;

    public ToggleSprint() {
        super("Toggle Sprint", "Toggle sprint and sneak instead of holding the keys", ModuleCategory.UTILITY);
        listen(TickEvent.Pre.class, event -> onTick());
        listen(KeyEvent.class, event -> {
            if (event.action() == GLFW.GLFW_PRESS && mc.gui.screen() == null
                    && KeyState.isBoundTo(mc.options.keyShift, InputConstants.Type.KEYSYM, event.key())) {
                sneakToggled = !sneakToggled;
            }
        });
        listen(MouseButtonEvent.class, event -> {
            if (event.action() == GLFW.GLFW_PRESS && mc.gui.screen() == null
                    && KeyState.isBoundTo(mc.options.keyShift, InputConstants.Type.MOUSE, event.button())) {
                sneakToggled = !sneakToggled;
            }
        });
        listen(WorldChangeEvent.class, event -> sneakToggled = false);
    }

    private void onTick() {
        if (mc.player == null) {
            return;
        }
        if (sprint.get()) {
            mc.options.keySprint.setDown(true);
            EffectProbe.hit("Toggle Sprint");
        }
        if (sneak.get()) {
            if (sneakToggled && mc.gui.screen() == null) {
                mc.options.keyShift.setDown(true);
            } else if (!sneakToggled) {
                // Leave physical state alone when not toggled.
                KeyState.restore(mc.options.keyShift);
            }
        }
    }

    @Override
    protected void onDisable() {
        sneakToggled = false;
        KeyState.restore(mc.options.keySprint);
        KeyState.restore(mc.options.keyShift);
    }

    public boolean isSneakToggled() {
        return sneakToggled;
    }
}
