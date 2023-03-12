package dev.heliosares.auxprotect.database;

import org.bukkit.Location;

import javax.annotation.Nullable;
import java.sql.SQLException;

public class DbEntry {

    protected final String world;
    protected final int x;
    protected final int y;
    protected final int z;
    protected final int pitch;
    protected final int yaw;
    protected final EntryAction action;
    protected final boolean state;
    private final long time;
    protected String data;
    protected String userLabel;
    protected String user;
    protected int uid;

    protected String targetLabel;
    protected String target;
    protected int target_id;

    private long blobid = -1;
    private byte[] blob;

    private DbEntry(String userLabel, EntryAction action, boolean state, String world, int x, int y, int z, int pitch,
                    int yaw, String targetLabel, String data) {
        this.time = DatabaseRunnable.getTime(action.getTable());
        this.userLabel = userLabel;
        this.action = action;
        this.state = state;
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
        this.pitch = pitch;
        this.yaw = yaw;
        this.targetLabel = targetLabel;
        this.data = data;
    }

    /**
     *
     */
    public DbEntry(String userLabel, EntryAction action, boolean state, String targetLabel, String data) {
        this(userLabel, action, state, null, 0, 0, 0, 0, 0, targetLabel, data);
    }

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
    public DbEntry(String userLabel, EntryAction action, boolean state, @Nullable Location location, String targetLabel,
                   String data) throws NullPointerException {
        this(userLabel, action, state, location == null ? null : location.getWorld().getName(),
                location == null ? 0 : location.getBlockX(), location == null ? 0 : location.getBlockY(),
                location == null ? 0 : location.getBlockZ(),
                location == null ? 0 : Math.round(location.getPitch()),
                location == null ? 0 : Math.round(location.getYaw()), targetLabel, data);
    }

    protected DbEntry(long time, int uid, EntryAction action, boolean state, String world, int x, int y, int z,
                      int pitch, int yaw, String target, int target_id, String data) {
        this.time = time;
        this.uid = uid;
        this.action = action;
        this.state = state;
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
        this.pitch = pitch;
        this.yaw = yaw;
        this.targetLabel = target;
        this.target_id = target_id;
        this.data = data;
    }

    public long getTime() {
        return time;
    }

    public EntryAction getAction() {
        return action;
    }

    public boolean getState() {
        return state;
    }

    public String getData() {
        return data;
    }

    public void setData(String data) {
        this.data = data;
    }

    public int getUid() throws SQLException {
        if (uid > 0) {
            return uid;
        }
        return uid = SQLManager.getInstance().getUserManager().getUIDFromUUID(getUserUUID(), true, true);
    }

    public int getTargetId() throws SQLException {
        if (action.getTable().hasStringTarget()) {
            return -1;
        }
        if (target_id > 0) {
            return target_id;
        }
        return target_id = SQLManager.getInstance().getUserManager().getUIDFromUUID(getTargetUUID(), true, true);
    }

    public String getUser() throws SQLException {
        return getUser(true);
    }

    public String getUser(boolean resolve) throws SQLException {
        if (user != null || !resolve) return user;

        if (!getUserUUID().startsWith("$") || getUserUUID().length() != 37) {
            return user = getUserUUID();
        }
        user = SQLManager.getInstance().getUserManager().getUsernameFromUID(getUid(), false);
        if (user == null) {
            user = getUserUUID();
        }
        return user;
    }

    public String getTarget() throws SQLException {
        return getTarget(true);
    }

    public String getTarget(boolean resolve) throws SQLException {
        if (target != null || !resolve) return target;

        if (action.getTable().hasStringTarget() || !getTargetUUID().startsWith("$") || getTargetUUID().length() != 37) {
            return target = getTargetUUID();
        }
        target = SQLManager.getInstance().getUserManager().getUsernameFromUID(getTargetId(), false);
        if (target == null) {
            target = getTargetUUID();
        }
        return target;
    }

    public String getTargetUUID() throws SQLException {
        if (targetLabel != null) {
            return targetLabel;
        }
        if (target_id > 0) {
            targetLabel = SQLManager.getInstance().getUserManager().getUUIDFromUID(target_id, false);
        } else if (target_id == 0) {
            return targetLabel = "";
        }
        if (targetLabel == null) {
            targetLabel = "#null";
        }
        return targetLabel;
    }

    public String getUserUUID() throws SQLException {
        if (userLabel != null) {
            return userLabel;
        }
        if (uid > 0) {
            userLabel = SQLManager.getInstance().getUserManager().getUUIDFromUID(uid, false);
        } else if (uid == 0) {
            return userLabel = "";
        }
        if (userLabel == null) {
            userLabel = "#null";
        }
        return userLabel;
    }

    public double getBoxDistance(DbEntry entry) {
        if (!entry.getWorld().equals(getWorld())) {
            return -1;
        }
        return Math.max(Math.max(Math.abs(entry.getX() - getX()), Math.abs(entry.getY() - getY())), Math.abs(entry.getZ() - getZ()));
    }

    public double getDistance(DbEntry entry) {
        return Math.sqrt(getDistanceSq(entry));
    }

    public double getDistanceSq(DbEntry entry) {
        return Math.pow(getX() - entry.getX(), 2) + Math.pow(getY() - entry.getY(), 2) + Math.pow(getZ() - entry.getZ(), 2);
    }

    public byte[] getBlob() throws SQLException {
        if (blob == null) blob = SQLManager.getInstance().getBlob(this);
        return blob;
    }

    public void setBlob(byte[] blob) {
        this.blob = blob;
    }

    public boolean hasBlob() {
        return blob != null || blobid >= 0;
    }

    public long getBlobID() {
        return blobid;
    }

    protected void setBlobID(long blobid) {
        this.blobid = blobid;
    }

    @Override
    public String toString() {
        String out;
        try {
            out = String.format("%s %s(%d) %s ", getUser(), getAction().getText(getState()),
                    getAction().getId(getState()), getTarget());
        } catch (SQLException e) {
            out = "ERROR ";
        }
        if (getData() != null && getData().length() > 0) {
            String data = getData();
            if (data.length() > 64) {
                data = data.substring(0, 64) + "...";
            }
            out += "(" + data + ")";

        }
        return out;
    }

    public String getWorld() {
        return world;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public int getZ() {
        return z;
    }

    public int getPitch() {
        return pitch;
    }

    public int getYaw() {
        return yaw;
    }
}
