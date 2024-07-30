package dev.heliosares.auxprotect.database;

import dev.heliosares.auxprotect.adapters.message.ClickEvent;
import dev.heliosares.auxprotect.adapters.message.GenericBuilder;
import dev.heliosares.auxprotect.adapters.message.GenericTextColor;
import dev.heliosares.auxprotect.adapters.message.HoverEvent;
import dev.heliosares.auxprotect.adapters.sender.SenderAdapter;
import dev.heliosares.auxprotect.api.AuxProtectAPI;
import dev.heliosares.auxprotect.core.IAuxProtect;
import dev.heliosares.auxprotect.exceptions.BusyException;
import dev.heliosares.auxprotect.utils.InvSerialization;
import jakarta.annotation.Nullable;
import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;

import java.io.IOException;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

public class TransactionEntry extends SpigotDbEntry {
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
            AuxProtectAPI.warning("Failed to serialize " + itemStack);
            AuxProtectAPI.getInstance().print(e);
        }
    }

    public TransactionEntry(long time, int uid, EntryAction action, boolean state, String world, int x, int y, int z, int pitch, int yaw, int target_id, String data, short quantity, double cost, double balance, int target_id2, SQLManager sql) {
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

        if (!getTargetUUID2().startsWith("$") || getTargetUUID2().length() != 37) {
            return target2 = getTargetUUID2();
        }
        target2 = sql.getUserManager().getUsernameFromUID(getTargetId2(), false);
        if (target2 == null) {
            target2 = getTargetUUID2();
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

    @Override
    public void appendTarget(GenericBuilder message, IAuxProtect plugin) throws SQLException, BusyException {
        HoverEvent hoverEvent = null;
        if (getQuantity() > 0) {
            message.append(GenericTextColor.BLUE + " " + getQuantity());
        }
        if (getBlob() != null && getBlob().length > 0) {
            try {
                ItemStack item = InvSerialization.toItemStack(getBlob());
                if (item.hasItemMeta()) {
                    hoverEvent = HoverEvent.showItem(item.getType().getKey().getKey(), getQuantity(), Objects.requireNonNull(item.getItemMeta()).getAsString());
                }
            } catch (ClassNotFoundException | IOException e) {
                AuxProtectAPI.warning("Error while deserializing item " + this);
            }
        }
        message.append((hoverEvent == null ? GenericTextColor.BLUE : (GenericTextColor.AQUA + "[")) + getTarget() + (hoverEvent == null ? "" : "]"));
        message.hover(hoverEvent == null ? Results.clickToCopyHoverEvent : hoverEvent);
        message.click(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, getTarget() /* Still using the base target here for TransactionEntry because I think it's the most useful. */));


        String target2 = getTarget2();
        if (target2 != null && !target2.isEmpty()) {
            String fromTo = getState() ? "from" : "to";
            message.append(GenericTextColor.WHITE + " " + fromTo + " ");
            message.append(GenericTextColor.BLUE + target2).hover(Results.clickToCopyHoverEvent).click(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, target2));
        }
        message.append(GenericTextColor.COLOR_CHAR + "f for ");
        String cost = plugin.formatMoney(getCost());
        message.append(GenericTextColor.BLUE + cost).hover(Results.clickToCopyHoverEvent).click(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, cost));
    }

    @Override
    public void appendData(GenericBuilder message, IAuxProtect plugin, SenderAdapter<?, ?> sender) {
        message.append(" " + GenericTextColor.DARK_GRAY + "[");
        String balance = plugin.formatMoney(getBalance());
        message.append(GenericTextColor.GRAY + "Balance: " + balance).hover(Results.clickToCopyHoverEvent).click(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, balance));
        message.append(GenericTextColor.DARK_GRAY + "]");
    }
}
