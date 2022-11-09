package dev.heliosares.auxprotect.database;

import dev.heliosares.auxprotect.core.IAuxProtect;
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
import java.util.concurrent.ConcurrentLinkedQueue;

public class InvDiffManager extends BlobManager {
    protected final ConcurrentLinkedQueue<InvDiffRecord> queue = new ConcurrentLinkedQueue<>();
    private final SQLManager sql;
    private final IAuxProtect plugin;
    private long nextBlobID;

    public InvDiffManager(SQLManager sql, IAuxProtect plugin) {
        super(Table.AUXPROTECT_INVDIFFBLOB, sql, plugin);
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

    public void logInvDiff(UUID uuid, int slot, int qty, ItemStack item) {
        queue.add(new InvDiffRecord(uuid, slot, qty, item));
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

    public DiffInventoryRecord getContentsAt(int uid, final long time) throws SQLException, IOException, ClassNotFoundException {

        PlayerInventoryRecord inv = null;
        long after = 0;

        // synchronized (sql.connection) {
        Connection connection = sql.getConnection(false);
        try {
            String stmt = "SELECT time,blobid,ablob FROM " + Table.AUXPROTECT_INVBLOB +
                    " LEFT JOIN " + Table.AUXPROTECT_INVENTORY + " ON " + Table.AUXPROTECT_INVDIFF + ".blobid=" + Table.AUXPROTECT_INVDIFFBLOB +
                    ".blobid where uid=? AND time BETWEEN ? AND ? ORDER BY time ASC";
            try (PreparedStatement statement = connection.prepareStatement("SELECT blobid,`blob`" + //
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
            try (PreparedStatement statement = connection.prepareStatement("SELECT * FROM " + Table.AUXPROTECT_INVDIFF +
                    " LEFT JOIN " + Table.AUXPROTECT_INVDIFFBLOB + " ON " + Table.AUXPROTECT_INVDIFF + ".blobid=" + Table.AUXPROTECT_INVDIFFBLOB +
                    ".blobid where uid=? AND time BETWEEN ? AND ? ORDER BY time ASC")) {
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

    public record InvDiffRecord(UUID uuid, int slot, int qty, ItemStack item) {
    }

    public record DiffInventoryRecord(long basetime, int numdiff, PlayerInventoryRecord inventory) {
    }
}
