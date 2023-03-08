package dev.heliosares.auxprotect.database;

import org.bukkit.Location;

public class PickupEntry extends DbEntry {
    private int quantity;

    public PickupEntry(String userUuid, EntryAction action, boolean state, Location location, String target,
                       int quantity) {
        super(userUuid, action, state, location, target, "");
        this.quantity = quantity;
    }

    public void add(PickupEntry entry) {
        this.quantity += entry.quantity;
    }

    @Override
    public String getData() {
        return "x" + quantity;
    }
}
