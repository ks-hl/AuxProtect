package dev.heliosares.auxprotect.utils;

import java.util.ArrayList;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class Pane implements InventoryHolder {
	private Inventory inventory;
	private ArrayList<Button> buttons;
	public final Type type;
	
	public Pane(Type type) {
		this.type = type;
	}

	@Override
	public Inventory getInventory() {
		return inventory;
	}

	public void setInventory(Inventory inventory) {
		this.inventory = inventory;
		buttons = new ArrayList<>();
	}

	public void addButton(int slot, Material type, Runnable action, String name, String... lore) {
		if (inventory == null) {
			return;
		}
		ItemStack item = new ItemStack(type);
		ItemMeta meta = item.getItemMeta();
		if (name != null) {
			meta.setDisplayName(name);
		}
		if (lore != null) {
			ArrayList<String> loreA = new ArrayList<>();
			for (String lore_ : lore) {
				loreA.add(lore_);
			}
			meta.setLore(loreA);
		}
		item.setItemMeta(meta);
		inventory.setItem(slot, item);
		buttons.add(new Button(action, slot));
	}

	public boolean click(int slot) {
		if (buttons == null) {
			return false;
		}
		for (Button button : buttons) {
			if (slot == button.index) {
				if (button.run != null) {
					button.run.run();
				}
				return true;
			}
		}
		return false;
	}

	private static class Button {
		final Runnable run;
		final int index;

		public Button(Runnable run, int index) {
			this.run = run;
			this.index = index;
		}
	}
	
	public static enum Type{CLAIM,SHOW}
}