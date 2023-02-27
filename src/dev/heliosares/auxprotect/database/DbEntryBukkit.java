package dev.heliosares.auxprotect.database;

import org.bukkit.Bukkit;
import org.bukkit.Location;

import javax.annotation.Nullable;

public class DbEntryBukkit extends DbEntry {

    public DbEntryBukkit(String userLabel, EntryAction action, boolean state, String targetLabel, String data) {
        super(userLabel, action, state, targetLabel, data);
    }

    public DbEntryBukkit(String userLabel, EntryAction action, boolean state, @Nullable Location location, String targetLabel, String data) throws NullPointerException {
        super(userLabel, action, state, location, targetLabel, data);
    }

    protected DbEntryBukkit(long time, int uid, EntryAction action, boolean state, String world, int x, int y, int z, int pitch, int yaw, String target, int target_id, String data) {
        super(time, uid, action, state, world, x, y, z, pitch, yaw, target, target_id, data);
    }

    //TODO implement as adapter

    public static Location getLocation(DbEntry entry) {
        double x, y, z;
        if (entry instanceof PosEntry posEntry) {
            x = posEntry.getDoubleX();
            y = posEntry.getDoubleY();
            z = posEntry.getDoubleZ();
        } else {
            x = entry.getX();
            y = entry.getY();
            z = entry.getZ();
        }
        return new Location(Bukkit.getWorld(entry.getWorld()), x, y, z, entry.getYaw(), entry.getPitch());
    }
}
