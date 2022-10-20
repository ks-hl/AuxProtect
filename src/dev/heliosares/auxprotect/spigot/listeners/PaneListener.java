package dev.heliosares.auxprotect.spigot.listeners;

import java.util.ArrayList;
import java.util.HashMap;

import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.ItemStack;

import dev.heliosares.auxprotect.core.APPermission;
import dev.heliosares.auxprotect.spigot.AuxProtectSpigot;
import dev.heliosares.auxprotect.utils.Pane;
import dev.heliosares.auxprotect.utils.Pane.Type;

public class PaneListener implements Listener {

	@SuppressWarnings("unused")
	private AuxProtectSpigot plugin;

	public PaneListener(AuxProtectSpigot plugin) {
		this.plugin = plugin;
	}

	@EventHandler
	public void onInventoryClickEvent(InventoryClickEvent e) {
		if (e.getInventory().getHolder() != null && e.getInventory().getHolder() instanceof Pane pane) {
			if (pane.type == Type.SHOW) {
				if (!APPermission.INV_EDIT.hasPermission(e.getWhoClicked())) {
					e.setCancelled(true);
				}
				if (e.getInventory().equals(e.getClickedInventory())) {
					if (pane.click(e.getSlot())) {
						e.setCancelled(true);
					}
				}
			}
		}
	}

	@EventHandler
	public void onInventoryCloseEvent(InventoryCloseEvent e) {
		if (e.getInventory().getHolder() != null && e.getInventory().getHolder() instanceof Pane pane) {
			if (pane.type == Type.CLAIM) {
				ArrayList<ItemStack> leftover = new ArrayList<>();
				for (int i = 0; i < e.getInventory().getSize(); i++) {
					ItemStack item = e.getInventory().getItem(i);
					if (item == null || item.getType() == Material.AIR)
						continue;
					HashMap<Integer, ItemStack> left = e.getPlayer().getInventory().addItem(item);
					e.getInventory().setItem(i, new ItemStack(Material.AIR));
					if (left != null) {
						leftover.addAll(left.values());
					}
				}
				for (ItemStack i : leftover) {
					e.getPlayer().getWorld().dropItem(e.getPlayer().getLocation(), i);
				}
			}
			pane.close();
		}
	}
}
