package dev.heliosares.auxprotect.spigot.listeners;

import dev.heliosares.auxprotect.database.EntryAction;
import dev.heliosares.auxprotect.database.TransactionEntry;
import dev.heliosares.auxprotect.spigot.AuxProtectSpigot;
import me.sat7.dynamicshop.events.ShopBuySellEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;

import java.io.IOException;

public class DynamicShopListener implements Listener {
    private final AuxProtectSpigot plugin;

    public DynamicShopListener(AuxProtectSpigot plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onShopBuySellEvent(ShopBuySellEvent e) {
        ItemStack item = e.getItemStack();
        boolean buy = e.isBuy();
        double price = buy ? e.getNewBuyPrice() : e.getNewSellPrice();

        plugin.add(new TransactionEntry(AuxProtectSpigot.getLabel(e.getPlayer()), EntryAction.SHOP_DS, buy, e.getPlayer().getLocation(), item.getType().toString().toLowerCase(), "", (short) 0, price, plugin.getEconomy().getBalance(e.getPlayer()), item, "#server"));
    }
}
