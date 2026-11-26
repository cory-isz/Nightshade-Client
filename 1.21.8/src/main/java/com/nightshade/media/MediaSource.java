package com.nightshade.media;

import org.jetbrains.annotations.Nullable;

/** Reads and controls the music player through the operating system. Called from a background thread. */
public interface MediaSource extends AutoCloseable {
    /** The current track, or null when nothing is playing or no player is open. */
    @Nullable
    MediaInfo poll(boolean spotifyOnly) throws Exception;

    /** "playpause", "next" or "previous". */
    void control(String command) throws Exception;

    @Override
    default void close() {
    }
}
