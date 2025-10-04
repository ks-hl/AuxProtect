package dev.heliosares.auxprotect.database;

import dev.heliosares.auxprotect.core.IAuxProtect;
import dev.heliosares.auxprotect.utils.BidiMapCache;
import dev.kshl.kshlib.exceptions.BusyException;
import dev.kshl.kshlib.sql.ConnectionManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.UUID;

public class SQLUserManager {
    private final IAuxProtect plugin;
    private final SQLManager sql;
    private final BidiMapCache<Integer, String> uuids = new BidiMapCache<>(300000L, 300000L, true);
    private final BidiMapCache<Integer, String> usernames = new BidiMapCache<>(300000L, 300000L, true);

    public SQLUserManager(IAuxProtect plugin, SQLManager sql) {
        this.plugin = plugin;
        this.sql = sql;
    }

    public void updateUsernameAndIP(UUID uuid, String name, String ip) throws SQLException, BusyException {
        final int uid = this.getUIDFromUUID("$" + uuid, true);
        if (uid <= 0) {
            return;
        }
        usernames.put(uid, name);
        sql.executeTransaction(connection -> {
            String newestusername = null;
            long newestusernametime = 0;
            boolean newip = true;
            String stmt = "SELECT * FROM " + Table.AUXPROTECT_LONGTERM + " WHERE uid=?;";
            plugin.debug(stmt, 3);
            try (PreparedStatement pstmt = connection.prepareStatement(stmt)) {
                pstmt.setInt(1, uid);
                try (ResultSet results = pstmt.executeQuery()) {
                    while (results.next()) {
                        String target = results.getString("target");
                        if (target == null) {
                            continue;
                        }
                        long time = results.getLong("time");
                        int action_id = results.getInt("action_id");
                        if (action_id == EntryAction.IP.id) {
                            if (target.equals(ip)) {
                                newip = false;
                            }
                        } else if (action_id == EntryAction.USERNAME.id) {
                            if (time > newestusernametime) {
                                newestusername = target;
                                newestusernametime = time;
                            }
                        }
                    }
                }
            }
            if (newip) {
                plugin.add(new DbEntry("$" + uuid, EntryAction.IP, false, ip, ""));
            }
            if (!name.equalsIgnoreCase(newestusername)) {
                plugin.debug("New username: " + name + " for " + newestusername);
                plugin.add(new DbEntry("$" + uuid, EntryAction.USERNAME, false, name, ""));
            }
        }, 300000L);
    }

    public String getUsernameFromUID(int uid) throws SQLException, BusyException {
        if (uid < 0) {
            return null;
        }
        if (uid == 0) {
            return "";
        }
        if (usernames.containsKey(uid)) {
            return usernames.get(uid);
        }

        return sql.query("SELECT * FROM " + Table.AUXPROTECT_LONGTERM + " WHERE action_id=? AND uid=? ORDER BY time DESC LIMIT 1", rs -> {
            if (!rs.next()) return null;

            String username = rs.getString("target");
            plugin.debug("Resolved UID " + uid + " to " + username, 5);
            if (username != null) {
                usernames.put(uid, username);
            }
            return username;
        }, 5000L, EntryAction.USERNAME.id, uid);
    }

    public HashMap<Long, String> getUsernamesFromUID(int uid, boolean wait) throws SQLException, BusyException {
        HashMap<Long, String> out = new HashMap<>();
        String stmt = "SELECT * FROM " + Table.AUXPROTECT_LONGTERM + " WHERE action_id=? AND uid=?;";
        plugin.debug(stmt, 3);
        sql.execute(connection -> {
            try (PreparedStatement pstmt = connection.prepareStatement(stmt)) {
                pstmt.setInt(1, EntryAction.USERNAME.id);
                pstmt.setInt(2, uid);
                try (ResultSet results = pstmt.executeQuery()) {
                    while (results.next()) {
                        long time = results.getLong("time");
                        String username = results.getString("target");
                        if (username != null) {
                            out.put(time, username);
                        }
                    }
                }
            }
        }, wait ? 300000L : 3000L);
        return out;
    }

    public int getUIDFromUsername(String username) throws SQLException, BusyException {
        if (username == null) {
            return -1;
        }
        if (usernames.containsValue(username)) {
            return usernames.getKey(username);
        }

        return sql.query("SELECT * FROM " + Table.AUXPROTECT_LONGTERM + " WHERE action_id=? AND target_hash=? ORDER BY time DESC LIMIT 1", rs -> {
            while (rs.next()) {
                String username_ = rs.getString("target");
                if (username_ == null || !username_.equalsIgnoreCase(username)) continue;
                int uid = rs.getInt("uid");
                if (uid > 0) {
                    plugin.debug("Resolved username " + username_ + " to UID " + uid, 5);
                    usernames.put(uid, username_);
                    return uid;
                }
            }
            plugin.debug("Unknown UID for " + username, 3);
            return -1;
        }, 5000L, EntryAction.USERNAME.id, username.toLowerCase().hashCode());
    }

    public int getUIDFromUUID(String uuid) throws SQLException, BusyException {
        return getUIDFromUUID(uuid, false);
    }

    public int getUIDFromUUID(String uuid, boolean insert) throws SQLException, BusyException {
        if (uuid == null || uuid.equalsIgnoreCase("#null")) {
            return -1;
        }
        if (uuid.isEmpty()) {
            return 0;
        }
        uuid = uuid.toLowerCase();
        if (uuids.containsValue(uuid)) {
            return uuids.getKey(uuid);
        }
        final String uuidLower = uuid;
        final int hash = uuidLower.hashCode();

        int uid = sql.query("SELECT uid,uuid FROM " + Table.AUXPROTECT_UIDS + " WHERE hash=?", rs -> {
            while (rs.next()) {
                if (!rs.getString("uuid").equalsIgnoreCase(uuidLower)) continue;
                int _uid = rs.getInt("uid");
                uuids.put(_uid, uuidLower);
                return _uid;
            }
            return -1;
        }, 5000L, hash);

        if (uid > 0) return uid;

        if (insert) {
            uid = sql.executeReturnGenerated("INSERT INTO " + Table.AUXPROTECT_UIDS + " (uuid,hash) VALUES (?,?)", 3000L, uuidLower, hash);
            uuids.put(uid, uuidLower);
            plugin.debug("New UUID: " + uuidLower + ":" + uid, 1);
            sql.incrementRows();
        }

        return uid;
    }

    public String getUUIDFromUID(int uid) throws SQLException, BusyException {
        if (uid < 0) {
            return "#null";
        }
        if (uid == 0) {
            return "";
        }
        if (uuids.containsKey(uid)) {
            return uuids.get(uid);
        }
        return sql.query("SELECT uuid FROM " + Table.AUXPROTECT_UIDS + " WHERE uid=?", rs -> {
            if (!rs.next()) return null;
            String uuid = rs.getString("uuid");
            uuids.put(uid, uuid);
            return uuid;
        }, 5000L, uid);
    }

    public Collection<String> getCachedUsernames() {
        return Collections.unmodifiableCollection(usernames.values());
    }

    public byte[] getPendingInventory(int uid) throws SQLException, BusyException {
        if (uid <= 0) {
            return null;
        }
        return sql.query("SELECT pending FROM " + Table.AUXPROTECT_USERDATA_PENDINV + " WHERE uid=?", rs -> {
            if (!rs.next()) return null;
            return sql.getBlob(rs, "pending");
        }, 3000L, uid);
    }

    public void setPendingInventory(int uid, byte[] blob) throws SQLException, BusyException {
        if (uid <= 0) {
            throw new IllegalArgumentException();
        }
        long time = System.currentTimeMillis();

        sql.executeTransaction(connection -> {
            if (blob == null) {
                sql.execute(connection, "DELETE FROM " + Table.AUXPROTECT_USERDATA_PENDINV + " WHERE uid=?", uid);
            } else {
                try {
                    sql.execute(connection, "INSERT INTO " + Table.AUXPROTECT_USERDATA_PENDINV + " (time, uid, pending) VALUES (?,?,?)", time, uid, blob);
                } catch (SQLException e) {
                    if (!ConnectionManager.isConstraintViolation(e)) throw e;
                    sql.execute(connection, "UPDATE " + Table.AUXPROTECT_USERDATA_PENDINV + " SET time=?,pending=? WHERE uid=?", time, blob, uid);
                }
            }
        }, 30000L);
    }

    protected void cleanup() {
        usernames.cleanup();
        uuids.cleanup();
    }

    public void init(Connection connection) throws SQLException {
        String stmt = "CREATE TABLE IF NOT EXISTS " + Table.AUXPROTECT_UIDS;
        if (sql.isMySQL()) {
            stmt += " (uid INTEGER AUTO_INCREMENT, uuid varchar(200) UNIQUE, hash INT, PRIMARY KEY (uid));";
        } else {
            stmt += " (uid INTEGER PRIMARY KEY AUTOINCREMENT, uuid TEXT UNIQUE, hash INT);";
        }
        sql.execute(connection, stmt);
        sql.execute(connection, "CREATE INDEX IF NOT EXISTS idx_" + Table.AUXPROTECT_UIDS + "_hash ON " + Table.AUXPROTECT_UIDS + " (hash)");

        sql.execute(connection, "CREATE TABLE IF NOT EXISTS " + Table.AUXPROTECT_USERDATA_PENDINV + " (time BIGINT, uid INTEGER PRIMARY KEY, pending MEDIUMBLOB)");
    }

    public void clearCache() {
        usernames.clear();
        uuids.clear();
    }
}
