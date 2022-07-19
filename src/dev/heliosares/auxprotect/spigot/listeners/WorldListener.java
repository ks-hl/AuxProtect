package dev.heliosares.auxprotect.spigot.listeners;

import org.bukkit.entity.ItemFrame;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.hanging.HangingBreakEvent.RemoveCause;
import org.bukkit.event.weather.LightningStrikeEvent;
import dev.heliosares.auxprotect.database.DbEntry;
import dev.heliosares.auxprotect.database.EntryAction;
import dev.heliosares.auxprotect.spigot.AuxProtectSpigot;
import dev.heliosares.auxprotect.utils.InvSerialization;

public class WorldListener implements Listener {

	private AuxProtectSpigot plugin;

	public WorldListener(AuxProtectSpigot plugin) {
		this.plugin = plugin;
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onBlockBreak(LightningStrikeEvent e) {
		plugin.add(new DbEntry("#env", EntryAction.LIGHTNING, false, e.getLightning().getLocation(), "",
				e.getLightning().isEffect() ? "effect" : ""));
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onHangingBreakEvent(HangingBreakEvent e) {
		if (e.getCause() == RemoveCause.ENTITY) {
			return;
		}
		if (e.getEntity() instanceof ItemFrame) {
			final ItemFrame item = (ItemFrame) e.getEntity();
			if (item.getItem() != null) {
				String data = "";
				if (InvSerialization.isCustom(item.getItem())) {
					data = InvSerialization.toBase64(item.getItem());
				}
				DbEntry entry = new DbEntry("#" + e.getCause().toString().toLowerCase(), EntryAction.ITEMFRAME, false,
						item.getLocation(), item.getItem().getType().toString().toLowerCase(), data);
				plugin.add(entry);
				return;
			}
		}

	}
}
