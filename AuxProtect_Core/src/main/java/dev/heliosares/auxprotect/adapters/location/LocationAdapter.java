package dev.heliosares.auxprotect.adapters.location;

public abstract class LocationAdapter {

    public abstract String getWorld();

    public abstract double getX();

    public abstract double getY();

    public abstract double getZ();

    public abstract int getBlockX();

    public abstract int getBlockY();

    public abstract int getBlockZ();

    public abstract float getPitch();

    public abstract float getYaw();

    public double distance(LocationAdapter other) {
        return Math.sqrt(distanceSq(other));
    }

    public double distanceSq(LocationAdapter other) {
        if (other == null || !other.getWorld().equals(getWorld())) {
            throw new IllegalArgumentException("Locations in different worlds");
        }
        return Math.pow(getX() - other.getX(), 2) + Math.pow(getY() - other.getY(), 2) + Math.pow(getZ() - other.getZ(), 2);
    }
}
