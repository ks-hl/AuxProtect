package dev.heliosares.auxprotect.towny;

import dev.heliosares.auxprotect.database.DbEntry;
import dev.heliosares.auxprotect.database.EntryAction;
import dev.heliosares.auxprotect.database.SQLManager;
import org.bukkit.Location;

public class TownyEntry extends DbEntry {

    public TownyEntry(String userLabel, EntryAction action, boolean state, Location location, String targetLabel,
                      String data) throws NullPointerException {
        super(userLabel, action, state, location, targetLabel, data);
    }

    public TownyEntry(String userLabel, EntryAction action, boolean state, String targetLabel, String data) {
        super(userLabel, action, state, targetLabel, data);
    }

    public TownyEntry(long time, int uid, EntryAction action, boolean state, String world, int x, int y, int z,
                      int pitch, int yaw, String target, int target_id, String data) {
        super(time, uid, action, state, world, x, y, z, pitch, yaw, target, target_id, data);
    }

    public String getUser() {
        if (user != null) {
            return user;
        }
        if (!getUserUUID().startsWith("$t") || getUserUUID().length() != 38) {
            return super.getUser();
        }
        user = SQLManager.getInstance().getTownyManager().getNameFromID(getUid());
        if (user == null) {
            user = getUserUUID();
        }
        return user;
    }

    public String getTarget() {
        if (target != null) {
            return target;
        }
        if (!getTargetUUID().startsWith("$t") || getTargetUUID().length() != 38) {
            return super.getTarget();
        }
        target = SQLManager.getInstance().getTownyManager().getNameFromID(getTargetId());
        if (target == null) {
            target = getTargetUUID();
        }
        return target;
    }
}
