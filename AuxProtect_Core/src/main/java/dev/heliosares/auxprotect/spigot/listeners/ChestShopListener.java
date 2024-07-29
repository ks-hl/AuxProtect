package dev.heliosares.auxprotect.spigot.listeners;

import com.Acrobot.ChestShop.Events.TransactionEvent;
import com.Acrobot.ChestShop.Events.TransactionEvent.TransactionType;
import dev.heliosares.auxprotect.database.EntryAction;
import dev.heliosares.auxprotect.database.TransactionEntry;
import dev.heliosares.auxprotect.spigot.AuxProtectSpigot;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;

public class ChestShopListener implements Listener {
    private final AuxProtectSpigot plugin;

    public ChestShopListener(AuxProtectSpigot plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onShopPostTransactionEvent(TransactionEvent e) {
        boolean state = e.getTransactionType() == TransactionType.BUY;
        short qty = 0;
        for (ItemStack i : e.getStock()) qty += (short) i.getAmount();
        if (qty == 0) return;

        String buyerLabel = AuxProtectSpigot.getLabel(e.getClient());
        String sellerLabel = AuxProtectSpigot.getLabel(e.getOwnerAccount().getUuid());

        plugin.add(new TransactionEntry(buyerLabel, EntryAction.SHOP_CS, state, e.getSign().getLocation(), e.getStock()[0].getType().toString().toLowerCase(), "", qty, e.getExactPrice().doubleValue(), plugin.getEconomy().getBalance(e.getClient()), e.getStock()[0], sellerLabel));
    }
}
