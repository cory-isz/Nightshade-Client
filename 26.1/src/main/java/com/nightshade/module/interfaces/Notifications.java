package com.nightshade.module.interfaces;

import com.nightshade.util.EffectProbe;
import com.nightshade.NightshadeClient;
import com.nightshade.event.events.HudRenderEvent;
import com.nightshade.event.events.ModuleToggleEvent;
import com.nightshade.module.Module;
import com.nightshade.module.ModuleCategory;
import com.nightshade.module.setting.BooleanSetting;
import com.nightshade.module.setting.ModeSetting;
import com.nightshade.render.ColorUtil;
import com.nightshade.render.Fonts;
import com.nightshade.render.RenderUtil;
import com.nightshade.render.Theme;
import com.nightshade.util.Notification;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Displays client toasts (module toggles, warnings, errors). */
public final class Notifications extends Module {
    private static Notifications instance;
    private final BooleanSetting toggleMessages = add(new BooleanSetting("Module Toggles", "Show a toast when a module is toggled", true));
    private final ModeSetting position = add(new ModeSetting("Position", "Screen corner", "Bottom Right", "Bottom Right", "Top Right"));

    public Notifications() {
        super("Notifications", "Shows toasts for module toggles and client messages", ModuleCategory.INTERFACE);
        enabledByDefault();
        instance = this;
        listen(ModuleToggleEvent.class, event -> {
            if (toggleMessages.get() && event.module().getCategory() != ModuleCategory.INTERFACE) {
                NightshadeClient.client().notifications().push(event.module().getName(),
                        event.enabled() ? "Enabled" : "Disabled",
                        event.enabled() ? Notification.Type.SUCCESS : Notification.Type.INFO);
            }
        });
        listen(HudRenderEvent.class, event -> {
            if (mc.screen == null) {
                renderAll(event.context());
            }
        });
    }

    /** Renders notifications if the module is enabled; screens call this so toasts stay visible in menus. */
    public static void renderIfEnabled(GuiGraphicsExtractor context) {
        Notifications module = instance;
        if (module != null && module.isEnabled()) {
            module.renderAll(context);
        }
    }

    private void renderAll(GuiGraphicsExtractor context) {
        List<Notification> list = NightshadeClient.client().notifications().snapshot();
        if (list.isEmpty()) {
            return;
        }
        EffectProbe.hit("Notifications");
        // In menus toasts go to the top right (below the main menu's profile chip) so they never cover buttons.
        boolean inMenu = mc.screen != null;
        boolean bottom = !inMenu && position.is("Bottom Right");
        int screenW = context.guiWidth();
        int screenH = context.guiHeight();
        int height = Fonts.height() * 2 + 10;
        boolean mainMenu = mc.screen instanceof com.nightshade.gui.menu.NightshadeMenuScreen;
        int y = bottom ? screenH - height - 6 : mainMenu ? 52 : 4;
        long now = System.currentTimeMillis();
        int[] avoid = inMenu ? null : moduleListBounds(screenW, screenH);
        context.pose().pushMatrix();
        // Menus show only the newest toast so stacked messages never cover a screen's controls.
        int oldest = inMenu ? list.size() - 1 : 0;
        for (int i = list.size() - 1; i >= oldest; i--) {
            Notification notification = list.get(i);
            float slide = notification.slide().update();
            int width = Math.max(110, Math.max(Fonts.width(notification.title()), Fonts.width(notification.message())) + 18);
            int rightEdge = screenW;
            if (avoid != null && y < avoid[3] && y + height > avoid[1] && screenW - width - 6 < avoid[2] && avoid[0] < screenW - 6) {
                rightEdge = avoid[0] - 2; // step left of the Module List instead of covering it
            }
            int x = rightEdge - Math.round((width + 6) * slide);
            int accent = switch (notification.type()) {
                case SUCCESS -> Theme.SUCCESS;
                case WARNING -> Theme.WARNING;
                case ERROR -> Theme.DANGER;
                default -> Theme.accent();
            };
            RenderUtil.roundedRect(context, x, y, x + width, y + height, 4, ColorUtil.fade(Theme.BACKGROUND, slide));
            RenderUtil.roundedOutline(context, x, y, x + width, y + height, 4, ColorUtil.fade(Theme.BORDER, slide));
            RenderUtil.rect(context, x + 1, y + 4, x + 3, y + height - 4, ColorUtil.fade(accent, slide));
            Fonts.draw(context, notification.title(), x + 9, y + 4, ColorUtil.fade(Theme.TEXT, slide), false);
            Fonts.draw(context, notification.message(), x + 9, y + 5 + Fonts.height(), ColorUtil.fade(Theme.TEXT_MUTED, slide), false);
            float life = notification.lifeFraction(now);
            RenderUtil.rect(context, x + 4, y + height - 2, x + 4 + Math.round((width - 8) * life), y + height - 1, ColorUtil.fade(accent, slide * 0.7f));
            y += bottom ? -(height + 4) : height + 4;
        }
        context.pose().popMatrix();
    }

    /** Screen rectangle {x1, y1, x2, y2} of the Module List HUD when it is visible, else null. */
    private static int[] moduleListBounds(int screenW, int screenH) {
        com.nightshade.module.hud.ModuleListHud list = NightshadeClient.client().modules().get(com.nightshade.module.hud.ModuleListHud.class);
        if (list == null || !list.isEnabled() || mc.options.hideGui) {
            return null;
        }
        int x = list.getScreenX(screenW);
        int y = list.getScreenY(screenH);
        return new int[]{x, y, x + list.getScaledWidth(), y + list.getScaledHeight()};
    }

    @Override
    protected void onEnable() {
        instance = this;
    }
}
