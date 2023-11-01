package dev.heliosares.auxprotect.spigot.listeners;

import com.olziedev.playerauctions.api.events.auction.PlayerAuctionBuyEvent;
import com.olziedev.playerauctions.api.events.auction.PlayerAuctionSellEvent;
import dev.heliosares.auxprotect.database.DbEntry;
import dev.heliosares.auxprotect.database.EntryAction;
import dev.heliosares.auxprotect.database.SingleItemEntry;
import dev.heliosares.auxprotect.spigot.AuxProtectSpigot;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

public class ExcellentCratesListener implements Listener {
    private final AuxProtectSpigot plugin;

    public ExcellentCratesListener(AuxProtectSpigot plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onListItemEvent(PlayerAuctionSellEvent e) {
        DbEntry entry = new SingleItemEntry("$" + e.getSeller().getUniqueId(), EntryAction.AUCTIONLIST, false, e.getSeller().getLocation(),
                e.getPlayerAuction().getItem().getType().toString().toLowerCase(), plugin.formatMoney(e.getPlayerAuction().getPrice()), e.getPlayerAuction().getItem());
        plugin.add(entry);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPurchaseItemEvent(PlayerAuctionBuyEvent e) {
        DbEntry entry = new SingleItemEntry("$" + e.getBuyer().getUniqueId(), EntryAction.AUCTIONBUY, false, e.getBuyer().getLocation(),
                e.getPlayerAuction().getItem().getType().toString().toLowerCase(),
                "From " + e.getPlayerAuction().getAuctionPlayer().getName() + " for " + plugin.formatMoney(e.getPrice()), e.getPlayerAuction().getItem());
        plugin.add(entry);
    }
}
