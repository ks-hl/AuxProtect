package dev.heliosares.auxprotect.listeners;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import com.spawnchunk.auctionhouse.AuctionHouse;
import com.spawnchunk.auctionhouse.events.ListItemEvent;
import com.spawnchunk.auctionhouse.events.PurchaseItemEvent;

import dev.heliosares.auxprotect.AuxProtect;
import dev.heliosares.auxprotect.database.DbEntry;
import dev.heliosares.auxprotect.database.EntryAction;
import dev.heliosares.auxprotect.utils.InvSerialization;

public class AuctionHouseListener implements Listener {
	private final AuxProtect plugin;
	@SuppressWarnings("unused")
	private final AuctionHouse ah;

	public AuctionHouseListener(AuxProtect plugin, AuctionHouse plugin2) {
		this.plugin = plugin;
		this.ah = plugin2;
	}

	@EventHandler
	public void onListItemEvent(ListItemEvent e) {
		Location l = null;
		if (e.getSeller() instanceof Player) {
			l = ((Player) e.getSeller()).getLocation();
		} else {
			World world = Bukkit.getWorld(e.getWorld());
			if (world != null) {
				l = new Location(world, 0, 0, 0);
			}
		}

		plugin.dbRunnable.add(new DbEntry("$" + e.getSeller_UUID(), EntryAction.AHLIST, false, l,
				e.getItem().getType().toString().toLowerCase(),
				plugin.formatMoney(e.getPrice()) + InvSerialization.toBase64(e.getItem())));
	}

	@EventHandler
	public void onPurchaseItemEvent(PurchaseItemEvent e) {
		Location l = null;
		if (e.getBuyer() instanceof Player) {
			l = ((Player) e.getBuyer()).getLocation();
		} else if (e.getSeller() instanceof Player) {
			l = ((Player) e.getSeller()).getLocation();
		} else {
			World world = Bukkit.getWorld(e.getWorld());
			if (world != null) {
				l = new Location(world, 0, 0, 0);
			}
		}

		plugin.dbRunnable.add(new DbEntry("$" + e.getBuyer_UUID(), EntryAction.AHBUY, false, l,
				e.getItem().getType().toString().toLowerCase(), "From " + e.getSeller().getName() + " for "
						+ plugin.formatMoney(e.getPrice()) + InvSerialization.toBase64(e.getItem())));
	}
}
