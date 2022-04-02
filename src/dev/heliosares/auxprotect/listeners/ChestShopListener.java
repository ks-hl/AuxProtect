package dev.heliosares.auxprotect.listeners;

import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;

import com.Acrobot.ChestShop.Events.TransactionEvent;
import com.Acrobot.ChestShop.Events.TransactionEvent.TransactionType;

import dev.heliosares.auxprotect.AuxProtect;
import dev.heliosares.auxprotect.database.DbEntry;
import dev.heliosares.auxprotect.database.EntryAction;

public class ChestShopListener implements Listener {
	private final AuxProtect plugin;

	public ChestShopListener(AuxProtect plugin) {
		this.plugin = plugin;
	}

	@EventHandler
	public void onShopPostTransactionEvent(TransactionEvent e) {
		boolean state = e.getTransactionType() == TransactionType.BUY;
		int qty = 0;
		for (ItemStack i : e.getStock()) {
			qty += i.getAmount();
		}
		if (qty == 0) {
			return;
		}
		String data = "CS, " + plugin.formatMoney(e.getExactPrice().doubleValue() / (double) qty) + " each, qty: " + qty
				+ ", shop: " + e.getOwnerAccount().getName();
		if (plugin.getEconomy() != null) {
			data += ", balance: " + plugin.formatMoney(plugin.getEconomy().getBalance(e.getClient()));
		}
		// Client
		plugin.dbRunnable.add(new DbEntry(AuxProtect.getLabel(e.getClient()), EntryAction.SHOP, state,
				e.getSign().getLocation(), e.getStock()[0].getType().toString().toLowerCase(), data));

		data = "CS, " + plugin.formatMoney(e.getExactPrice().doubleValue() / (double) qty) + " each, qty: " + qty
				+ ", client: " + e.getClient().getName();
		if (plugin.getEconomy() != null) {
			data += ", balance: " + plugin.formatMoney(
					plugin.getEconomy().getBalance(Bukkit.getOfflinePlayer(e.getOwnerAccount().getUuid())));
		}
		// Owner
		plugin.dbRunnable.add(new DbEntry("$" + e.getOwnerAccount().getUuid().toString(), EntryAction.SHOP, !state,
				e.getSign().getLocation(), e.getStock()[0].getType().toString().toLowerCase(), data));
	}
}
