package dev.heliosares.auxprotect.database;

import dev.heliosares.auxprotect.core.IAuxProtect;
import dev.kshl.kshlib.exceptions.BusyException;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class BlobManager {
    private final SQLManager sql;
    private final IAuxProtect plugin;
    protected final HashMap<Integer, BlobCache> cache = new HashMap<>();
    private final Table table;
    private long lastcleanup;

    public BlobManager(Table table, SQLManager sqlManager, IAuxProtect plugin) {
        this.table = table;
        this.sql = sqlManager;
        this.plugin = plugin;
    }

    protected void createTable(Connection connection) throws SQLException {
        sql.execute(connection, "CREATE TABLE IF NOT EXISTS " + table + " (blobid BIGINT PRIMARY KEY, ablob MEDIUMBLOB, hash INT);");

        sql.execute(connection, "CREATE INDEX IF NOT EXISTS idx_" + table + "_blobid ON " + table + " (blobid)");
        sql.execute(connection, "CREATE INDEX IF NOT EXISTS idx_" + table + "_hash ON " + table + " (hash)");
    }

    protected long getBlobId(Connection connection, final byte[] blob, long snowflake) throws SQLException {
        if (blob == null) {
            return -1;
        }
        final int hash = Arrays.hashCode(blob);
        BlobCache other;
        synchronized (cache) {
            other = cache.get(hash);
        }
        cleanup();

        final BlobCache blobCache = new BlobCache(0, blob, hash);

        if (blobCache.equals(other)) {
            other.touch();
            plugin.debug("Used cached blob: " + other.blobid, 5);
            return other.blobid;
        }


        // DESC to use the most recent blobid if there are duplicates. Allows duplicate data to be purged sooner
        long id = sql.query(connection, "SELECT blobid,ablob FROM " + table + " WHERE hash=? ORDER BY blobid DESC", rs -> {
            while (rs.next()) {
                long otherid = rs.getLong(1);
                byte[] otherBytes = sql.getBlob(rs, "ablob");
                if (Arrays.equals(blob, otherBytes)) {
                    plugin.debug("Looked up blobid: " + otherid, 5);
                    return otherid;
                }
            }
            return -1L;
        }, hash);
        if (id < 0) {
            id = snowflake;
            sql.execute(connection, "INSERT INTO " + table + " (blobid, ablob, hash) VALUES (?,?,?)", id, blob, hash);
        }
        if (id > 0) {
            synchronized (cache) {
                cache.put(hash, new BlobCache(id, blob, hash));
            }
        }
        return id;
    }

    public byte[] getBlob(DbEntry entry) throws SQLException, BusyException {
        if (entry.getBlobID() <= 0) return null;
        return sql.query("SELECT ablob FROM " + table + " WHERE blobid=?", rs -> {
            if (!rs.next()) return null;
            return sql.getBlob(rs, 1);
        }, 30000L, entry.getBlobID());
    }

    public void cleanup() {
        synchronized (cache) {
            if (System.currentTimeMillis() - lastcleanup < 30000) {
                return;
            }
            lastcleanup = System.currentTimeMillis();
            Iterator<Map.Entry<Integer, BlobCache>> it = cache.entrySet().iterator();
            for (Map.Entry<Integer, BlobCache> other; it.hasNext(); ) {
                other = it.next();
                if (System.currentTimeMillis() - other.getValue().lastused > 600000L) {
                    it.remove();
                }
            }
        }
    }

    protected static class BlobCache {
        final long blobid;
        final byte[] ablob;
        final int hash;
        long lastused;

        BlobCache(long blobid, byte[] ablob, int hash) {
            this.blobid = blobid;
            this.ablob = ablob;
            this.hash = hash;
            touch();
        }

        public void touch() {
            this.lastused = System.currentTimeMillis();
        }

        @Override
        public boolean equals(Object otherObj) {
            if (otherObj instanceof BlobCache other && ablob.length == other.ablob.length) {
                if (hash != other.hash) {
                    return false;
                }
                for (int i = 0; i < ablob.length; i++) {
                    if (ablob[i] != other.ablob[i]) {
                        return false;
                    }
                }
                return true;
            }
            return false;
        }
    }
}
