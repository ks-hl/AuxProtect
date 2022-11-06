package dev.heliosares.auxprotect.spigot.listeners;

import dev.heliosares.auxprotect.database.DbEntry;
import dev.heliosares.auxprotect.database.EntryAction;
import dev.heliosares.auxprotect.spigot.AuxProtectSpigot;
import dev.heliosares.auxprotect.utils.InvSerialization;
import org.bukkit.entity.ItemFrame;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.hanging.HangingBreakEvent.RemoveCause;
import org.bukkit.event.weather.LightningStrikeEvent;

public class WorldListener implements Listener {

    private AuxProtectSpigot plugin;

    public WorldListener(AuxProtectSpigot plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onLightningStrikeEvent(LightningStrikeEvent e) {
        plugin.add(new DbEntry("#env", EntryAction.LIGHTNING, false, e.getLightning().getLocation(), "",
                e.getLightning().isEffect() ? "effect" : ""));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHangingBreakEvent(HangingBreakEvent e) {
        if (e.getCause() == RemoveCause.ENTITY) {
            return;
        }
        if (e.getEntity() instanceof ItemFrame) {
            final ItemFrame item = (ItemFrame) e.getEntity();
            if (item.getItem() != null) {
                DbEntry entry = new DbEntry("#" + e.getCause().toString().toLowerCase(), EntryAction.ITEMFRAME, false,
                        item.getLocation(), item.getItem().getType().toString().toLowerCase(), "");
                if (InvSerialization.isCustom(item.getItem())) {
                    try {
                        entry.setBlob(InvSerialization.toByteArray(item.getItem()));
                    } catch (Exception e1) {
                        plugin.warning("Error serializing itemframe");
                        plugin.print(e1);
                    }
                }
                plugin.add(entry);
                return;
            }
        }

    }
}
