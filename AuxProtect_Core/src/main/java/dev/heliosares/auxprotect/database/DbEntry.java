package dev.heliosares.auxprotect.database;

import dev.heliosares.auxprotect.adapters.message.ClickEvent;
import dev.heliosares.auxprotect.adapters.message.GenericBuilder;
import dev.heliosares.auxprotect.adapters.message.GenericTextColor;
import dev.heliosares.auxprotect.adapters.sender.SenderAdapter;
import dev.heliosares.auxprotect.api.AuxProtectAPI;
import dev.heliosares.auxprotect.core.APPermission;
import dev.heliosares.auxprotect.core.Language;
import dev.heliosares.auxprotect.exceptions.BusyException;
import dev.heliosares.auxprotect.utils.TimeUtil;
import org.bukkit.Location;

import javax.annotation.Nullable;
import java.sql.SQLException;
import java.util.TimeZone;

public class DbEntry {

    protected SQLManager getSql() {
        return sql;
    }

    void setSql(SQLManager sql) {
        this.sql = sql;
    }

    public void deResolveUIDs() {
        uid = -1;
        target_id = -1;
    }

    protected SQLManager sql;
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
                    int yaw, String targetLabel, String data, SQLManager sql) {
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
        this.sql = sql;
    }

    /**
     *
     */
    public DbEntry(String userLabel, EntryAction action, boolean state, String targetLabel, String data) {
        this(userLabel, action, state, null, 0, 0, 0, 0, 0, targetLabel, data, SQLManager.getInstance());
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
                location == null ? 0 : Math.round(location.getYaw()), targetLabel, data, SQLManager.getInstance());
    }

    protected DbEntry(long time, int uid, EntryAction action, boolean state, String world, int x, int y, int z,
                      int pitch, int yaw, String target, int target_id, String data, SQLManager sql) {
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
        this.sql = sql;
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

    public int getUid() throws SQLException, BusyException {
        if (uid > 0) {
            return uid;
        }
        return uid = sql.getUserManager().getUIDFromUUID(getUserUUID(), true, true);
    }

    public String getUser() throws SQLException, BusyException {
        return getUser(true);
    }

    public String getUser(boolean resolve) throws SQLException, BusyException {
        if (user != null || !resolve) return user;

        if (!getUserUUID().startsWith("$") || getUserUUID().length() != 37) {
            return user = getUserUUID();
        }
        user = sql.getUserManager().getUsernameFromUID(getUid(), false);
        if (user == null) {
            user = getUserUUID();
        }
        return user;
    }

    public int getTargetId() throws SQLException, BusyException {
        if (action.getTable().hasStringTarget()) {
            return -1;
        }
        if (target_id > 0) {
            return target_id;
        }
        return target_id = sql.getUserManager().getUIDFromUUID(getTargetUUID(), true, true);
    }

    public String getTarget() throws SQLException, BusyException {
        return getTarget(true);
    }

    public String getTarget(boolean resolve) throws SQLException, BusyException {
        if (target != null || !resolve) return target;

        if (action.getTable().hasStringTarget() || !getTargetUUID().startsWith("$") || getTargetUUID().length() != 37) {
            return target = getTargetUUID();
        }
        target = sql.getUserManager().getUsernameFromUID(getTargetId(), false);
        if (target == null) {
            target = getTargetUUID();
        }
        return target;
    }

    public String getTargetUUID() throws SQLException, BusyException {
        if (targetLabel != null) {
            return targetLabel;
        }
        if (target_id > 0) {
            targetLabel = sql.getUserManager().getUUIDFromUID(target_id, false);
        } else if (target_id == 0) {
            return targetLabel = "";
        }
        if (targetLabel == null) {
            targetLabel = "#null";
        }
        return targetLabel;
    }

    public String getUserUUID() throws SQLException, BusyException {
        if (userLabel != null) {
            return userLabel;
        }
        if (uid > 0) {
            userLabel = sql.getUserManager().getUUIDFromUID(uid, false);
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

    public byte[] getBlob() throws SQLException, BusyException {
        if (blob == null) blob = sql.getBlob(this);
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
        } catch (SQLException | BusyException e) {
            out = "ERROR ";
        }
        if (getData() != null && !getData().isEmpty()) {
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

    public void appendTime(GenericBuilder message, TimeZone timeZone) {
        String msg;
        if (System.currentTimeMillis() - getTime() < 55) {
            msg = Language.L.RESULTS__TIME_NOW.translate();
        } else {
            msg = Language.L.RESULTS__TIME.translate(TimeUtil.millisToString(System.currentTimeMillis() - getTime()));
        }
        message.append(msg).hover(TimeUtil.format(getTime(), TimeUtil.entryTimeFormat, timeZone.toZoneId())
                        + "\n" + Language.L.RESULTS__CLICK_TO_COPY_TIME.translate(getTime()))
                .click(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, getTime() + "e"));
    }

    public void appendCoordinates(SenderAdapter<?,?> senderAdapter, GenericBuilder message) {
        String tpCommand = "/" + AuxProtectAPI.getInstance().getCommandPrefix() + " tp ";

        tpCommand += String.format("%d.5 %d %d.5 ", getX(), getY(), getZ());

        tpCommand += getWorld();
        if (getAction().getTable().hasLook()) {
            // TODO is this necessary since PosEntry overrides?
            tpCommand += String.format(" %d %d", getPitch(), getYaw());
        }
        message.append("\n" + " ".repeat(17));
        message.append(String.format(GenericTextColor.COLOR_CHAR + "7(x%d/y%d/z%d/%s)", getX(), getY(), getZ(), getWorld()));

        if (senderAdapter == null || APPermission.TP.hasPermission(senderAdapter)) {
            message.click(new ClickEvent(ClickEvent.Action.RUN_COMMAND, tpCommand))
                    .hover(GenericTextColor.COLOR_CHAR + "7" + tpCommand);
        }

        if (getAction().getTable().hasLook()) {
            // TODO is this necessary since PosEntry overrides?
            message.append(String.format(GenericTextColor.COLOR_CHAR + "7 (p%s/y%d)", getPitch(), getYaw()));
        }
    }
}
