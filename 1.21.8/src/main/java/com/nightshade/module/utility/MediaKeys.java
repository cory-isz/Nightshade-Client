package com.nightshade.module.utility;

import com.nightshade.media.NowPlaying;
import com.nightshade.module.Module;
import com.nightshade.module.ModuleCategory;
import com.nightshade.util.EffectProbe;

/** Keybind actions for your music player (Spotify and others): play/pause, next and previous song. */
public final class MediaKeys {
    private MediaKeys() {
    }

    private abstract static class MediaKey extends Module {
        private final String command;

        MediaKey(String name, String description, String command) {
            super(name, description, ModuleCategory.UTILITY);
            this.command = command;
        }

        @Override
        public boolean isToggleable() {
            return false;
        }

        @Override
        public void onAction() {
            NowPlaying.control(command);
            EffectProbe.hit(getName());
        }
    }

    public static final class PlayPause extends MediaKey {
        public PlayPause() {
            super("Media Play/Pause", "Press to play or pause your music (Spotify and other players)", "playpause");
        }
    }

    public static final class Next extends MediaKey {
        public Next() {
            super("Media Next", "Press to skip to the next song", "next");
        }
    }

    public static final class Previous extends MediaKey {
        public Previous() {
            super("Media Previous", "Press to go back to the previous song", "previous");
        }
    }
}
