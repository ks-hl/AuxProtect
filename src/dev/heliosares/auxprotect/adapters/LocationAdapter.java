package dev.heliosares.auxprotect.adapters;

public abstract class LocationAdapter {
    private String world;
    private double x;
    private double y;
    private double z;
    private float pitch;
    private float yaw;

    // TODO implement

    public LocationAdapter(String world, double x, double y, double z, float pitch, float yaw) {
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
        this.pitch = pitch;
        this.yaw = yaw;
    }

    public LocationAdapter(String world, double x, double y, double z) {
        this(world, x, y, z, 0, 0);
    }

    public String getWorld() {
        return world;
    }

    public LocationAdapter setWorld(String world) {
        this.world = world;
        return this;
    }

    public double getX() {
        return x;
    }

    public LocationAdapter setX(double x) {
        this.x = x;
        return this;
    }

    public double getY() {
        return y;
    }

    public LocationAdapter setY(double y) {
        this.y = y;
        return this;
    }

    public double getZ() {
        return z;
    }

    public LocationAdapter setZ(double z) {
        this.z = z;
        return this;
    }

    public float getPitch() {
        return pitch;
    }

    public LocationAdapter setPitch(float pitch) {
        this.pitch = pitch;
        return this;
    }

    public float getYaw() {
        return yaw;
    }

    public LocationAdapter setYaw(float yaw) {
        this.yaw = yaw;
        return this;
    }

    public LocationAdapter add(double x, double y, double z) {
        this.x += x;
        this.y += y;
        this.z += z;
        return this;
    }

    public double distance(LocationAdapter other) {
        return Math.sqrt(distanceSq(other));
    }

    public double distanceSq(LocationAdapter other) {
        if (other == null || !other.getWorld().equals(world)) {
            throw new IllegalArgumentException();
        }
        return Math.pow(x - other.x, 2) + Math.pow(y - other.y, 2) + Math.pow(z - other.z, 2);
    }

    public abstract int getBlockX();

    public abstract int getBlockY();

    public abstract int getBlockZ();
}
