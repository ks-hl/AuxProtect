package dev.heliosares.auxprotect.spigot.listeners;

import dev.heliosares.auxprotect.database.DbEntry;
import dev.heliosares.auxprotect.database.EntryAction;
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
            String data = "ESG, " + plugin.formatMoney(e.getPrice()) + ", QTY: " + item.getAmount();
            if (plugin.getEconomy() != null) {
                data += ", bal: " + plugin.formatMoney(plugin.getEconomy().getBalance(e.getPlayer()));
            }
            DbEntry entry = new DbEntry(AuxProtectSpigot.getLabel(e.getPlayer()), EntryAction.SHOP, state,
                    e.getPlayer().getLocation(), item.getType().toString().toLowerCase(), data);
            plugin.add(entry);
        }
    }
}
