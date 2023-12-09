package dev.heliosares.auxprotect.database;

import dev.heliosares.auxprotect.api.AuxProtectAPI;
import dev.heliosares.auxprotect.exceptions.BusyException;
import dev.heliosares.auxprotect.utils.InvSerialization;
import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;

import javax.annotation.Nullable;
import java.io.IOException;
import java.sql.SQLException;

public class TransactionEntry extends DbEntry {
    private final short quantity;
    private final double cost;
    private final double balance;

    protected String targetLabel2;
    protected String target2;
    protected int target_id2;


    public TransactionEntry(String userLabel, EntryAction action, boolean state, @Nullable Location location, String targetLabel, String data, short quantity, double cost, double balance, String targetLabel2) {
        super(userLabel, action, state, location, targetLabel, data);
        this.quantity = quantity;
        this.cost = cost;
        this.balance = balance;
        this.targetLabel2 = targetLabel2;
    }

    // TODO this could be moved to a subclass if needed to make it non-Bukkit specific
    public TransactionEntry(String userLabel, EntryAction action, boolean state, @Nullable Location location, String targetLabel, String data, short quantity, double cost, double balance, @Nullable ItemStack itemStack, String targetLabel2) {
        super(userLabel, action, state, location, targetLabel, data);
        this.quantity = quantity > 0 || itemStack == null ? quantity : (short) itemStack.getAmount();
        this.cost = cost;
        this.balance = balance;
        this.targetLabel2 = targetLabel2;

        if (itemStack == null || !InvSerialization.isCustom(itemStack)) return;

        itemStack = itemStack.clone();
        itemStack.setAmount(quantity);
        try {
            setBlob(InvSerialization.toByteArray(itemStack));
        } catch (IOException e) {
            AuxProtectAPI.getInstance().warning("Failed to serialize " + itemStack);
            AuxProtectAPI.getInstance().print(e);
        }
    }

    protected TransactionEntry(long time, int uid, EntryAction action, boolean state, String world, int x, int y, int z, int pitch, int yaw, int target_id, String data, short quantity, double cost, double balance, int target_id2, SQLManager sql) {
        super(time, uid, action, state, world, x, y, z, pitch, yaw, null, target_id, data, sql);
        this.quantity = quantity;
        this.cost = cost;
        this.balance = balance;
        this.target_id2 = target_id2;
    }

    public short getQuantity() {
        return quantity;
    }

    public double getCost() {
        return cost;
    }

    public double getBalance() {
        return balance;
    }

    public ItemStack getItem() throws SQLException, BusyException, IOException, ClassNotFoundException {
        if (getBlob() == null) return null;

        ItemStack item = InvSerialization.toItemStack(getBlob());
        item.setAmount(quantity);
        return item;
    }

    public int getTargetId2() throws SQLException, BusyException {
        if (action.getTable().hasStringTarget()) {
            return -1;
        }
        if (target_id2 > 0) {
            return target_id2;
        }
        return target_id2 = sql.getUserManager().getUIDFromUUID(getTargetUUID2(), true, true);
    }

    public String getTarget2() throws SQLException, BusyException {
        return getTarget2(true);
    }

    public String getTarget2(boolean resolve) throws SQLException, BusyException {
        if (target2 != null || !resolve) return target2;

        if (action.getTable().hasStringTarget() || !getTargetUUID().startsWith("$") || getTargetUUID().length() != 37) {
            return target2 = getTargetUUID();
        }
        target2 = sql.getUserManager().getUsernameFromUID(getTargetId(), false);
        if (target2 == null) {
            target2 = getTargetUUID();
        }
        return target2;
    }

    public String getTargetUUID2() throws SQLException, BusyException {
        if (targetLabel2 != null) {
            return targetLabel2;
        }
        if (target_id2 > 0) {
            targetLabel2 = sql.getUserManager().getUUIDFromUID(target_id2, false);
        } else if (target_id2 == 0) {
            return targetLabel2 = "";
        }
        if (targetLabel2 == null) {
            targetLabel2 = "#null";
        }
        return targetLabel2;
    }
}
