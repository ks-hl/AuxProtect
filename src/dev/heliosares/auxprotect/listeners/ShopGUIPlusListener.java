package dev.heliosares.auxprotect.listeners;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import dev.heliosares.auxprotect.AuxProtect;
import dev.heliosares.auxprotect.database.DbEntry;
import dev.heliosares.auxprotect.database.EntryAction;
import net.brcdev.shopgui.event.ShopPostTransactionEvent;
import net.brcdev.shopgui.shop.ShopManager.ShopAction;
import net.brcdev.shopgui.shop.ShopTransactionResult;

public class ShopGUIPlusListener implements Listener {
	private final AuxProtect plugin;

	public ShopGUIPlusListener(AuxProtect plugin) {
		this.plugin = plugin;
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onShopPostTransactionEvent(ShopPostTransactionEvent e) {
		if (e.getResult().getAmount() == 0) {
			return;
		}
		ShopTransactionResult result = e.getResult();
		boolean state = result.getShopAction() == ShopAction.BUY;
		String data = "SGP, " + plugin.formatMoney(result.getPrice()) + ", QTY: " + result.getAmount();
		if (plugin.getEconomy() != null) {
			data += ", bal: " + plugin.formatMoney(plugin.getEconomy().getBalance(result.getPlayer()));
		}
		DbEntry entry = new DbEntry(AuxProtect.getLabel(result.getPlayer()), EntryAction.SHOP, state,
				result.getPlayer().getLocation(), result.getShopItem().getItem().getType().toString().toLowerCase(),
				data);
		plugin.add(entry);
	}
}
