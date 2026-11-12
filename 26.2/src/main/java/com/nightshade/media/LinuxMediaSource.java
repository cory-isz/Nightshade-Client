package com.nightshade.media;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/** Linux: any MPRIS player (Spotify, browsers, VLC...) through the common {@code playerctl} tool. */
final class LinuxMediaSource implements MediaSource {
    private static final String FORMAT = "{{status}}\u001f{{title}}\u001f{{artist}}\u001f{{album}}\u001f{{position}}\u001f{{mpris:length}}\u001f{{mpris:artUrl}}\u001f{{playerName}}";

    static String run(String... command) throws IOException, InterruptedException {
        Process p = new ProcessBuilder(command).redirectErrorStream(false).start();
        if (!p.waitFor(3, TimeUnit.SECONDS)) {
            p.destroyForcibly();
            return "";
        }
        return new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
    }

    @Override
    public MediaInfo poll(boolean spotifyOnly) throws Exception {
        String out;
        try {
            out = run("playerctl", "-p", spotifyOnly ? "spotify" : "spotify,%any", "metadata", "--format", FORMAT);
        } catch (IOException noPlayerctl) {
            throw new IOException("Install playerctl to show what is playing");
        }
        String[] f = out.split("\u001f", -1);
        if (f.length < 8 || f[1].isBlank()) {
            return null;
        }
        long position = parse(f[4]) / 1000L; // microseconds -> ms
        long duration = parse(f[5]) / 1000L;
        String art = f[6].isBlank() ? null : f[6];
        return new MediaInfo(f[1], f[2], f[3], f[0].equalsIgnoreCase("Playing"), position, duration, System.currentTimeMillis(), art, f[7]);
    }

    private static long parse(String s) {
        try {
            return Long.parseLong(s.trim());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    @Override
    public void control(String command) throws Exception {
        String verb = switch (command) {
            case "next" -> "next";
            case "previous" -> "previous";
            default -> "play-pause";
        };
        run("playerctl", "-p", "spotify,%any", verb);
    }
}
