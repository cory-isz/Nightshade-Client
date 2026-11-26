package com.nightshade.media;

import org.jetbrains.annotations.Nullable;

/**
 * What is playing right now. Times are in milliseconds; {@code positionAtMillis} is when {@code position} was read,
 * so the HUD can move the progress bar smoothly between updates. {@code art} is a local file path or an http(s) URL.
 */
public record MediaInfo(String title, String artist, String album, boolean playing, long position, long duration,
                        long positionAtMillis, @Nullable String art, String player) {

    /** Position now, counting the time since it was read while playing. */
    public long positionNow() {
        long p = playing ? position + (System.currentTimeMillis() - positionAtMillis) : position;
        return duration > 0 ? Math.min(p, duration) : p;
    }

    /** Identity of the track (for "song changed" and album art). */
    public String key() {
        return title + "\u0000" + artist + "\u0000" + album;
    }
}
