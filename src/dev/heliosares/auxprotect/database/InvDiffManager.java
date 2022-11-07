package dev.heliosares.auxprotect.database;

import dev.heliosares.auxprotect.core.IAuxProtect;
import dev.heliosares.auxprotect.database.ConnectionPool.BusyException;
import dev.heliosares.auxprotect.utils.InvSerialization;
import dev.heliosares.auxprotect.utils.InvSerialization.PlayerInventoryRecord;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentLinkedQueue;

public class InvDiffManager {
    protected final ConcurrentLinkedQueue<InvDiffRecord> queue = new ConcurrentLinkedQueue<>();
    private final SQLManager sql;
    private final IAuxProtect plugin;
    private final HashMap<Integer, BlobCache> cache = new HashMap<>();
    private long blobid;
    private long lastcleanup;

    public InvDiffManager(SQLManager sql, IAuxProtect plugin) {
        this.sql = sql;
        this.plugin = plugin;
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
            } else break;
        }
        return new PlayerInventoryRecord(storage, armor, extra, ender, exp);
    }

    public static List<ItemStack> playerInvToList(PlayerInventoryRecord inv, boolean addender) {
        if (inv == null) {
            return null;
        }
        List<ItemStack> output = new ArrayList<>();
        output.addAll(Arrays.asList(inv.storage()).subList(9, inv.storage().length));
        output.addAll(Arrays.asList(inv.storage()).subList(0, 9));
        for (int i = inv.armor().length - 1; i >= 0; i--) {
            output.add(inv.armor()[i]);
        }
        Collections.addAll(output, inv.extra());
        if (addender) {
            Collections.addAll(output, inv.ender());
        }
        return output;
    }

    protected void init(Connection connection) throws SQLException {
        try (PreparedStatement stmt = connection.prepareStatement("SELECT MAX(blobid) FROM " + Table.AUXPROTECT_INVDIFFBLOB)) {
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    blobid = rs.getLong(1);
                }
            }
        }
    }

    public void logInvDiff(UUID uuid, int slot, int qty, ItemStack item) {
        queue.add(new InvDiffRecord(uuid, slot, qty, item));
    }

    private long getBlobId(Connection connection, final byte[] blob) throws SQLException, BusyException, IOException {
        if (blob == null) {
            return -1;
        }
        final int hash = Arrays.hashCode(blob);
        BlobCache other;
        synchronized (cache) {
            other = cache.get(hash);
        }

        final BlobCache blobCache = new BlobCache(0, blob, hash);

        if (blobCache.equals(other)) {
            other.touch();
            plugin.debug("Used cached blob: " + other.blobid, 5);
            return other.blobid;
        }


        String stmt = "SELECT blobid,ablob FROM " + Table.AUXPROTECT_INVDIFFBLOB + " WHERE hash=?";
        // synchronized (sql.connection) {
        long id = -1;
        try (PreparedStatement statement = connection.prepareStatement(stmt)) {
            statement.setInt(1, hash);
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    long otherid = rs.getLong(1);
                    byte[] otherBytes = sql.getBlob(rs, "ablob");
                    if (blobCache.equals(new BlobCache(otherid, otherBytes, Arrays.hashCode(otherBytes)))) {
                        plugin.debug("Looked up blobid: " + (id = otherid), 5);
                    } else {
                        plugin.warning("Hash collision! " + otherid);
                    }
                }
            }
        }
        if (id < 0) {
            stmt = "INSERT INTO " + Table.AUXPROTECT_INVDIFFBLOB + " (blobid, ablob, hash) VALUES (?,?,?)";
            sql.executeWrite(connection, stmt, id = ++blobid, blob, hash);
            plugin.debug("NEW blobid: " + blobid, 5);
        }
        if (id > 0) {
            synchronized (cache) {
                cache.put(hash, new BlobCache(id, blob, hash));
            }
        }
        return id;
    }

    protected void put(Connection connection) {
        for (InvDiffRecord diff; (diff = queue.poll()) != null; ) {
            byte[] blob = null;
            final long time = System.currentTimeMillis();
            Integer damage = null;
            if (diff.qty() != 0 && diff.item() != null) {
                if (diff.item().getItemMeta() != null && diff.item().getItemMeta() instanceof Damageable meta) {
                    damage = meta.getDamage();
                    meta.setDamage(0);
                    diff.item().setItemMeta(meta);
                }
                try {
                    blob = InvSerialization.toByteArraySingle(diff.item());
                } catch (IOException e) {
                    plugin.print(e);
                    continue;
                }
            }
            try {
                long blobid = getBlobId(connection, blob);
                String stmt = "INSERT INTO " + Table.AUXPROTECT_INVDIFF + " (time, uid, slot, qty, blobid, damage) VALUES (?,?,?,?,?,?)";

                sql.executeWrite(connection, stmt, time, sql.getUserManager().getUIDFromUUID("$" + diff.uuid(), false), diff.slot(), diff.qty() >= 0 ? diff.qty() : null, blobid >= 0 ? blobid : null, damage);
            } catch (SQLException | IOException e) {
                plugin.print(e);
            }
        }
    }

    public DiffInventoryRecord getContentsAt(int uid, final long time) throws SQLException, IOException, ClassNotFoundException, BusyException {

        PlayerInventoryRecord inv = null;
        long after = 0;

        // synchronized (sql.connection) {
        Connection connection = sql.getConnection(false);
        try {
            try (PreparedStatement statement = connection.prepareStatement("SELECT time,`blob`" + //
                    "\nFROM " + Table.AUXPROTECT_INVBLOB + //
                    "\nWHERE time=(" + "SELECT time FROM " + Table.AUXPROTECT_INVENTORY + " WHERE uid=? AND action_id=? AND time<=? ORDER BY time DESC LIMIT 1);")) {
                statement.setInt(1, uid);
                statement.setInt(2, EntryAction.INVENTORY.id);
                statement.setLong(3, time);
                try (ResultSet rs = statement.executeQuery()) {
                    if (rs.next()) {
                        after = rs.getLong("time");

                        byte[] blob;
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
            }
            if (inv == null) {
                return null;
            }
            // }
            List<ItemStack> output = playerInvToList(inv, true);
            // synchronized (sql.connection) {
            int numdiff = 0;
            try (PreparedStatement statement = connection.prepareStatement("SELECT * FROM " + Table.AUXPROTECT_INVDIFF + " LEFT JOIN " + Table.AUXPROTECT_INVDIFFBLOB + " ON " + Table.AUXPROTECT_INVDIFF + ".blobid=" + Table.AUXPROTECT_INVDIFFBLOB + ".blobid where uid=? AND time BETWEEN ? AND ? ORDER BY time ASC")) {
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
            }
            return new DiffInventoryRecord(after, numdiff, listToPlayerInv(output, inv.exp()));
        } finally {
            sql.returnConnection(connection);
        }
    }

    public void cleanup() {
        synchronized (cache) {
            if (System.currentTimeMillis() - lastcleanup < 300000) {
                return;
            }
            lastcleanup = System.currentTimeMillis();
            Iterator<Entry<Integer, BlobCache>> it = cache.entrySet().iterator();
            for (Entry<Integer, BlobCache> other; it.hasNext(); ) {
                other = it.next();
                if (System.currentTimeMillis() - other.getValue().lastused > 600000L) {
                    it.remove();
                }
            }
        }
    }

    private static class BlobCache {
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

    public record InvDiffRecord(UUID uuid, int slot, int qty, ItemStack item) {
    }

    public record DiffInventoryRecord(long basetime, int numdiff, PlayerInventoryRecord inventory) {
    }
}
