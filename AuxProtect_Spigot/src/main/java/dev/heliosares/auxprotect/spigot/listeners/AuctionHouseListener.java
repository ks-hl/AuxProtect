package dev.heliosares.auxprotect.spigot.listeners;

import com.spawnchunk.auctionhouse.events.ListItemEvent;
import com.spawnchunk.auctionhouse.events.PurchaseItemEvent;
import dev.heliosares.auxprotect.api.AuxProtectAPI;
import dev.heliosares.auxprotect.database.DbEntry;
import dev.heliosares.auxprotect.database.EntryAction;
import dev.heliosares.auxprotect.database.SingleItemEntry;
import dev.heliosares.auxprotect.spigot.AuxProtectSpigot;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

public class AuctionHouseListener implements Listener {
    private final AuxProtectSpigot plugin;

    public AuctionHouseListener(AuxProtectSpigot plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onListItemEvent(ListItemEvent e) {
        Location l = null;
        if (e.getSeller() instanceof Player) {
            l = ((Player) e.getSeller()).getLocation();
        } else {
            World world = Bukkit.getWorld(e.getWorld());
            if (world != null) {
                l = new Location(world, 0, 0, 0);
            }
        }
        DbEntry entry = new SingleItemEntry("$" + e.getSeller_UUID(), EntryAction.AUCTIONLIST, false, l,
                e.getItem().getType().toString().toLowerCase(), plugin.formatMoney(e.getPrice()), e.getItem());
        plugin.add(entry);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPurchaseItemEvent(PurchaseItemEvent e) {
        Location l = null;
        if (e.getBuyer() instanceof Player) {
            l = ((Player) e.getBuyer()).getLocation();
        } else if (e.getSeller() instanceof Player) {
            l = ((Player) e.getSeller()).getLocation();
        } else {
            World world = Bukkit.getWorld(e.getWorld());
            if (world != null) {
                l = new Location(world, 0, 0, 0);
            }
        }

        DbEntry entry = new SingleItemEntry("$" + e.getBuyer_UUID(), EntryAction.AUCTIONBUY, false, l,
                e.getItem().getType().toString().toLowerCase(),
                "From " + e.getSeller().getName() + " for " + plugin.formatMoney(e.getPrice()), e.getItem());
        plugin.add(entry);
    }
}
