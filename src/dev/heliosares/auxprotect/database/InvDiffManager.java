package dev.heliosares.auxprotect.database;

import dev.heliosares.auxprotect.core.IAuxProtect;
import dev.heliosares.auxprotect.database.ConnectionPool.BusyException;
import dev.heliosares.auxprotect.utils.InvSerialization;
import dev.heliosares.auxprotect.utils.InvSerialization.PlayerInventoryRecord;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;

import java.io.IOException;
import java.io.InputStream;
import java.sql.*;
import java.util.*;
import java.util.Map.Entry;

public class InvDiffManager {
    private final SQLManager sql;
    private final IAuxProtect plugin;
    private long blobid;

    public InvDiffManager(SQLManager sql, IAuxProtect plugin) {
        this.sql = sql;
        this.plugin = plugin;
    }

    public void init(Connection connection) throws SQLException {
        try (PreparedStatement stmt = connection
                .prepareStatement("SELECT MAX(blobid) FROM " + Table.AUXPROTECT_INVDIFFBLOB)) {
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    blobid = rs.getLong(1);
                }
            }
        }
    }

    private static class BlobCache {
        long lastused;
        final long blobid;
        final byte[] ablob;

        BlobCache(long blobid, byte[] ablob) {
            this.blobid = blobid;
            this.ablob = ablob;
            touch();
        }

        public void touch() {
            this.lastused = System.currentTimeMillis();
        }
    }

    private final HashMap<Integer, BlobCache> cache = new HashMap<>();

    public void logInvDiff(UUID uuid, int slot, int qty, ItemStack item) throws SQLException, BusyException {
        byte[] blob = null;
        final long time = System.currentTimeMillis();
        Integer damage = null;
        if (qty != 0 && item != null) {
            if (item.getItemMeta() != null && item.getItemMeta() instanceof Damageable meta) {
                damage = meta.getDamage();
                meta.setDamage(0);
                item.setItemMeta(meta);
            }
            try {
                blob = InvSerialization.toByteArraySingle(item);
            } catch (IOException e) {
                plugin.print(e);
                return;
            }
        }
        long blobid = getBlobId(blob);
        String stmt = "INSERT INTO " + Table.AUXPROTECT_INVDIFF.toString()
                + " (time, uid, slot, qty, blobid, damage) VALUES (?,?,?,?,?,?)";

        Connection connection = sql.getWriteConnection(30000);
        try (PreparedStatement statement = connection.prepareStatement(stmt)) {
            statement.setLong(1, time);
            int uid = sql.getUIDFromUUID("$" + uuid.toString(), false);
            statement.setInt(2, uid);
            statement.setInt(3, slot);
            if (qty >= 0) {
                statement.setInt(4, qty);
            } else {
                statement.setNull(4, Types.INTEGER);
            }
            if (blobid >= 0) {
                statement.setLong(5, blobid);
            } else {
                statement.setNull(5, Types.BIGINT);
            }
            if (damage != null) {
                statement.setInt(6, damage);
            } else {
                statement.setNull(6, Types.INTEGER);
            }
            statement.execute();
        } finally {
            sql.returnConnection(connection);
        }
    }

    private long getBlobId(final byte[] blob) throws SQLException, BusyException {
        if (blob == null) {
            return -1;
        }
        long cachedid = -1;
        int hash = Arrays.hashCode(blob);
        BlobCache other;
        synchronized (cache) {
            other = cache.get(hash);
        }
        out:
        if (other != null && blob.length == other.ablob.length) {
            for (int i = 0; i < blob.length; i++) {
                if (blob[i] != other.ablob[i]) {
                    break out; // This iteration isn't really necessary, just a check
                }
            }
            cachedid = other.blobid;
            other.touch();
        }

        if (cachedid > 0) {
            plugin.debug("Used cached blob: " + cachedid, 5);
            return cachedid;
        }

        cachedid = findOrInsertBlob(blob);
        if (cachedid > 0) {
            synchronized (cache) {
                cache.put(hash, new BlobCache(cachedid, blob));
            }
        }
        return cachedid;
    }

    private long findOrInsertBlob(byte[] blob) throws SQLException, BusyException {

        String stmt = "SELECT blobid FROM " + Table.AUXPROTECT_INVDIFFBLOB.toString() + " WHERE ablob=?";
        // synchronized (sql.connection) {
        Connection connection = sql.getConnection();
        try (PreparedStatement statement = connection.prepareStatement(stmt)) {
            if (sql.isMySQL()) {
                Blob sqlblob = connection.createBlob();
                sqlblob.setBytes(1, blob);
                statement.setBlob(1, sqlblob);
            } else {
                statement.setBytes(1, blob);
            }
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    int id = rs.getInt(1);
                    plugin.debug("Looked up blobid: " + id, 5);
                    return id;
                }
            }
        } finally {
            sql.returnConnection(connection);
        }
        connection = sql.getWriteConnection(30000);
        // }
        stmt = "INSERT INTO " + Table.AUXPROTECT_INVDIFFBLOB.toString() + " (blobid, ablob) VALUES (?,?)";
        try (PreparedStatement statement = connection.prepareStatement(stmt)) {
            long blobid = ++this.blobid;
            statement.setLong(1, blobid);
            if (sql.isMySQL()) {
                Blob sqlblob = connection.createBlob();
                sqlblob.setBytes(1, blob);
                statement.setBlob(2, sqlblob);
            } else {
                statement.setBytes(2, blob);
            }
            statement.execute();
            plugin.debug("NEW blobid: " + blobid, 5);
            return blobid;
        } finally {
            sql.returnConnection(connection);
        }
    }

    public static record DiffInventoryRecord(long basetime, int numdiff, PlayerInventoryRecord inventory) {
    }

    ;

    public DiffInventoryRecord getContentsAt(int uid, final long time)
            throws SQLException, IOException, ClassNotFoundException {

        PlayerInventoryRecord inv = null;
        long after = 0;

        // synchronized (sql.connection) {
        Connection connection = sql.getConnection();
        try (PreparedStatement statement = connection.prepareStatement("SELECT time,`blob`" + //
                "\nFROM " + Table.AUXPROTECT_INVBLOB.toString() + //
                "\nWHERE time=(" + "SELECT time FROM " + Table.AUXPROTECT_INVENTORY.toString()
                + " WHERE uid=? AND action_id=? AND time<=? ORDER BY time DESC LIMIT 1);")) {
            statement.setInt(1, uid);
            statement.setInt(2, EntryAction.INVENTORY.id);
            statement.setLong(3, time);
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    after = rs.getLong("time");

                    byte[] blob = null;
                    if (sql.isMySQL()) {
                        try (InputStream in = rs.getBlob("blob").getBinaryStream()) {
                            blob = in.readAllBytes();
                        }
                    } else {
                        blob = rs.getBytes("blob");
                    }
                    if (blob != null) {
                        inv = InvSerialization.toPlayerInventory(blob);
                    }
                }
            }
        } finally {
            sql.returnConnection(connection);
        }
        if (inv == null) {
            return null;
        }
        // }
        List<ItemStack> output = playerInvToList(inv, true);
        // synchronized (sql.connection) {
        int numdiff = 0;
        connection = sql.getConnection();
        try (PreparedStatement statement = connection.prepareStatement("SELECT * FROM "
                + Table.AUXPROTECT_INVDIFF.toString() + " LEFT JOIN " + Table.AUXPROTECT_INVDIFFBLOB.toString() + " ON "
                + Table.AUXPROTECT_INVDIFF.toString() + ".blobid=" + Table.AUXPROTECT_INVDIFFBLOB.toString()
                + ".blobid where uid=? AND time BETWEEN ? AND ? ORDER BY time ASC")) {
            statement.setInt(1, uid);
            statement.setLong(2, after);
            statement.setLong(3, time);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    int slot = rs.getInt("slot");
                    byte[] blob = null;
                    if (sql.isMySQL()) {
                        try (InputStream in = rs.getBlob("ablob").getBinaryStream()) {
                            blob = in.readAllBytes();
                        } catch (Exception ignored) {
                        }
                    } else {
                        blob = rs.getBytes("ablob");
                    }

                    int qty = rs.getInt("qty");
                    ItemStack item;
                    if (qty == 0 && !rs.wasNull()) {
                        item = null;
                    } else {
                        if (blob == null) {
                            item = output.get(slot);
                        } else {
                            item = InvSerialization.toItemStack(blob);
                        }
                        if (item != null) {
                            if (qty > 0) {
                                item.setAmount(qty);
                                plugin.debug("setting slot " + slot + " to " + qty);
                            }
                            if (item.getItemMeta() != null && item.getItemMeta() instanceof Damageable meta) {
                                int damage = rs.getInt("damage");
                                if (!rs.wasNull()) {
                                    meta.setDamage(damage);
                                    item.setItemMeta(meta);
                                }
                            }
                        }
                    }
                    output.set(slot, item);
                    numdiff++;
                }
            }
        } finally {
            sql.returnConnection(connection);
        }
        return new DiffInventoryRecord(after, numdiff, listToPlayerInv(output, inv.exp()));
    }

    public static PlayerInventoryRecord listToPlayerInv(List<ItemStack> contents, int exp) {
        ItemStack[] storage = new ItemStack[36];
        ItemStack[] armor = new ItemStack[4];
        ItemStack[] extra = new ItemStack[1];
        ItemStack[] ender = new ItemStack[27];
        for (int i = 0; i < contents.size(); i++) {
            ItemStack item = contents.get(i);
            if (i < 27) {
                storage[i + 9] = item;
            } else if (i < 36) {
                storage[i - 27] = item;
            } else if (i < 40) {
                armor[4 - i + 35] = item;
            } else if (i < 41) {
                extra[i - 40] = item;
            } else if (i < 68) {
                ender[i - 41] = item;
            } else
                break;
        }
        return new PlayerInventoryRecord(storage, armor, extra, ender, exp);
    }

    public static List<ItemStack> playerInvToList(PlayerInventoryRecord inv, boolean addender) {
        if (inv == null) {
            return null;
        }
        List<ItemStack> output = new ArrayList<>();
        for (int i = 9; i < inv.storage().length; i++) {
            output.add(inv.storage()[i]);
        }
        for (int i = 0; i < 9; i++) {
            output.add(inv.storage()[i]);
        }
        for (int i = inv.armor().length - 1; i >= 0; i--) {
            output.add(inv.armor()[i]);
        }
        for (int i = 0; i < inv.extra().length; i++) {
            output.add(inv.extra()[i]);
        }
        if (addender) {
            for (ItemStack item : inv.ender()) {
                output.add(item);
            }
        }
        return output;
    }

    private long lastcleanup;

    public void cleanup() {
        synchronized (cache) {
            if (System.currentTimeMillis() - lastcleanup < 300000) {
                return;
            }
            lastcleanup = System.currentTimeMillis();
            Iterator<Entry<Integer, BlobCache>> it = cache.entrySet().iterator();
            for (Entry<Integer, BlobCache> other = null; it.hasNext(); ) {
                other = it.next();
                if (System.currentTimeMillis() - other.getValue().lastused > 600000L) {
                    it.remove();
                }
            }
        }
    }
}
