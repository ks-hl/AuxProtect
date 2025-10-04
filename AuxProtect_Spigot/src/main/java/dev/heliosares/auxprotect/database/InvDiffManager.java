package dev.heliosares.auxprotect.database;

import dev.heliosares.auxprotect.core.IAuxProtect;
import dev.kshl.kshlib.exceptions.BusyException;
import dev.heliosares.auxprotect.utils.InvSerialization;
import dev.heliosares.auxprotect.utils.InvSerialization.PlayerInventoryRecord;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

public class InvDiffManager extends BlobManager {
    protected final ConcurrentLinkedQueue<InvDiffRecord> queue = new ConcurrentLinkedQueue<>();
    private final SQLManager sql;
    private final IAuxProtect plugin;

    public InvDiffManager(SQLManager sql, IAuxProtect plugin) {
        super(Table.AUXPROTECT_INVDIFFBLOB, sql, plugin);
        this.sql = sql;
        this.plugin = plugin;
    }

    public static PlayerInventoryRecord listToPlayerInv(List<ItemStack> contents, int exp) {
        ItemStack[] storage = new ItemStack[36];
        ItemStack[] armor = new ItemStack[4];
        ItemStack[] extra = new ItemStack[3];
        ItemStack[] ender = new ItemStack[27];
        int storageEnd = storage.length; // 36
        int armorEnd = storageEnd + armor.length; // 40
        int extraEnd = armorEnd + extra.length; // 43
        int enderEnd = extraEnd + ender.length; // 70
        for (int i = 0; i < contents.size(); i++) {
            ItemStack item = contents.get(i);
            if (i < 27) {
                storage[i + 9] = item; // hotbar
            } else if (i < storageEnd) {
                storage[i - 27] = item; // main inv
            } else if (i < armorEnd) {
                armor[armor.length - (i - storageEnd) - 1] = item;
            } else if (i < extraEnd) {
                extra[i - armorEnd] = item;
            } else if (i < enderEnd) {
                ender[i - extraEnd] = item;
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
        queue.add(new InvDiffRecord(Snowflake.getNextSnowflake(), uuid, slot, qty, item));
    }

    protected void put(Connection connection) {
        for (InvDiffRecord diff; (diff = queue.poll()) != null; ) {
            byte[] blob = null;
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
                long blobid = getBlobId(connection, blob, diff.snowflake());
                String stmt = "INSERT INTO " + Table.AUXPROTECT_INVDIFF + " (time, uid, slot, qty, blobid, damage) VALUES (?,?,?,?,?,?)";

                sql.execute(connection, stmt, diff.snowflake(), sql.getUserManager().getUIDFromUUID("$" + diff.uuid(), false), diff.slot(), diff.qty() >= 0 ? diff.qty() : null, blobid >= 0 ? blobid : null, damage);
            } catch (SQLException | BusyException e) {
                plugin.print(e);
            }
        }
    }

    public DiffInventoryRecord getContentsAt(int uid, final long time) throws Exception {
        return sql.executeWithException(connection -> {
            long baseSnowflake;
            PlayerInventoryRecord inv;

            try (PreparedStatement statement = connection.prepareStatement(
                    String.format("""
                            SELECT i.time, b.ablob
                            FROM %s i
                            JOIN %s b ON i.blobid = b.blobid
                            WHERE i.uid = ?
                              AND i.action_id = ?
                              AND i.time <= ?
                            ORDER BY i.time DESC
                            LIMIT 1
                            """, Table.AUXPROTECT_INVENTORY, Table.AUXPROTECT_INVBLOB))) {
                statement.setInt(1, uid);
                statement.setInt(2, EntryAction.INVENTORY.id);
                statement.setLong(3, time * Snowflake.COUNTER_FACTOR);
                try (ResultSet rs = statement.executeQuery()) {
                    if (!rs.next()) {
                        plugin.debug("Did not find base inventory");
                        return null;
                    }
                    baseSnowflake = rs.getLong(1);
                    byte[] blob = sql.getBlob(rs, 2);
                    if (rs.wasNull() || blob == null) {
                        plugin.debug("Did not find inventory from blob");
                        return null;
                    }
                    inv = InvSerialization.toPlayerInventory(blob);
                }
            }

            List<ItemStack> output = playerInvToList(inv, true);
            Map<Integer, InvDiffIngredients> ingredientsMap = new HashMap<>();

            int numdiff = 0;
            try (PreparedStatement statement = connection.prepareStatement(String.format("""
                    SELECT *
                    FROM %s AS inv
                    LEFT JOIN %s AS invblob ON inv.blobid=invblob.blobid
                    WHERE uid=?
                     AND time BETWEEN ? AND ?
                    ORDER BY time DESC""", Table.AUXPROTECT_INVDIFF, Table.AUXPROTECT_INVDIFFBLOB))) {
                statement.setInt(1, uid);
                statement.setLong(2, baseSnowflake);
                statement.setLong(3, time * Snowflake.COUNTER_FACTOR);
                try (ResultSet rs = statement.executeQuery()) {
                    while (rs.next()) {
                        numdiff++;
                        int slot = rs.getInt("slot");
                        InvDiffIngredients ingredients = ingredientsMap.computeIfAbsent(slot, s -> new InvDiffIngredients());
                        if (ingredients.quantity == null) {
                            int qty = rs.getInt("qty");
                            if (!rs.wasNull()) {
                                ingredients.quantity = qty;
                            }
                        }
                        if (ingredients.quantity != null && ingredients.quantity <= 0) {
                            continue; // If the most recent quantity is 0, we don't need any other data
                        }
                        if (ingredients.damage == null) {
                            int damage = rs.getInt("damage");
                            if (!rs.wasNull()) {
                                ingredients.damage = damage;
                            }
                        }
                        if (ingredients.blob == null) {
                            byte[] blob = sql.getBlob(rs, "ablob");
                            if (!rs.wasNull()) {
                                ingredients.blob = blob;
                            }
                        }
                    }
                }
                for (int i = 0; i < output.size(); i++) {
                    InvDiffIngredients ingredients = ingredientsMap.get(i);
                    if (ingredients == null) continue; // No change, don't touch the slot

                    if (ingredients.getQuantity() != null && ingredients.getQuantity() <= 0) { // There was change and it ended with nothing in the slot
                        output.set(i, null);
                        continue;
                    }

                    ItemStack item = output.get(i);
                    if (ingredients.getBlob() != null) {
                        item = InvSerialization.toItemStack(ingredients.getBlob());
                    }
                    if (item != null) {
                        if (ingredients.getQuantity() != null) {
                            item.setAmount(ingredients.getQuantity());
                        }
                        plugin.debug("setting slot " + i + " to " + ingredients.quantity);
                        if (ingredients.getDamage() != null && item.getItemMeta() != null && item.getItemMeta() instanceof Damageable meta) {
                            meta.setDamage(ingredients.getDamage());
                            item.setItemMeta(meta);
                        }
                    }
                    output.set(i, item);
                }
            }
            return new DiffInventoryRecord(baseSnowflake / Snowflake.COUNTER_FACTOR, numdiff, listToPlayerInv(output, inv.exp()));
        }, 3000L);
    }

    @Getter
    @Setter
    private static final class InvDiffIngredients {
        private Integer quantity;
        private Integer damage;
        private byte[] blob;
    }

    public record InvDiffRecord(long snowflake, UUID uuid, int slot, int qty, ItemStack item) {
    }

    public record DiffInventoryRecord(long basetime, int numdiff, PlayerInventoryRecord inventory) {
    }
}
