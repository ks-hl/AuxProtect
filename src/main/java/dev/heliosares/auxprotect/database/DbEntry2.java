package dev.heliosares.auxprotect.database;

import dev.heliosares.auxprotect.exceptions.BusyException;
import org.bukkit.Location;

import javax.annotation.Nullable;
import java.sql.SQLException;

public class DbEntry2 extends DbEntry {
    protected String userLabel2;
    protected String user2;
    protected int uid2;

    public DbEntry2(String userLabel, String userLabel2, EntryAction action, boolean state, String targetLabel, String data) {
        super(userLabel, action, state, targetLabel, data);
        this.userLabel2 = userLabel2;
    }

    public DbEntry2(String userLabel, String userLabel2, EntryAction action, boolean state, @Nullable Location location, String targetLabel, String data) throws NullPointerException {
        super(userLabel, action, state, location, targetLabel, data);
        this.userLabel2 = userLabel2;
    }

    protected DbEntry2(long time, int uid, int uid2, EntryAction action, boolean state, String world, int x, int y, int z, int pitch, int yaw, String target, int target_id, String data, SQLManager sql) {
        super(time, uid, action, state, world, x, y, z, pitch, yaw, target, target_id, data, sql);
        this.uid2 = uid2;
    }

    public String getUser2(boolean resolve) throws SQLException {
        if (user2 != null || !resolve) return user2;

        if (!getUserUUID().startsWith("$") || getUserUUID().length() != 37) {
            return user2 = getUserUUID();
        }
        user2 = sql.getUserManager().getUsernameFromUID(getUid(), false);
        if (user2 == null) {
            user2 = getUserUUID();
        }
        return user2;
    }

    public int getUid2() throws SQLException {
        if (uid2 > 0) {
            return uid2;
        }
        return uid2 = sql.getUserManager().getUIDFromUUID(getUserUUID2(), true, true);
    }

    public String getUserUUID2() throws SQLException {
        if (userLabel2 != null) {
            return userLabel2;
        }
        if (uid2 > 0) {
            userLabel2 = sql.getUserManager().getUUIDFromUID(uid2, false);
        } else if (uid2 == 0) {
            return userLabel2 = "";
        }
        if (userLabel2 == null) {
            userLabel2 = "#null";
        }
        return userLabel2;
    }

    public String getUser2() throws SQLException {
        return getUser(true);
    }
}
