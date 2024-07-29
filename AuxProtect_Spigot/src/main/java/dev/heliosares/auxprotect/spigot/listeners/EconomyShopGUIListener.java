package dev.heliosares.auxprotect.spigot.listeners;

import dev.heliosares.auxprotect.database.EntryAction;
import dev.heliosares.auxprotect.database.TransactionEntry;
import dev.heliosares.auxprotect.spigot.AuxProtectSpigot;
import me.gypopo.economyshopgui.api.events.PostTransactionEvent;
import me.gypopo.economyshopgui.objects.ShopItem;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;

public class EconomyShopGUIListener implements Listener {
    private final AuxProtectSpigot plugin;

    public EconomyShopGUIListener(AuxProtectSpigot plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPostTransactionEvent(PostTransactionEvent e) {
        for (ShopItem shopItem : e.getItems().keySet()) {
            ItemStack item = shopItem.getItemToGive();
            boolean state = e.getTransactionType().toString().contains("BUY");

            plugin.add(new TransactionEntry(AuxProtectSpigot.getLabel(e.getPlayer()), EntryAction.SHOP_ESG, state, e.getPlayer().getLocation(), item.getType().toString().toLowerCase(), "", (short) 0, e.getPrice(), plugin.getEconomy().getBalance(e.getPlayer()), item, "#server"));
        }
    }
}
