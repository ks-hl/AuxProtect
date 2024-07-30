package dev.heliosares.auxprotect.database;

import org.bukkit.Location;

import jakarta.annotation.Nullable;

public class SpigotDbEntry extends DbEntry {
    /**
     * @param userLabel   The label of the user, provided by
     *                    AuxProtect#getLabel(Object) and
     *                    AuxProtectBungee#getLabel(Object) as applicable. This may
     *                    also be a generic string such as "#env"
     * @param state       Specifies the state of EntryAction (i.e +mount vs -mount),
     *                    if applicable, otherwise false.
     * @param location    May be null
     * @param targetLabel The label of the target, see userLabel for details.
     * @param data        Extra data about your entry. This is stored as plain text
     *                    so use sparingly.
     */
    public SpigotDbEntry(String userLabel, EntryAction action, boolean state, @Nullable Location location, String targetLabel,
                         String data) throws NullPointerException {
        super(userLabel, action, state, location == null || location.getWorld() == null ? null : location.getWorld().getName(),
                location == null ? 0 : location.getBlockX(), location == null ? 0 : location.getBlockY(),
                location == null ? 0 : location.getBlockZ(),
                location == null ? 0 : Math.round(location.getPitch()),
                location == null ? 0 : Math.round(location.getYaw()), targetLabel, data, SQLManager.getInstance());
    }

    protected SpigotDbEntry(long time, int uid, EntryAction action, boolean state, String world, int x, int y, int z,
                            int pitch, int yaw, String target, int target_id, String data, SQLManager sql) {
        super(time, uid, action, state, world, x, y, z, pitch, yaw, target, target_id, data, sql);
    }
}
