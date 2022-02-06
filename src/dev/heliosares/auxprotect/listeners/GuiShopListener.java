package dev.heliosares.auxprotect.listeners;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import dev.heliosares.auxprotect.AuxProtect;
import dev.heliosares.auxprotect.database.DbEntry;
import dev.heliosares.auxprotect.database.EntryAction;
import net.brcdev.shopgui.event.ShopPostTransactionEvent;
import net.brcdev.shopgui.shop.ShopManager.ShopAction;
import net.brcdev.shopgui.shop.ShopTransactionResult;

public class GuiShopListener implements Listener {
	private final AuxProtect plugin;

	public GuiShopListener(AuxProtect plugin) {
		this.plugin = plugin;
	}

	@EventHandler
	public void onShopPostTransactionEvent(ShopPostTransactionEvent e) {
		ShopTransactionResult result = e.getResult();
		boolean state = result.getShopAction() == ShopAction.BUY;
		String data = "SERVER_SHOP, $" + (Math.round(result.getPrice() / (double) result.getAmount() * 100) / 100.0)
				+ " each, QTY: " + result.getAmount();
		if (plugin.getEconomy() != null) {
			data += ", balance: " + plugin.formatMoney(plugin.getEconomy().getBalance(result.getPlayer()));
		}
		DbEntry entry = new DbEntry(AuxProtect.getLabel(result.getPlayer()), EntryAction.SHOP, state,
				result.getPlayer().getLocation(), result.getShopItem().getItem().getType().toString().toLowerCase(),
				data);
		plugin.dbRunnable.add(entry);
	}
}
