package dev.heliosares.auxprotect.towny;

import dev.heliosares.auxprotect.database.EntryAction;
import dev.heliosares.auxprotect.database.SQLManager;
import dev.heliosares.auxprotect.database.SpigotDbEntry;
import dev.heliosares.auxprotect.exceptions.BusyException;
import dev.heliosares.auxprotect.spigot.AuxProtectSpigot;
import org.bukkit.Location;

import java.sql.SQLException;

public class TownyEntry extends SpigotDbEntry {

    public TownyEntry(String userLabel, EntryAction action, boolean state, Location location, String targetLabel,
                      String data) throws NullPointerException {
        super(userLabel, action, state, location, targetLabel, data);
    }

    public TownyEntry(String userLabel, EntryAction action, boolean state, String targetLabel, String data) {
        super(userLabel, action, state, null, targetLabel, data);
    }

    public TownyEntry(long time, int uid, EntryAction action, boolean state, String world, int x, int y, int z,
                      int pitch, int yaw, String target, int target_id, String data) {
        super(time, uid, action, state, world, x, y, z, pitch, yaw, target, target_id, data, SQLManager.getInstance());
    }

    @Override
    public String getUser() throws SQLException, BusyException {
        if (user != null) {
            return user;
        }
        if (!getUserUUID().startsWith("$t") || getUserUUID().length() != 38) {
            return super.getUser();
        }
        user = AuxProtectSpigot.getInstance().getTownyManager().getNameFromID(getUid(), false);
        if (user == null) {
            user = super.getUser();
        }
        return user;
    }

    @Override
    public String getTarget() throws SQLException, BusyException {
        if (target != null) {
            return target;
        }
        if (!getTargetUUID().startsWith("$t") || getTargetUUID().length() != 38) {
            return super.getTarget();
        }
        target = AuxProtectSpigot.getInstance().getTownyManager().getNameFromID(getTargetId(), false);
        if (target == null) {
            target = super.getTarget();
        }
        return target;
    }
}
