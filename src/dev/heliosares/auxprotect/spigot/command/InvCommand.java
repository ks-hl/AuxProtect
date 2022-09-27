package dev.heliosares.auxprotect.spigot.command;

import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import dev.heliosares.auxprotect.core.APPermission;
import dev.heliosares.auxprotect.database.DbEntry;
import dev.heliosares.auxprotect.database.EntryAction;
import dev.heliosares.auxprotect.database.Results;
import dev.heliosares.auxprotect.spigot.AuxProtectSpigot;
import dev.heliosares.auxprotect.utils.Experience;
import dev.heliosares.auxprotect.utils.InvSerialization;
import dev.heliosares.auxprotect.utils.InvSerialization.PlayerInventoryRecord;
import dev.heliosares.auxprotect.utils.Pane;
import dev.heliosares.auxprotect.utils.TimeUtil;
import dev.heliosares.auxprotect.utils.Pane.Type;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.hover.content.Text;

public class InvCommand implements CommandExecutor {

	private AuxProtectSpigot plugin;

	public InvCommand(AuxProtectSpigot plugin, APCommand command) {
		this.plugin = plugin;
	}

	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
		if (args.length < 2) {
			return true;
		}
		if (!(sender instanceof Player)) {
			return true;
		}
		Player player = (Player) sender;
		int idnex = -1;
		try {
			idnex = Integer.parseInt(args[1]);
		} catch (NumberFormatException e) {

		}
		if (idnex < 0) {
			return true;
		}
		Results results = LookupCommand.results.get(player.getUniqueId().toString());
		if (results == null || idnex >= results.getSize()) {
			return true;
		}

		DbEntry entry = results.get(idnex);
		if (entry == null) {
			return true;
		}
		if (entry.getAction().equals(EntryAction.INVENTORY)) {
			PlayerInventoryRecord inv_ = null;
			try {
				inv_ = InvSerialization.toPlayerInventory(entry.getBlob());
			} catch (Exception e1) {
				plugin.warning("Error serializing inventory lookup");
				plugin.print(e1);
				sender.sendMessage(plugin.translate("error"));
				return true;
			}
			final PlayerInventoryRecord inv = inv_;
			OfflinePlayer target = Bukkit.getOfflinePlayer(UUID.fromString(entry.getUserUUID().substring(1)));
			final Player targetO = target.getPlayer();
			Pane enderpane = new Pane(Type.SHOW);

			Inventory enderinv = Bukkit.getServer().createInventory(enderpane, 27, target.getName() + " Ender Chest - "
					+ TimeUtil.millisToString(System.currentTimeMillis() - entry.getTime()) + " ago.");
			enderinv.setContents(inv.ender());

			Pane pane = new Pane(Type.SHOW);
			Inventory mainInv = Bukkit.getServer().createInventory(pane, 54, target.getName() + " "
					+ TimeUtil.millisToString(System.currentTimeMillis() - entry.getTime()) + " ago.");
			pane.setInventory(mainInv);

			if (APPermission.INV_RECOVER.hasPermission(sender)) {
				if (targetO != null) {
					pane.addButton(49, Material.GREEN_STAINED_GLASS_PANE, new Runnable() {
						private long lastClick = 0;

						@Override
						public void run() {
							if (System.currentTimeMillis() - lastClick > 500) {
								lastClick = System.currentTimeMillis();
								return;
							}

							targetO.getInventory().setStorageContents(inv.storage());
							targetO.getInventory().setArmorContents(inv.armor());
							targetO.getInventory().setExtraContents(inv.extra());
							try {
								Experience.setExp(targetO, inv.exp());
							} catch (Exception e) {
								player.sendMessage("§cUnable to recover experience.");
							}
							player.closeInventory();
							player.sendMessage("§aYou recovered " + target.getName() + "'"
									+ (target.getName().endsWith("s") ? "" : "s") + " inventory.");
							targetO.sendMessage("§a" + player.getName() + " recovered your inventory from "
									+ TimeUtil.millisToString(System.currentTimeMillis() - entry.getTime()) + " ago.");
							targetO.playSound(targetO.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1, 1);
							plugin.add(new DbEntry(AuxProtectSpigot.getLabel(player), EntryAction.RECOVER, false,
									player.getLocation(), AuxProtectSpigot.getLabel(target), "force"));
						}
					}, "§2§lForce Recover Inventory", "", "§a§lDouble click", "",
							"§c!!! This will recover the inventory as saved !!!", "§cNot as edited.",
							"§cDo a regular recovery to recover as edited.");
				} else {
					pane.addButton(49, Material.GRAY_STAINED_GLASS_PANE, null,
							"§8§lForce Recover Inventory Unavailable", "§cPlayer must be online to",
							"§cforce recover their inventory.");
				}
				pane.addButton(50, Material.GREEN_STAINED_GLASS_PANE, new Runnable() {

					@Override
					public void run() {
						ItemStack[] output = new ItemStack[45];
						for (int i = 0; i < output.length; i++) {
							output[i] = mainInv.getItem(i);
						}
						String recover = null;
						try {
							recover = InvSerialization.toBase64(InvSerialization.toByteArray(output));
						} catch (Exception e1) {
							plugin.warning("Error serializing inventory recovery");
							plugin.print(e1);
							sender.sendMessage(plugin.translate("error"));
						}
						// TODO Implemented for future use...
						plugin.data.getData().set("Recoverables." + target.getUniqueId().toString() + ".time",
								System.currentTimeMillis());
						plugin.data.getData().set("Recoverables." + target.getUniqueId().toString() + ".inv", recover);
						if (inv.exp() > 0) {
							plugin.data.getData().set("Recoverables." + target.getUniqueId().toString() + ".xp",
									inv.exp());
						}
						plugin.data.save();
						player.sendMessage("§aYou recovered " + target.getName() + "'"
								+ (target.getName().endsWith("s") ? "" : "s") + " inventory.");
						if (targetO != null) {
							targetO.sendMessage("§a" + player.getName() + " recovered your inventory from "
									+ TimeUtil.millisToString(System.currentTimeMillis() - entry.getTime()) + " ago.");
							targetO.sendMessage("§7Ensure you have room in your inventory before claiming!");
							ComponentBuilder message = new ComponentBuilder();
							message.append("§f\n         ");
							message.append("§a[Claim]")
									.event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/claiminv"))
									.event(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
											new Text("§aClick to claim your recovered inventory")));
							message.append("\n§f").event((ClickEvent) null).event((HoverEvent) null);
							targetO.spigot().sendMessage(message.create());
							targetO.playSound(targetO.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1, 1);
						}
						player.closeInventory();
						plugin.add(new DbEntry(AuxProtectSpigot.getLabel(player), EntryAction.RECOVER, false,
								player.getLocation(), AuxProtectSpigot.getLabel(target), "regular"));
					}
				}, "§a§lRecover Inventory");
			}
			int space = 53;
			pane.addButton(space--, Material.RED_STAINED_GLASS_PANE, new Runnable() {

				@Override
				public void run() {
					player.closeInventory();
				}
			}, "§c§lClose");
			pane.addButton(space--, Material.BLACK_STAINED_GLASS_PANE, new Runnable() {

				@Override
				public void run() {
					player.openInventory(enderinv);
				}
			}, "§8§lView Enderchest");
			// TODO backpack goes here
			pane.addButton(space--, Material.GREEN_STAINED_GLASS_PANE, null,
					inv.exp() >= 0 ? ("§2§lPlayer had " + inv.exp() + "xp") : "§8§lNo XP data");

			int i1 = 0;
			for (int i = 9; i < inv.storage().length; i++) {
				if (inv.storage()[i] != null)
					mainInv.setItem(i1, inv.storage()[i]);
				i1++;
			}
			for (int i = 0; i < 9; i++) {
				if (inv.storage()[i] != null)
					mainInv.setItem(i1, inv.storage()[i]);
				i1++;
			}
			for (int i = inv.armor().length - 1; i >= 0; i--) {
				if (inv.armor()[i] != null)
					mainInv.setItem(i1, inv.armor()[i]);
				i1++;
			}
			for (int i = 0; i < inv.extra().length; i++) {
				if (inv.extra()[i] != null)
					mainInv.setItem(i1, inv.extra()[i]);
				i1++;
			}
			player.openInventory(mainInv);
		} else if (entry.hasBlob()) {
			Pane pane = new Pane(Type.SHOW);
			try {
				Inventory inv = InvSerialization.toInventory(entry.getBlob(), pane, "Item Viewer");
				pane.setInventory(inv);
				player.openInventory(inv);
			} catch (Exception e1) {
				plugin.warning("Error serializing itemviewer");
				plugin.print(e1);
				sender.sendMessage(plugin.translate("error"));
				return true;
			}
		}
		return true;
	}
}
