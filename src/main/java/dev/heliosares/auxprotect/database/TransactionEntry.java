package dev.heliosares.auxprotect.database;

import org.bukkit.Location;

import javax.annotation.Nullable;
import java.sql.SQLException;

public class TransactionEntry extends DbEntry {
    private final int quantity;
    private final double cost;
    private final double balance;
    private int item_type_id;
    private String itemType;

    public TransactionEntry(String userLabel, EntryAction action, boolean state, @Nullable Location location, String targetLabel, String data, String material, int quantity, double cost, double balance) throws NullPointerException {
        super(userLabel, action, state, location, targetLabel, data);
        this.itemType = material;
        this.quantity = quantity;
        this.cost = cost;
        this.balance = balance;
    }

    protected TransactionEntry(long time, int uid, EntryAction action, boolean state, String world, int x, int y, int z, int pitch, int yaw, int target_id, int item_type, int quantity, int cost, long balance) throws SQLException {
        super(time, uid, action, state, world, x, y, z, pitch, yaw, null, target_id, null);
        this.item_type_id = item_type;
        this.quantity = quantity;
        this.cost = cost / 100D;
        this.balance = balance / 100D;
    }

    public String getItemType(boolean resolve) throws SQLException {
        if (itemType != null || !resolve) {
            return itemType;
        }
        if (item_type_id > 0) {
            itemType = SQLManager.getInstance().getUserManager().getUUIDFromUID(item_type_id, false);
        } else if (item_type_id == 0) {
            return itemType = "";
        }
        if (itemType == null) {
            itemType = "#null";
        }
        return itemType;
    }

    public int getQuantity() {
        return quantity;
    }

    public double getCost() {
        return cost;
    }

    public double getBalance() {
        return balance;
    }

    public int getCostInt() {
        return (int) (cost * 100);
    }

    public long getBalanceLong() {
        return (long) (balance * 100);
    }

    public int getItemTypeID() {
        return item_type_id;
    }
}
