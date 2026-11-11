package com.nightshade.module.hud;

import com.nightshade.event.events.HitLandedEvent;
import com.nightshade.event.events.TickEvent;
import com.nightshade.event.events.WorldChangeEvent;
import com.nightshade.module.HudModule;
import com.nightshade.module.ModuleCategory;
import com.nightshade.module.setting.BooleanSetting;
import com.nightshade.render.Fonts;
import com.nightshade.render.Theme;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Stats for the current session (since joining the world or server): play time, kills, deaths and K/D. A kill is an
 * entity that dies within three seconds of a hit you landed (confirmed by the server).
 */
public final class SessionStatsHud extends HudModule {
    private final BooleanSetting playersOnly = add(new BooleanSetting("Players Only", "Only count kills on players", false));
    private final BooleanSetting showTime = add(new BooleanSetting("Play Time", "Show the session time", true));
    private final Map<LivingEntity, Integer> recentHits = new IdentityHashMap<>();
    private long sessionStart = System.currentTimeMillis();
    private int kills;
    private int deaths;
    private boolean wasDead;
    private final List<String> lines = new ArrayList<>();

    public SessionStatsHud() {
        super("Session Stats", "Play time, kills, deaths and K/D for this session", ModuleCategory.HUD, 0.0, 0.52);
        listen(HitLandedEvent.class, event -> {
            LivingEntity target = event.target();
            if (!playersOnly.get() || target instanceof Player) {
                recentHits.put(target, 60);
            }
        });
        listen(TickEvent.Post.class, event -> tick());
        listen(WorldChangeEvent.class, event -> {
            if (event.world() == null) {
                return;
            }
            if (mc.getCurrentServer() == null && mc.getSingleplayerServer() == null) {
                return;
            }
            // New session on every join (dimension changes keep the session).
            if (!sameSession()) {
                reset();
            }
        });
    }

    private String sessionKey;

    private boolean sameSession() {
        String key = mc.getCurrentServer() != null ? "server:" + mc.getCurrentServer().ip
                : mc.getSingleplayerServer() != null ? "world:" + mc.getSingleplayerServer().getWorldData().getLevelName() : "";
        boolean same = key.equals(sessionKey);
        sessionKey = key;
        return same;
    }

    private void reset() {
        sessionStart = System.currentTimeMillis();
        kills = 0;
        deaths = 0;
        recentHits.clear();
    }

    private void tick() {
        if (mc.player == null) {
            return;
        }
        recentHits.entrySet().removeIf(e -> {
            LivingEntity entity = e.getKey();
            if (entity.isDeadOrDying()) {
                kills++;
                return true;
            }
            int left = e.getValue() - 1;
            if (left <= 0 || entity.isRemoved()) {
                return true;
            }
            e.setValue(left);
            return false;
        });
        boolean dead = mc.player.isDeadOrDying();
        if (dead && !wasDead) {
            deaths++;
        }
        wasDead = dead;
    }

    @Override
    protected void refresh() {
        lines.clear();
        if (showTime.get()) {
            long s = (System.currentTimeMillis() - sessionStart) / 1000L;
            lines.add(s >= 3600 ? String.format(Locale.ROOT, "%d:%02d:%02d", s / 3600, s / 60 % 60, s % 60) : String.format(Locale.ROOT, "%d:%02d", s / 60, s % 60));
        }
        lines.add(kills + " K  " + deaths + " D");
        lines.add("K/D " + String.format(Locale.ROOT, "%.2f", deaths == 0 ? (double) kills : kills / (double) deaths));
    }

    @Override
    public int getWidth() {
        int w = Fonts.width("Session");
        for (String line : lines) {
            w = Math.max(w, Fonts.width(line));
        }
        return w + 8;
    }

    @Override
    public int getHeight() {
        return (lines.size() + 1) * (Fonts.height() + 2) + 5;
    }

    @Override
    protected void renderElement(GuiGraphics context, float tickDelta, boolean preview) {
        drawBackground(context, getWidth(), getHeight());
        Fonts.draw(context, "Session", 4, 4, Theme.accentLight(), shadow());
        int y = 4 + Fonts.height() + 2;
        for (String line : lines) {
            Fonts.draw(context, line, 4, y, textColor.argb(), shadow());
            y += Fonts.height() + 2;
        }
    }

    /** Test hooks. */
    public int killsForTest() {
        return kills;
    }

    public int deathsForTest() {
        return deaths;
    }
}
