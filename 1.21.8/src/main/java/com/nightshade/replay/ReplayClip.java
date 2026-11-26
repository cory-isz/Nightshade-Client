package com.nightshade.replay;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/** A saved fight: who was there and where everyone was, 20 times a second. Stored as gzip'd JSON (.nsr). */
public final class ReplayClip {
    public static final int FORMAT = 1;
    private static final Gson GSON = new GsonBuilder().create();

    public int format = FORMAT;
    public String client = "";
    public String server = "";
    public String dimension = "";
    public long recordedAt;
    /** uuid of the recording player. */
    public String self = "";
    /** uuid -> name. */
    public Map<String, String> names = new LinkedHashMap<>();
    public List<Frame> frames = new ArrayList<>();

    /** One tick. */
    public static final class Frame {
        public List<Pose> players = new ArrayList<>();
    }

    /** One player in one tick. */
    public static final class Pose {
        public String id;
        public double x;
        public double y;
        public double z;
        public float yaw;
        public float pitch;
        public float head;
        public float body;
        public boolean sneak;
        public boolean swing;
        public int hurt;
        public String main;
        public String off;
        /** feet, legs, chest, head */
        public String[] armor;
    }

    public double seconds() {
        return frames.size() / 20.0;
    }

    public void write(Path file) throws IOException {
        Files.createDirectories(file.getParent());
        try (Writer out = new OutputStreamWriter(new GZIPOutputStream(Files.newOutputStream(file)), StandardCharsets.UTF_8)) {
            GSON.toJson(this, out);
        }
    }

    public static ReplayClip read(Path file) throws IOException {
        try (Reader in = new InputStreamReader(new GZIPInputStream(Files.newInputStream(file)), StandardCharsets.UTF_8)) {
            ReplayClip clip = GSON.fromJson(in, ReplayClip.class);
            if (clip == null || clip.frames == null) {
                throw new IOException("Not a replay");
            }
            return clip;
        }
    }
}
