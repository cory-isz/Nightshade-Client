package com.nightshade.module.utility;

import com.google.gson.JsonObject;
import com.nightshade.NightshadeClient;
import com.nightshade.event.events.TickEvent;
import com.nightshade.module.Module;
import com.nightshade.module.ModuleCategory;
import com.nightshade.module.setting.BooleanSetting;
import com.nightshade.module.setting.TextSetting;
import com.nightshade.util.EffectProbe;
import com.nightshade.util.Log;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Discord Rich Presence: shows "Playing Nightshade Client" on your Discord profile, with where you are (a server,
 * singleplayer or the menus) and for how long. Talks to the Discord app on this computer over its local IPC
 * connection (a named pipe on Windows, a socket elsewhere); nothing goes over the internet from here.
 */
public final class DiscordPresence extends Module {
    /** The Discord application that names and pictures the presence. Settable in case you use your own. */
    public static final String DEFAULT_APP_ID = "1570173573820317817"; // the "Nightshade Client" Discord application

    private final TextSetting appId = add(new TextSetting("Application ID", "Discord application ID (from discord.com/developers)", DEFAULT_APP_ID, 24));
    private final BooleanSetting showServer = add(new BooleanSetting("Show Server", "Show which server you play on (turn off for privacy)", true));
    private final BooleanSetting showSong = add(new BooleanSetting("Show Song", "Show the song you listen to (needs the Spotify HUD on)", false));
    private final BooleanSetting showVersion = add(new BooleanSetting("Show Version", "Show your Minecraft and Nightshade version", true));

    private volatile String details = "In the menus";
    private volatile String state = "";
    private volatile long since = System.currentTimeMillis() / 1000L;
    private volatile boolean running;
    private volatile String status = "Not connected";
    private Thread worker;
    private String lastPlace = "";

    public DiscordPresence() {
        super("Discord Rich Presence", "Shows that you play Nightshade Client on your Discord profile", ModuleCategory.UTILITY);
        enabledByDefault();
        listen(TickEvent.Post.class, event -> updateText());
    }

    @Override
    protected void onEnable() {
        updateText();
        running = true;
        worker = new Thread(this::loop, "Nightshade Discord");
        worker.setDaemon(true);
        worker.start();
    }

    @Override
    protected void onDisable() {
        running = false;
        if (worker != null) {
            worker.interrupt();
            worker = null;
        }
    }

    /** What the connection is doing, for the settings panel and tests. */
    public String status() {
        return status;
    }

    /** The two lines Discord will show, refreshed every tick on the main thread. */
    private void updateText() {
        String place;
        if (mc.level == null) {
            place = "In the menus";
        } else if (mc.getSingleplayerServer() != null) {
            place = "Playing singleplayer";
        } else {
            var server = mc.getCurrentServer();
            place = server == null ? "Playing multiplayer" : showServer.get() ? "Playing on " + server.ip : "Playing multiplayer";
        }
        if (!place.equals(lastPlace)) {
            lastPlace = place;
            since = System.currentTimeMillis() / 1000L;
        }
        details = place;
        com.nightshade.media.MediaInfo song = showSong.get() ? com.nightshade.media.NowPlaying.current() : null;
        if (song != null && song.playing()) {
            state = "\u266B " + song.title() + (song.artist().isBlank() ? "" : " - " + song.artist());
            return;
        }
        state = showVersion.get() ? "Minecraft " + net.minecraft.SharedConstants.getCurrentVersion().name() + " | Nightshade " + NightshadeClient.VERSION : "";
    }

    public String detailsForTest() {
        return details;
    }

    // ------------------------------------------------------------------ IPC

    /** One open connection to the Discord app. */
    private interface Pipe extends AutoCloseable {
        void write(ByteBuffer buffer) throws IOException;

        void readFully(ByteBuffer buffer) throws IOException;

        @Override
        void close() throws IOException;
    }

    private static Pipe open() {
        String os = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT);
        for (int i = 0; i < 10; i++) {
            try {
                if (os.contains("win")) {
                    RandomAccessFile file = new RandomAccessFile("\\\\.\\pipe\\discord-ipc-" + i, "rw");
                    return new Pipe() {
                        public void write(ByteBuffer b) throws IOException {
                            file.write(b.array(), 0, b.limit());
                        }

                        public void readFully(ByteBuffer b) throws IOException {
                            file.readFully(b.array(), 0, b.limit());
                        }

                        public void close() throws IOException {
                            file.close();
                        }
                    };
                }
                for (Path dir : socketDirs()) {
                    Path socket = dir.resolve("discord-ipc-" + i);
                    if (!Files.exists(socket)) {
                        continue;
                    }
                    SocketChannel channel = SocketChannel.open(StandardProtocolFamily.UNIX);
                    channel.connect(UnixDomainSocketAddress.of(socket));
                    return new Pipe() {
                        public void write(ByteBuffer b) throws IOException {
                            while (b.hasRemaining()) {
                                channel.write(b);
                            }
                        }

                        public void readFully(ByteBuffer b) throws IOException {
                            while (b.hasRemaining()) {
                                if (channel.read(b) < 0) {
                                    throw new IOException("Discord closed the connection");
                                }
                            }
                        }

                        public void close() throws IOException {
                            channel.close();
                        }
                    };
                }
            } catch (IOException ignored) {
                // try the next one
            }
        }
        return null;
    }

    /** Where the Discord app (normal, Flatpak or Snap) puts its socket on Linux and macOS. */
    private static List<Path> socketDirs() {
        List<Path> dirs = new ArrayList<>();
        for (String env : new String[]{"XDG_RUNTIME_DIR", "TMPDIR", "TMP", "TEMP"}) {
            String v = System.getenv(env);
            if (v != null && !v.isBlank()) {
                Path base = Path.of(v);
                dirs.add(base);
                dirs.add(base.resolve("app/com.discordapp.Discord"));
                dirs.add(base.resolve("snap.discord"));
            }
        }
        dirs.add(Path.of("/tmp"));
        return dirs;
    }

    private static void send(Pipe pipe, int op, JsonObject json) throws IOException {
        byte[] body = json.toString().getBytes(StandardCharsets.UTF_8);
        ByteBuffer buffer = ByteBuffer.allocate(8 + body.length).order(ByteOrder.LITTLE_ENDIAN);
        buffer.putInt(op).putInt(body.length).put(body).flip();
        pipe.write(buffer);
    }

    private static String receive(Pipe pipe) throws IOException {
        ByteBuffer header = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN);
        pipe.readFully(header);
        header.flip();
        header.getInt();
        int length = header.getInt();
        if (length < 0 || length > 1 << 20) {
            throw new IOException("Bad reply from Discord");
        }
        ByteBuffer body = ByteBuffer.allocate(length);
        pipe.readFully(body);
        return new String(body.array(), StandardCharsets.UTF_8);
    }

    private JsonObject activity() {
        JsonObject activity = new JsonObject();
        activity.addProperty("details", details);
        if (!state.isEmpty()) {
            activity.addProperty("state", state);
        }
        JsonObject timestamps = new JsonObject();
        timestamps.addProperty("start", since);
        activity.add("timestamps", timestamps);
        JsonObject assets = new JsonObject();
        assets.addProperty("large_image", "logo");
        assets.addProperty("large_text", "Nightshade Client " + NightshadeClient.VERSION);
        activity.add("assets", assets);
        return activity;
    }

    private void loop() {
        while (running) {
            String id = appId.get().trim();
            if (id.isEmpty()) {
                status = "Needs a Discord application ID";
                sleep(5000);
                continue;
            }
            try (Pipe pipe = open()) {
                if (pipe == null) {
                    status = "Discord is not running";
                    sleep(15000);
                    continue;
                }
                JsonObject hello = new JsonObject();
                hello.addProperty("v", 1);
                hello.addProperty("client_id", id);
                send(pipe, 0, hello);
                String ready = receive(pipe);
                if (!ready.contains("READY")) {
                    status = "Discord refused the connection (check the application ID)";
                    sleep(30000);
                    continue;
                }
                status = "Connected";
                String sent = "";
                while (running && id.equals(appId.get().trim())) {
                    String now = details + "|" + state + "|" + since;
                    if (!now.equals(sent)) {
                        JsonObject args = new JsonObject();
                        args.addProperty("pid", ProcessHandle.current().pid());
                        args.add("activity", activity());
                        JsonObject frame = new JsonObject();
                        frame.addProperty("cmd", "SET_ACTIVITY");
                        frame.add("args", args);
                        frame.addProperty("nonce", UUID.randomUUID().toString());
                        send(pipe, 1, frame);
                        receive(pipe);
                        sent = now;
                        EffectProbe.hit("Discord Rich Presence");
                    }
                    sleep(2000);
                }
            } catch (IOException e) {
                status = "Disconnected from Discord";
                Log.info("Discord presence: {}", e.getMessage());
                sleep(15000);
            }
        }
        status = "Not connected";
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
