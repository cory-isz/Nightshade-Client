package com.nightshade.waypoint;

/** A named position in one dimension of one world/server. Mutable visibility only. */
public final class Waypoint {
    private final String name;
    private final int x;
    private final int y;
    private final int z;
    private final String dimension;
    private final int color;
    private boolean visible = true;

    public Waypoint(String name, int x, int y, int z, String dimension, int color) {
        this.name = name;
        this.x = x;
        this.y = y;
        this.z = z;
        this.dimension = dimension;
        this.color = color;
    }

    public String name() {
        return name;
    }

    public int x() {
        return x;
    }

    public int y() {
        return y;
    }

    public int z() {
        return z;
    }

    public String dimension() {
        return dimension;
    }

    public int color() {
        return color;
    }

    public boolean visible() {
        return visible;
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
    }
}
