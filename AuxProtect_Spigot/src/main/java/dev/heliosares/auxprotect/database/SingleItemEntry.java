package dev.heliosares.auxprotect.database;

import dev.heliosares.auxprotect.adapters.message.GenericBuilder;
import dev.heliosares.auxprotect.adapters.message.GenericTextColor;
import dev.heliosares.auxprotect.adapters.sender.SenderAdapter;
import dev.heliosares.auxprotect.api.AuxProtectAPI;
import dev.heliosares.auxprotect.core.IAuxProtect;
import dev.heliosares.auxprotect.exceptions.BusyException;
import dev.heliosares.auxprotect.utils.InvSerialization;
import jakarta.annotation.Nullable;
import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.SkullMeta;

import java.io.IOException;
import java.sql.SQLException;

public class SingleItemEntry extends SpigotDbEntry {

    private final int qty;
    private final int damage;
    @Nullable
    private ItemStack item;

    public SingleItemEntry(String userLabel, EntryAction action, boolean state, Location location, String targetLabel, String data, @Nullable ItemStack item) {
        super(userLabel, action, state, location, targetLabel, data);
        if (item != null) {
            item = item.clone();
            qty = item.getAmount();
            item.setAmount(1);
            if (item.hasItemMeta() && item.getItemMeta() instanceof Damageable meta) {
                damage = meta.getDamage();
                meta.setDamage(0);
                try {
                    item.setItemMeta(meta);
                } catch (NullPointerException e) {
                    if (!(meta instanceof SkullMeta)) {
                        throw e;
                    }
                    // For some reason, SkullMeta is throwing a null pointer randomly. Shouldn't affect
                    // anything because setting the damage to 0 is only done to reduce the number of blobs
                    // stored in the database.
                }
            } else damage = 0;
            this.item = item;
            try {
                setBlob(InvSerialization.toByteArray(item));
            } catch (IOException e) {
                AuxProtectAPI.warning("Failed to serialize item: " + item.getType() + " at " + getTime() + "e");
                AuxProtectAPI.getInstance().print(e);
            }
        } else {
            qty = -1;
            damage = -1;
        }
    }

    public SingleItemEntry(long time, int uid, EntryAction action, boolean state, String world, int x, int y, int z, int pitch, int yaw, String target, int target_id, String data, int qty, int damage) {
        super(time, uid, action, state, world, x, y, z, pitch, yaw, target, target_id, data, SQLManager.getInstance());
        this.qty = qty;
        this.damage = damage;
    }

    public ItemStack getItem() throws SQLException, BusyException {
        if (item != null) {
            return item;
        }
        byte[] blob = super.getBlob();
        if (blob == null) return null;
        try {
            item = InvSerialization.toItemStack(blob);
        } catch (ClassNotFoundException | IOException e) {
            AuxProtectAPI.getInstance().print(e);
            return null;
        }
        item.setAmount(qty);
        if (item.hasItemMeta() && item.getItemMeta() instanceof Damageable meta) {
            meta.setDamage(damage);
            item.setItemMeta(meta);
        }
        return item;
    }

    public int getQty() {
        return qty;
    }

    public int getDamage() {
        return damage;
    }

    @Override
    public void appendData(GenericBuilder message, IAuxProtect plugin, SenderAdapter<?, ?> sender) {
        message.append(" " + GenericTextColor.COLOR_CHAR + "8[" + GenericTextColor.COLOR_CHAR + "7x" + getQty() + (getDamage() > 0 ? ", " + getDamage() + " damage" : "") + GenericTextColor.COLOR_CHAR + "8]");
        super.appendData(message, plugin, sender);
    }
}
