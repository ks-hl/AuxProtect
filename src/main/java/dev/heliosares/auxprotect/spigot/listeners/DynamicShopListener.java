package dev.heliosares.auxprotect.spigot.listeners;

import dev.heliosares.auxprotect.database.DbEntry;
import dev.heliosares.auxprotect.database.EntryAction;
import dev.heliosares.auxprotect.spigot.AuxProtectSpigot;
import me.sat7.dynamicshop.events.ShopBuySellEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;

public class DynamicShopListener implements Listener {
    private final AuxProtectSpigot plugin;

    public DynamicShopListener(AuxProtectSpigot plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onShopBuySellEvent(ShopBuySellEvent e) {
        ItemStack item = e.getItemStack();
        boolean buy = e.isBuy();
        double price;
        if (buy) {
            price = e.getNewBuyPrice();
        } else {
            price = e.getNewSellPrice();
        }
        String data = "DS, " + plugin.formatMoney(price * item.getAmount()) + ", QTY: " + item.getAmount();
        if (plugin.getEconomy() != null) {
            data += ", bal: " + plugin.formatMoney(plugin.getEconomy().getBalance(e.getPlayer()));
        }
        data += ", stock: " + e.getNewStock();
        DbEntry entry = new DbEntry(AuxProtectSpigot.getLabel(e.getPlayer()), EntryAction.SHOP, buy,
                e.getPlayer().getLocation(), item.getType().toString().toLowerCase(), data);
        plugin.add(entry);
    }
}
