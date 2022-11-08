package dev.heliosares.auxprotect.database;

import dev.heliosares.auxprotect.core.IAuxProtect;
import dev.heliosares.auxprotect.utils.BidiMapCache;

import java.io.IOException;
import java.sql.*;
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

    public void updateUsernameAndIP(UUID uuid, String name, String ip) throws SQLException {
        final int uid = this.getUIDFromUUID("$" + uuid, true);
        if (uid <= 0) {
            return;
        }
        usernames.put(uid, name);

        Connection connection;
        try {
            connection = sql.getConnection(true);
        } catch (SQLException e1) {
            plugin.print(e1);
            return;
        }
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
        } catch (SQLException e) {
            plugin.print(e);
        } finally {
            sql.returnConnection(connection);
        }
        if (newip) {
            plugin.add(new DbEntry("$" + uuid, EntryAction.IP, false, ip, ""));
        }
        if (!name.equalsIgnoreCase(newestusername)) {
            plugin.debug("New username: " + name + " for " + newestusername);
            plugin.add(new DbEntry("$" + uuid, EntryAction.USERNAME, false, name, ""));
        }
    }

    public String getUsernameFromUID(int uid, boolean wait) throws SQLException {
        if (uid < 0) {
            return null;
        }
        if (uid == 0) {
            return "";
        }
        if (usernames.containsKey(uid)) {
            return usernames.get(uid);
        }
        /*
         * if (plugin.isBungee()) { return uuid; } else { OfflinePlayer player =
         * Bukkit.getOfflinePlayer(UUID.fromString(uuid.substring(1))); if (player !=
         * null) { usernames.put(uuid, player.getName()); return player.getName
         */

        String stmt = "SELECT * FROM " + Table.AUXPROTECT_LONGTERM
                + " WHERE action_id=? AND uid=?\nORDER BY time DESC\nLIMIT 1;";
        plugin.debug(stmt, 3);

        Connection connection = sql.getConnection(wait);
        try (PreparedStatement pstmt = connection.prepareStatement(stmt)) {
            pstmt.setInt(1, EntryAction.USERNAME.id);
            pstmt.setInt(2, uid);
            try (ResultSet results = pstmt.executeQuery()) {
                if (results.next()) {
                    String username = results.getString("target");
                    plugin.debug("Resolved UID " + uid + " to " + username, 5);
                    if (username != null) {
                        usernames.put(uid, username);
                        return username;
                    }
                }
            }
        } finally {
            sql.returnConnection(connection);
        }
        return null;
    }

    public HashMap<Long, String> getUsernamesFromUID(int uid, boolean wait) throws SQLException {
        HashMap<Long, String> out = new HashMap<>();
        String stmt = "SELECT * FROM " + Table.AUXPROTECT_LONGTERM + " WHERE action_id=? AND uid=?;";
        plugin.debug(stmt, 3);

        Connection connection = sql.getConnection(wait);
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
        } finally {
            sql.returnConnection(connection);
        }
        return out;
    }

    public int getUIDFromUsername(String username, boolean wait) throws SQLException {
        if (username == null) {
            return -1;
        }
        if (usernames.containsValue(username)) {
            return usernames.getKey(username);
        }
        String stmt = "SELECT * FROM " + Table.AUXPROTECT_LONGTERM
                + " WHERE action_id=? AND lower(target)=?\nORDER BY time DESC\nLIMIT 1;";
        plugin.debug(stmt, 3);

        Connection connection = sql.getConnection(wait);

        try (PreparedStatement pstmt = connection.prepareStatement(stmt)) {
            pstmt.setInt(1, EntryAction.USERNAME.id);
            pstmt.setString(2, username.toLowerCase());
            try (ResultSet results = pstmt.executeQuery()) {
                if (results.next()) {
                    int uid = results.getInt("uid");
                    String username_ = results.getString("target");
                    plugin.debug("Resolved username " + username_ + " to UID " + uid, 5);
                    if (username_ != null && uid > 0) {
                        usernames.put(uid, username_);
                        return uid;
                    }
                }
            }
        } finally {
            sql.returnConnection(connection);
        }
        plugin.debug("Unknown UID for " + username, 3);
        return -1;
    }

    public int getUIDFromUUID(String uuid, boolean wait) throws SQLException {
        return getUIDFromUUID(uuid, false, wait);
    }

    public int getUIDFromUUID(String uuid, boolean insert, boolean wait) throws SQLException {
        if (uuid == null || uuid.equalsIgnoreCase("#null")) {
            return -1;
        }
        if (uuid.length() == 0) {
            return 0;
        }
        uuid = uuid.toLowerCase();
        if (uuids.containsValue(uuid)) {
            return uuids.getKey(uuid);
        }

        String stmt = "SELECT * FROM " + Table.AUXPROTECT_UIDS + " WHERE uuid=?;";
        plugin.debug(stmt, 3);
        Connection connection = sql.getConnection(wait);
        try (PreparedStatement pstmt = connection.prepareStatement(stmt)) {
            pstmt.setString(1, uuid);
            try (ResultSet results = pstmt.executeQuery()) {
                if (results.next()) {
                    int uid = results.getInt("uid");
                    uuids.put(uid, uuid);
                    return uid;
                }
            }
        } finally {
            sql.returnConnection(connection);
        }

        if (insert) {
            stmt = "INSERT INTO " + Table.AUXPROTECT_UIDS + " (uuid) VALUES (?)";
            int uid = sql.executeWriteReturnGenerated(stmt, uuid);
            uuids.put(uid, uuid);
            plugin.debug("New UUID: " + uuid + ":" + uid, 1);
            sql.incrementRows();
            return uid;
        }
        return -1;
    }

    public String getUUIDFromUID(int uid, boolean wait) throws SQLException {
        if (uid < 0) {
            return "#null";
        }
        if (uid == 0) {
            return "";
        }
        if (uuids.containsKey(uid)) {
            return uuids.get(uid);
        }

        Connection connection = sql.getConnection(wait);
        try (Statement statement = connection.createStatement()) {
            String stmt = "SELECT * FROM " + Table.AUXPROTECT_UIDS + " WHERE uid='" + uid + "';";
            plugin.debug(stmt, 3);
            try (ResultSet results = statement.executeQuery(stmt)) {
                if (results.next()) {
                    String uuid = results.getString("uuid");
                    uuids.put(uid, uuid);
                    return uuid;
                }
            }
        } finally {
            sql.returnConnection(connection);
        }
        return null;
    }

    public Collection<String> getCachedUsernames() {
        return Collections.unmodifiableCollection(usernames.values());
    }

    public byte[] getPendingInventory(int uid) throws SQLException, IOException {
        if (uid <= 0) {
            return null;
        }
        Connection connection = sql.getConnection(false);
        try (PreparedStatement stmt = connection
                .prepareStatement("SELECT * FROM " + Table.AUXPROTECT_USERDATA_PENDINV + " WHERE uid=?")) {
            stmt.setInt(1, uid);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return sql.getBlob(rs, "pending");
                }
            }
        } finally {
            sql.returnConnection(connection);
        }
        return null;
    }

    public void setPendingInventory(int uid, byte[] blob) throws SQLException {
        if (uid <= 0) {
            throw new IllegalArgumentException();
        }
        long time = System.currentTimeMillis();
        if (blob == null) {
            sql.executeWrite("DELETE FROM " + Table.AUXPROTECT_USERDATA_PENDINV + " WHERE uid=?", uid);
        } else {
            try {
                sql.executeWrite(
                        "INSERT INTO " + Table.AUXPROTECT_USERDATA_PENDINV + " (time, uid, pending) VALUES (?,?,?)",
                        time, uid, blob);
            } catch (SQLException ignored) {
                sql.executeWrite("UPDATE " + Table.AUXPROTECT_USERDATA_PENDINV + " SET time=?,pending=? WHERE uid=?",
                        time, blob, uid);
            }
        }
    }

    protected void cleanup() {
        usernames.cleanup();
        uuids.cleanup();
    }

    public void init(Connection connection) throws SQLException {
        String stmt = "CREATE TABLE IF NOT EXISTS " + Table.AUXPROTECT_UIDS;
        if (sql.isMySQL()) {
            stmt += " (uid INTEGER AUTO_INCREMENT, uuid varchar(255), PRIMARY KEY (uid));";
        } else {
            stmt += " (uuid varchar(255), uid INTEGER PRIMARY KEY AUTOINCREMENT);";
        }
        plugin.debug(stmt, 3);
        sql.execute(connection, stmt);
        sql.execute(connection, "CREATE TABLE IF NOT EXISTS " + Table.AUXPROTECT_USERDATA_PENDINV
                + " (time BIGINT, uid INTEGER PRIMARY KEY, pending MEDIUMBLOB)");
    }
}
