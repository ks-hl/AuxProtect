package dev.heliosares.auxprotect.spigot.listeners;

import dev.heliosares.auxprotect.database.EntryAction;
import dev.heliosares.auxprotect.database.TransactionEntry;
import dev.heliosares.auxprotect.spigot.AuxProtectSpigot;
import net.brcdev.shopgui.event.ShopPostTransactionEvent;
import net.brcdev.shopgui.shop.ShopManager.ShopAction;
import net.brcdev.shopgui.shop.ShopTransactionResult;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;

public class ShopGUIPlusListener implements Listener {
    private final AuxProtectSpigot plugin;

    public ShopGUIPlusListener(AuxProtectSpigot plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onShopPostTransactionEvent(ShopPostTransactionEvent e) {
        ShopTransactionResult result = e.getResult();
        if (result.getAmount() == 0) return;
        boolean state = result.getShopAction() == ShopAction.BUY;
        ItemStack item = result.getShopItem().getItem();

        plugin.add(new TransactionEntry(AuxProtectSpigot.getLabel(result.getPlayer()), EntryAction.SHOP_SGP, state, result.getPlayer().getLocation(), item.getType().toString().toLowerCase(), "", (short) 0, result.getPrice(), plugin.getEconomy().getBalance(result.getPlayer()), item, "#server"));
    }
}
