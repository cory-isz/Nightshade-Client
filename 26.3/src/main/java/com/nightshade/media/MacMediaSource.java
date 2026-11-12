package com.nightshade.media;

/** macOS: asks the Spotify app directly with AppleScript (no permission prompts for reading the track). */
final class MacMediaSource implements MediaSource {
    private static final String SCRIPT = """
            if application "Spotify" is running then
              tell application "Spotify"
                set t to current track
                return (player state as string) & tab & (name of t) & tab & (artist of t) & tab & (album of t) & tab & ((player position * 1000) as integer) & tab & (duration of t) & tab & (artwork url of t)
              end tell
            end if
            return ""
            """;

    @Override
    public MediaInfo poll(boolean spotifyOnly) throws Exception {
        String out = LinuxMediaSource.run("osascript", "-e", SCRIPT);
        String[] f = out.split("\t", -1);
        if (f.length < 7 || f[1].isBlank()) {
            return null;
        }
        return new MediaInfo(f[1], f[2], f[3], f[0].equalsIgnoreCase("playing"), num(f[4]), num(f[5]), System.currentTimeMillis(),
                f[6].isBlank() ? null : f[6], "Spotify");
    }

    private static long num(String s) {
        try {
            return (long) Double.parseDouble(s.trim().replace(',', '.'));
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    @Override
    public void control(String command) throws Exception {
        String verb = switch (command) {
            case "next" -> "next track";
            case "previous" -> "previous track";
            default -> "playpause";
        };
        LinuxMediaSource.run("osascript", "-e", "tell application \"Spotify\" to " + verb);
    }
}
