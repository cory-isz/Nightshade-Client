package com.nightshade.gui;

import com.nightshade.render.Fx;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.gui.screens.Screen;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Smooth screen changes: every Nightshade screen eases in over 0.2 s - it settles from a slight zoom while a dark
 * veil fades away - instead of popping in. Purely cosmetic and very cheap (one transform and one fill).
 */
public final class ScreenTransitions {
    private static final float DURATION = 0.2f;
    private static final Map<Screen, Long> OPENED = new WeakHashMap<>();
    private ScreenTransitions() {
    }

    private static float progress(Screen screen) {
        Long t = OPENED.get(screen);
        return t == null ? 1f : Math.min(1f, (System.nanoTime() - t) / 1e9f / DURATION);
    }

    public static void register() {
        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            if (!(screen instanceof NsScreen) || OPENED.containsKey(screen)) {
                return; // resizing re-inits a screen: do not replay the transition
            }
            OPENED.put(screen, System.nanoTime());
            ScreenEvents.beforeRender(screen).register((s, ctx, mx, my, d) -> {
                float p = progress(s);
                ctx.pose().pushMatrix();
                if (p < 1f && com.nightshade.module.performance.MenuOptimizer.allowTransitions()) {
                    float zoom = 1f + 0.025f * (1f - Fx.easeOutCubic(p));
                    float cx = s.width / 2f;
                    float cy = s.height / 2f;
                    ctx.pose().translate(cx, cy);
                    ctx.pose().scale(zoom, zoom);
                    ctx.pose().translate(-cx, -cy);
                }
            });
            ScreenEvents.afterRender(screen).register((s, ctx, mx, my, d) -> {
                ctx.pose().popMatrix();
                float p = progress(s);
                if (p < 1f && com.nightshade.module.performance.MenuOptimizer.allowTransitions()) {
                    int alpha = Math.round(0x90 * (1f - Fx.easeOutCubic(p)));
                    ctx.fill(0, 0, s.width, s.height, alpha << 24 | 0x05020C);
                }
            });
        });
    }
}
