package dev.heliosares.auxprotect.database;

import dev.heliosares.auxprotect.core.IAuxProtect;
import dev.heliosares.auxprotect.utils.BidiMapCache;
import dev.kshl.kshlib.exceptions.BusyException;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Collections;
import java.util.Objects;
import java.util.UUID;

public class SQLUserManager {
    private final IAuxProtect plugin;
    private final SQLManager sql;
    private final BidiMapCache<Integer, String> usernames = new BidiMapCache<>(300000L, 300000L, true);

    public SQLUserManager(IAuxProtect plugin, SQLManager sql) {
        this.plugin = plugin;
        this.sql = sql;
    }

    public void updateUsernameAndIP(UUID uuid, String name, String ip) throws SQLException, BusyException {
        Objects.requireNonNull(uuid, "UUID cannot be null");
        Objects.requireNonNull(name, "Username cannot be null");
        Objects.requireNonNull(ip, "IP cannot be null");

        final int uid = this.getUID("$" + uuid, true);
        if (uid <= 0) {
            return;
        }
        usernames.put(uid, name);
        sql.executeTransaction(connection -> {
            int ipID = getUID(connection, ip, true);
            if (sql.query(connection, "SELECT 1 FROM " + Table.AUXPROTECT_LONGTERM + " WHERE target_id=?", ResultSet::next, ipID)) {
                plugin.add(new DbEntry("$" + uuid, EntryAction.IP, false, ip, ""));
            }
            String stmt = String.format("""
                    SELECT value
                    FROM %s
                    LEFT JOIN %s AS u
                        ON u.id=target_id
                    WHERE
                        uid=?
                        AND action_id=?
                    ORDER BY time DESC
                    LIMIT 1
                    """, Table.AUXPROTECT_LONGTERM, Table.AUXPROTECT_UIDS);
            if (sql.query(connection, stmt, rs -> !name.equalsIgnoreCase(rs.getString(1)), uid, EntryAction.USERNAME.id)) {
                plugin.add(new DbEntry("$" + uuid, EntryAction.USERNAME, false, name, ""));
            }
        }, 300000L);
    }

    public String getUsernameFromUID(int uid) throws SQLException, BusyException {
        return sql.execute(connection -> {
            return getUsernameFromUID(connection, uid);
        }, 3000L);
    }

    public String getUsernameFromUID(Connection connection, int uid) throws SQLException {
        if (uid < 0) {
            return null;
        }
        if (uid == 0) {
            return "";
        }
        if (usernames.containsKey(uid)) {
            return usernames.get(uid);
        }

        return sql.query(connection, String.format("""
                SELECT value FROM %s
                LEFT JOIN %s AS u ON u.id=target_id
                WHERE
                    action_id=?
                    AND uid=?
                ORDER BY time DESC
                LIMIT 1
                """, Table.AUXPROTECT_LONGTERM, Table.AUXPROTECT_UIDS), rs -> {
            if (!rs.next()) return null;

            String username = rs.getString(1);
            plugin.debug("Resolved UID " + uid + " to " + username, 2);
            if (username != null) {
                usernames.put(uid, username);
            }
            return username;
        }, EntryAction.USERNAME.id, uid);
    }

    public long getJoinTime(int uid) throws SQLException, BusyException {
        return sql.query("SELECT MIN(time) FROM " + Table.AUXPROTECT_LONGTERM + " WHERE uid=?", rs -> {
            if (!rs.next()) return 0L;
            return rs.getLong(1);
        }, 3000L, uid) / Snowflake.COUNTER_FACTOR;
    }

    public int getUID(String value, boolean insert) throws SQLException, BusyException {
        if (value == null || value.equalsIgnoreCase("#null")) return -1;
        if (value.isBlank()) return 0;
        return sql.getUidManager().getIDOpt(value, insert).orElse(-1);
    }

    public int getUID(Connection connection, String value, boolean insert) throws SQLException {
        if (value == null || value.equalsIgnoreCase("#null")) return -1;
        if (value.isBlank()) return 0;
        return sql.getUidManager().getIDOpt(connection, value, insert).orElse(-1);
    }

    public int getUIDFromUsernameID(int nameID) throws SQLException, BusyException {
        if (nameID <= 0) return -1;
        return sql.query("SELECT uid FROM " + Table.AUXPROTECT_LONGTERM + " WHERE target_id=? AND action_id=? ORDER BY time DESC LIMIT 1", rs -> {
            if (!rs.next()) return -1;
            return rs.getInt("uid");
        }, 3000L, nameID, EntryAction.USERNAME.id);
    }

    public String getUUIDFromUID(int uid) throws SQLException, BusyException {
        if (uid < 0) {
            return "#null";
        }
        if (uid == 0) {
            return "";
        }
        return sql.getUidManager().getValueOpt(uid).orElse(null);
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
                    if (!sql.isConstraintViolation(e)) throw e;
                    sql.execute(connection, "UPDATE " + Table.AUXPROTECT_USERDATA_PENDINV + " SET time=?,pending=? WHERE uid=?", time, blob, uid);
                }
            }
        }, 30000L);
    }

    protected void cleanup() {
        usernames.cleanup();
    }

    public void init(Connection connection) throws SQLException {
        sql.execute(connection, "CREATE TABLE IF NOT EXISTS " + Table.AUXPROTECT_USERDATA_PENDINV + " (time BIGINT, uid INTEGER PRIMARY KEY, pending MEDIUMBLOB)");
    }
}
