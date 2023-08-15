package dev.heliosares.auxprotect.database;

import dev.heliosares.auxprotect.core.IAuxProtect;
import dev.heliosares.auxprotect.utils.BidiMapCache;

import java.sql.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;

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
        final int uid = this.getUIDFromUUID("$" + uuid, true, true);
        if (uid <= 0) {
            return;
        }
        usernames.put(uid, name);
        sql.execute(connection -> {
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
        return sql.executeReturn(connection -> {
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
            }
            return null;
        }, wait ? 300000L : 3000L, String.class);
    }

    public HashMap<Long, String> getUsernamesFromUID(int uid, boolean wait) throws SQLException {
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

    public int getUIDFromUsername(String username, boolean wait) throws SQLException {
        if (username == null) {
            return -1;
        }
        if (usernames.containsValue(username)) {
            return usernames.getKey(username);
        }
        String stmt = "SELECT * FROM " + Table.AUXPROTECT_LONGTERM
                + " WHERE action_id=? AND target_hash=?\nORDER BY time DESC\nLIMIT 1;";
        plugin.debug(stmt, 3);

        return sql.executeReturn(connection -> {
            try (PreparedStatement pstmt = connection.prepareStatement(stmt)) {
                pstmt.setInt(1, EntryAction.USERNAME.id);
                pstmt.setInt(2, username.toLowerCase().hashCode());
                try (ResultSet results = pstmt.executeQuery()) {
                    while (results.next()) {
                        String username_ = results.getString("target");
                        if (username_ == null || !username_.equalsIgnoreCase(username)) continue;
                        int uid = results.getInt("uid");
                        if (uid > 0) {
                            plugin.debug("Resolved username " + username_ + " to UID " + uid, 5);
                            usernames.put(uid, username_);
                            return uid;
                        }
                    }
                }
            }
            plugin.debug("Unknown UID for " + username, 3);
            return -1;
        }, wait ? 300000L : 3000L, Integer.class);
    }

    public int getUIDFromUUID(String uuid, boolean wait) throws SQLException {
        return getUIDFromUUID(uuid, false, wait);
    }

    public int getUIDFromUUID(String uuid, boolean insert, boolean wait) throws SQLException {
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

        String stmt = "SELECT * FROM " + Table.AUXPROTECT_UIDS + " WHERE hash=?;";
        plugin.debug(stmt, 3);
        final String stmt_ = stmt;
        final String uuid_ = uuid;

        CompletableFuture<Integer> uidHolder = new CompletableFuture<>();
        sql.execute(connection -> {
            try (PreparedStatement pstmt = connection.prepareStatement(stmt_)) {
                pstmt.setInt(1, uuid_.hashCode());
                try (ResultSet results = pstmt.executeQuery()) {
                    while (results.next()) {
                        if (!results.getString("uuid").equals(uuid_)) continue;
                        int uid = results.getInt("uid");
                        uuids.put(uid, uuid_);
                        uidHolder.complete(uid);
                    }
                }
            }
        }, wait ? 300000L : 3000L);
        if (uidHolder.isDone()) return uidHolder.getNow(-1);

        if (insert) {
            stmt = "INSERT INTO " + Table.AUXPROTECT_UIDS + " (uuid,hash) VALUES (?,?)";
            int uid = sql.executeReturnGenerated(stmt, uuid_, uuid_.hashCode());
            uuids.put(uid, uuid_);
            plugin.debug("New UUID: " + uuid_ + ":" + uid, 1);
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
        return sql.executeReturn(connection -> {
            try (Statement statement = connection.createStatement()) {
                String stmt = "SELECT * FROM " + Table.AUXPROTECT_UIDS + " WHERE uid=" + uid;
                plugin.debug(stmt, 3);
                try (ResultSet results = statement.executeQuery(stmt)) {
                    if (results.next()) {
                        String uuid = results.getString("uuid");
                        uuids.put(uid, uuid);
                        return uuid;
                    }
                }
            }
            return null;
        }, wait ? 300000L : 3000L, String.class);
    }

    public Collection<String> getCachedUsernames() {
        return Collections.unmodifiableCollection(usernames.values());
    }

    public byte[] getPendingInventory(int uid) throws SQLException {
        if (uid <= 0) {
            return null;
        }
        return sql.executeReturn(connection -> {
            try (PreparedStatement stmt = connection
                    .prepareStatement("SELECT * FROM " + Table.AUXPROTECT_USERDATA_PENDINV + " WHERE uid=?")) {
                stmt.setInt(1, uid);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return sql.getBlob(rs, "pending");
                    }
                }
            }
            return null;
        }, 3000L, byte[].class);
    }

    public void setPendingInventory(int uid, byte[] blob) throws SQLException {
        if (uid <= 0) {
            throw new IllegalArgumentException();
        }
        long time = System.currentTimeMillis();
        if (blob == null) {
            sql.execute("DELETE FROM " + Table.AUXPROTECT_USERDATA_PENDINV + " WHERE uid=?", 300000L, uid);
        } else {
            try {
                sql.execute(
                        "INSERT INTO " + Table.AUXPROTECT_USERDATA_PENDINV + " (time, uid, pending) VALUES (?,?,?)",
                        300000L, time, uid, blob);
            } catch (SQLException ignored) {
                sql.execute("UPDATE " + Table.AUXPROTECT_USERDATA_PENDINV + " SET time=?,pending=? WHERE uid=?",
                        300000L, time, blob, uid);
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
            stmt += " (uid INTEGER AUTO_INCREMENT, uuid varchar(255) UNIQUE, hash INT, PRIMARY KEY (uid));";
        } else {
            stmt += " (uuid varchar(255) UNIQUE, uid INTEGER PRIMARY KEY AUTOINCREMENT, hash INT);";
        }
        plugin.debug(stmt, 3);
        sql.execute(stmt, connection);
        sql.execute("CREATE TABLE IF NOT EXISTS " + Table.AUXPROTECT_USERDATA_PENDINV
                + " (time BIGINT, uid INTEGER PRIMARY KEY, pending MEDIUMBLOB)", connection);
    }

    public void clearCache() {
        usernames.clear();
        uuids.clear();
    }
}
