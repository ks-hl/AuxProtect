package dev.heliosares.auxprotect.database;

import dev.heliosares.auxprotect.core.IAuxProtect;
import dev.heliosares.auxprotect.exceptions.BusyException;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
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
    private long nextBlobID = 1;
    private long lastcleanup;

    public BlobManager(Table table, SQLManager sqlManager, IAuxProtect plugin) {
        this.table = table;
        this.sql = sqlManager;
        this.plugin = plugin;
    }

    protected void createTable(Connection connection) throws SQLException {
        sql.execute("CREATE TABLE IF NOT EXISTS " + table + " (blobid BIGINT, ablob MEDIUMBLOB, hash INT);", connection);
    }

    protected void init(Connection connection) throws SQLException {
        try (PreparedStatement stmt = connection.prepareStatement("SELECT MAX(blobid) FROM " + table)) {
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    nextBlobID = rs.getLong(1);
                }
            }
        }
    }

    protected long getBlobId(Connection connection, final byte[] blob) throws SQLException {
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
        String stmt = "SELECT blobid,ablob FROM " + table + " WHERE hash=? ORDER BY blobid DESC";
        long id = -1;
        try (PreparedStatement statement = connection.prepareStatement(stmt)) {
            statement.setInt(1, hash);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    long otherid = rs.getLong(1);
                    byte[] otherBytes = sql.getBlob(rs, "ablob");
                    if (blobCache.equals(new BlobCache(otherid, otherBytes, Arrays.hashCode(otherBytes)))) {
                        plugin.debug("Looked up blobid: " + (id = otherid), 5);
                        break;
                    } else {
                        plugin.warning("Hash collision! id=" + otherid);
                    }
                }
            }
        }
        if (id < 0) {
            stmt = "INSERT INTO " + table + " (blobid, ablob, hash) VALUES (?,?,?)";
            sql.execute(stmt, connection, id = ++nextBlobID, blob, hash);
            plugin.debug("NEW blobid: " + nextBlobID, 5);
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
        return sql.executeReturn(connection -> {
            try (PreparedStatement pstmt = connection.prepareStatement("SELECT ablob FROM " + table + " WHERE blobid=" + entry.getBlobID())) {
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        return sql.getBlob(rs, 1);
                    }
                }
            }
            return null;
        }, 30000L, byte[].class);
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
