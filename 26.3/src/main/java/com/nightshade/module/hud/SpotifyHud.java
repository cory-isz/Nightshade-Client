package com.nightshade.module.hud;

import com.nightshade.NightshadeClient;
import com.nightshade.event.events.TickEvent;
import com.nightshade.media.MediaInfo;
import com.nightshade.media.NowPlaying;
import com.nightshade.module.HudModule;
import com.nightshade.module.ModuleCategory;
import com.nightshade.module.setting.BooleanSetting;
import com.nightshade.module.setting.NumberSetting;
import com.nightshade.render.ColorUtil;
import com.nightshade.render.Fonts;
import com.nightshade.render.RenderUtil;
import com.nightshade.render.Theme;
import com.nightshade.util.EffectProbe;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;

/**
 * Spotify (and other music players): the song, artist, album art and a progress bar, read from your computer's media
 * controls - no Spotify login. Media keys (Play/Pause, Next, Previous) can be bound in the modules menu.
 */
public final class SpotifyHud extends HudModule {
    private final NumberSetting width = add(new NumberSetting("Width", "Width of the panel", 170, 120, 260, 5, "px"));
    private final BooleanSetting art = add(new BooleanSetting("Show Album Art", "Show the album cover", true));
    private final BooleanSetting progress = add(new BooleanSetting("Show Progress", "Progress bar and times", true));
    private final BooleanSetting spotifyOnly = add(new BooleanSetting("Spotify Only", "Only show Spotify (off: any music or video player)", false));
    private final BooleanSetting hideIdle = add(new BooleanSetting("Hide When Idle", "Hide the panel when nothing is playing", true));
    private final BooleanSetting toast = add(new BooleanSetting("Song Toast", "Show a notification when the song changes", true));
    private String lastKey = "";

    public SpotifyHud() {
        super("Spotify", "Shows the song you are listening to, with album art and progress", ModuleCategory.HUD, 0.0, 0.86);
        listen(TickEvent.Post.class, event -> tick());
    }

    @Override
    protected void onEnable() {
        NowPlaying.acquire();
    }

    @Override
    protected void onDisable() {
        NowPlaying.release();
    }

    private void tick() {
        NowPlaying.setSpotifyOnly(spotifyOnly.get());
        MediaInfo info = NowPlaying.current();
        if (info == null) {
            return;
        }
        if (!info.key().equals(lastKey)) {
            boolean first = lastKey.isEmpty();
            lastKey = info.key();
            if (!first && toast.get() && info.playing()) {
                NightshadeClient.client().notifications().info("Now playing", info.title() + (info.artist().isBlank() ? "" : " - " + info.artist()));
            }
        }
    }

    @Override
    public boolean shouldRender() {
        return mc.player != null && (!hideIdle.get() || NowPlaying.current() != null);
    }

    @Override
    public int getWidth() {
        return width.intValue();
    }

    @Override
    public int getHeight() {
        return 40;
    }

    private static String time(long ms) {
        long s = Math.max(0, ms / 1000);
        return s / 60 + ":" + String.format("%02d", s % 60);
    }

    @Override
    protected void renderElement(GuiGraphicsExtractor ctx, float tickDelta, boolean preview) {
        int w = getWidth();
        int h = getHeight();
        drawBackground(ctx, w, h);
        MediaInfo info = NowPlaying.current();
        if (info == null && preview) {
            info = new MediaInfo("Song title", "Artist", "Album", true, 72_000, 200_000, System.currentTimeMillis(), null, "Spotify");
        }
        int x = 5;
        int artSide = 30;
        if (art.get()) {
            if (info != null && NowPlaying.hasArt() && !preview) {
                ctx.blit(RenderPipelines.GUI_TEXTURED, NowPlaying.ART, 5, 5, 0f, 0f, artSide, artSide, 64, 64, 64, 64);
            } else {
                RenderUtil.roundedRect(ctx, 5, 5, 5 + artSide, 5 + artSide, 3, ColorUtil.withAlpha(0xFF1DB954, 0x50));
                Fonts.drawCentered(ctx, "♫", 5 + artSide / 2, 5 + artSide / 2 - 4, 0xFF1DB954, false);
            }
            if (info != null && !info.playing()) {
                RenderUtil.rect(ctx, 5, 5, 5 + artSide, 5 + artSide, 0x80000000);
                RenderUtil.rect(ctx, 5 + artSide / 2 - 4, 5 + artSide / 2 - 5, 5 + artSide / 2 - 1, 5 + artSide / 2 + 5, 0xFFFFFFFF);
                RenderUtil.rect(ctx, 5 + artSide / 2 + 1, 5 + artSide / 2 - 5, 5 + artSide / 2 + 4, 5 + artSide / 2 + 5, 0xFFFFFFFF);
            }
            x = 5 + artSide + 6;
        }
        int textW = w - x - 5;
        if (info == null) {
            Fonts.draw(ctx, "Nothing playing", x, 9, Theme.TEXT_MUTED, shadow());
            Fonts.draw(ctx, Fonts.trim(NowPlaying.status(), textW), x, 21, Theme.TEXT_DIM, shadow());
            return;
        }
        Fonts.draw(ctx, Fonts.trim(info.title(), textW), x, 5, textColor.argb(), shadow());
        Fonts.draw(ctx, Fonts.trim(info.artist().isBlank() ? info.player() : info.artist(), textW), x, 16, Theme.TEXT_MUTED, shadow());
        if (progress.get() && info.duration() > 0) {
            long pos = info.positionNow();
            String times = time(pos) + " / " + time(info.duration());
            int tw = Fonts.width(times);
            int barX2 = w - 5 - tw - 5;
            float frac = Math.min(1f, pos / (float) info.duration());
            RenderUtil.roundedRect(ctx, x, 30, barX2, 33, 1, 0x50FFFFFF);
            RenderUtil.roundedRect(ctx, x, 30, x + Math.max(2, Math.round((barX2 - x) * frac)), 33, 1, 0xFF1DB954);
            Fonts.draw(ctx, times, w - 5 - tw, 27, Theme.TEXT_DIM, shadow());
        }
        if (!preview) {
            EffectProbe.hit("Spotify");
        }
    }
}
