package com.nightshade.event.events;

/** Fired at the start ({@link Pre}) and end ({@link Post}) of every client tick. */
public final class TickEvent {
    private TickEvent() {
    }

    public static final class Pre {
        public static final Pre INSTANCE = new Pre();

        private Pre() {
        }
    }

    public static final class Post {
        public static final Post INSTANCE = new Post();

        private Post() {
        }
    }
}
