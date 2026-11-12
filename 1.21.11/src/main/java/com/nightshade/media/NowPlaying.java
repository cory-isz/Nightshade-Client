package com.nightshade.media;

import com.mojang.blaze3d.platform.NativeImage;
import com.nightshade.util.Log;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Locale;

/**
 * What your music player is playing, read through the operating system (no Spotify login): Windows media controls,
 * the Spotify app on macOS, or MPRIS players on Linux. Polls about once a second on a background thread while
 * something uses it, and keeps the album art as a texture.
 */
public final class NowPlaying {
    public static final Identifier ART = Identifier.fromNamespaceAndPath("nightshade", "now_playing_art");
    private static final int ART_SIZE = 64;

    private static volatile MediaInfo current;
    private static volatile String status = "";
    private static volatile boolean spotifyOnly;
    private static int users;
    private static Thread worker;
    private static MediaSource source;
    /** Set by tests to stand in for a real music player. */
    private static volatile MediaSource override;
    private static String artKey = "";
    private static volatile boolean artReady;
    private static DynamicTexture artTexture;

    private NowPlaying() {
    }

    @Nullable
    public static MediaInfo current() {
        return current;
    }

    /** Why nothing is shown (no player, missing tool...), for the settings panel. */
    public static String status() {
        return status;
    }

    public static boolean hasArt() {
        return artReady;
    }

    public static void setSpotifyOnly(boolean value) {
        spotifyOnly = value;
    }

    public static void setOverrideForTest(@Nullable MediaSource fake) {
        override = fake;
    }

    /** A module started using Now Playing (HUD, toast, Discord). */
    public static synchronized void acquire() {
        users++;
        if (worker == null) {
            worker = new Thread(NowPlaying::loop, "Nightshade Now Playing");
            worker.setDaemon(true);
            worker.start();
        }
    }

    public static synchronized void release() {
        users = Math.max(0, users - 1);
        if (users == 0 && worker != null) {
            worker.interrupt();
            worker = null;
        }
    }

    /** "playpause", "next" or "previous" - sent to the player in the background. */
    public static void control(String command) {
        MediaSource s = override != null ? override : source;
        if (s == null) {
            return;
        }
        Thread t = new Thread(() -> {
            try {
                s.control(command);
            } catch (Exception e) {
                status = "Could not control the player: " + e.getMessage();
            }
        }, "Nightshade media control");
        t.setDaemon(true);
        t.start();
    }

    private static MediaSource createSource() throws Exception {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("win")) {
            return new WindowsMediaSource();
        }
        if (os.contains("mac")) {
            return new MacMediaSource();
        }
        return new LinuxMediaSource();
    }

    private static void loop() {
        try {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    MediaSource s = override;
                    if (s == null) {
                        if (source == null) {
                            source = createSource();
                        }
                        s = source;
                    }
                    MediaInfo info = s.poll(spotifyOnly);
                    current = info;
                    status = info == null ? "Nothing playing" : "Playing from " + info.player();
                    if (info != null && !info.key().equals(artKey)) {
                        artKey = info.key();
                        loadArt(info.art());
                    }
                } catch (Exception e) {
                    current = null;
                    status = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                }
                Thread.sleep(1000);
            }
        } catch (InterruptedException ignored) {
            // stopped
        } finally {
            synchronized (NowPlaying.class) {
                if (source != null && worker == null) {
                    source.close();
                    source = null;
                }
            }
            current = null;
        }
    }

    /** Reads the cover (file or URL, JPEG or PNG), squares it to 64x64 and uploads it as a texture. */
    private static void loadArt(@Nullable String where) {
        artReady = false;
        if (where == null || where.isBlank()) {
            return;
        }
        try {
            byte[] bytes;
            if (where.startsWith("http://") || where.startsWith("https://")) {
                HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NORMAL).build();
                bytes = http.send(HttpRequest.newBuilder(URI.create(where)).timeout(Duration.ofSeconds(8)).GET().build(),
                        HttpResponse.BodyHandlers.ofByteArray()).body();
            } else {
                Path path = where.startsWith("file://") ? Path.of(URI.create(where)) : Path.of(where);
                bytes = Files.readAllBytes(path);
            }
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
            if (image == null) {
                return;
            }
            int side = Math.min(image.getWidth(), image.getHeight());
            BufferedImage square = new BufferedImage(ART_SIZE, ART_SIZE, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = square.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(image, 0, 0, ART_SIZE, ART_SIZE, (image.getWidth() - side) / 2, (image.getHeight() - side) / 2,
                    (image.getWidth() + side) / 2, (image.getHeight() + side) / 2, null);
            g.dispose();
            ByteArrayOutputStream png = new ByteArrayOutputStream();
            ImageIO.write(square, "png", png);
            byte[] pngBytes = png.toByteArray();
            Minecraft.getInstance().execute(() -> {
                try (InputStream in = new ByteArrayInputStream(pngBytes)) {
                    NativeImage nativeImage = NativeImage.read(in);
                    if (artTexture == null) {
                        artTexture = new DynamicTexture(() -> "nightshade now playing art", nativeImage);
                        Minecraft.getInstance().getTextureManager().register(ART, artTexture);
                    } else {
                        artTexture.setPixels(nativeImage);
                        artTexture.upload();
                    }
                    artReady = true;
                } catch (Exception e) {
                    Log.warn("Now Playing: could not show album art: {}", e.getMessage());
                }
            });
        } catch (Exception e) {
            Log.warn("Now Playing: could not load album art: {}", e.getMessage());
        }
    }
}
