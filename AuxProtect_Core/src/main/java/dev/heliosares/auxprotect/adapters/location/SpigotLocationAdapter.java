package dev.heliosares.auxprotect.adapters.location;

import org.bukkit.Location;
import org.bukkit.World;

public class SpigotLocationAdapter extends LocationAdapter {
    private final Location handle;

    public SpigotLocationAdapter(Location handle) {
        this.handle = handle;
    }

    @Override
    public String getWorld() {
        World world = handle.getWorld();
        if (world == null) return null;
        return world.getName();
    }

    @Override
    public double getX() {
        return handle.getX();
    }

    @Override
    public double getY() {
        return handle.getY();
    }

    @Override
    public double getZ() {
        return handle.getZ();
    }

    @Override
    public float getPitch() {
        return handle.getPitch();
    }

    @Override
    public float getYaw() {
        return handle.getYaw();
    }
}
