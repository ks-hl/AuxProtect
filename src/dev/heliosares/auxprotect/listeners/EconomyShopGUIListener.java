package dev.heliosares.auxprotect.listeners;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;

import dev.heliosares.auxprotect.AuxProtect;
import dev.heliosares.auxprotect.database.DbEntry;
import dev.heliosares.auxprotect.database.EntryAction;
import me.gypopo.economyshopgui.api.events.PreTransactionEvent;

public class EconomyShopGUIListener implements Listener {
	private final AuxProtect plugin;

	public EconomyShopGUIListener(AuxProtect plugin) {
		this.plugin = plugin;
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onPreTransactionEvent(PreTransactionEvent e) {
		if (e.isCancelled()) {
			return;
		}
		ItemStack item = e.getItemStack();
		boolean state = e.getTransactionMode().contains("BUY");
		String data = "ESG, " + plugin.formatMoney(e.getPrice()) + ", QTY: " + item.getAmount();
		if (plugin.getEconomy() != null) {
			data += ", bal: " + plugin.formatMoney(plugin.getEconomy().getBalance(e.getPlayer()));
		}
		DbEntry entry = new DbEntry(AuxProtect.getLabel(e.getPlayer()), EntryAction.SHOP, state,
				e.getPlayer().getLocation(), item.getType().toString().toLowerCase(), data);
		plugin.add(entry);
	}
}
