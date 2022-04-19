package dev.heliosares.auxprotect.listeners;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;

import dev.heliosares.auxprotect.AuxProtect;
import dev.heliosares.auxprotect.database.DbEntry;
import dev.heliosares.auxprotect.database.EntryAction;
import me.sat7.dynamicshop.events.ShopBuySellEvent;

public class DynamicShopListener implements Listener {
	private final AuxProtect plugin;

	public DynamicShopListener(AuxProtect plugin) {
		this.plugin = plugin;
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onShopBuySellEvent(ShopBuySellEvent e) {
		ItemStack item = e.getItemStack();
		boolean buy = e.isBuy();
		double price = 0;
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
		DbEntry entry = new DbEntry(AuxProtect.getLabel(e.getPlayer()), EntryAction.SHOP, buy,
				e.getPlayer().getLocation(), item.getType().toString().toLowerCase(), data);
		plugin.add(entry);
	}
}
