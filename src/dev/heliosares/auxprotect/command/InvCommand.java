package dev.heliosares.auxprotect.command;

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

import dev.heliosares.auxprotect.AuxProtect;
import dev.heliosares.auxprotect.database.DbEntry;
import dev.heliosares.auxprotect.database.EntryAction;
import dev.heliosares.auxprotect.database.Results;
import dev.heliosares.auxprotect.utils.Experience;
import dev.heliosares.auxprotect.utils.InvSerialization;
import dev.heliosares.auxprotect.utils.MyPermission;
import dev.heliosares.auxprotect.utils.Pane;
import dev.heliosares.auxprotect.utils.TimeUtil;
import dev.heliosares.auxprotect.utils.Pane.Type;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.hover.content.Text;

public class InvCommand implements CommandExecutor {

	private AuxProtect plugin;

	public InvCommand(AuxProtect plugin, APCommand command) {
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

			String[] data = entry.getData().split(",");
			final ItemStack[] storage = InvSerialization.toItemStackArray(data[0]);
			final ItemStack[] armor = InvSerialization.toItemStackArray(data[1]);
			final ItemStack[] extra = InvSerialization.toItemStackArray(data[2]);
			OfflinePlayer target = Bukkit.getOfflinePlayer(UUID.fromString(entry.getUserUUID().substring(1)));
			final Player targetO = target.getPlayer();
			Pane enderpane = new Pane(Type.SHOW);
			final Inventory ender = InvSerialization.toInventory(data[3], enderpane,
					target.getName() + "'" + (target.getName().endsWith("s") ? "" : "s") + " enderchest");
			enderpane.setInventory(ender);

			Pane pane = new Pane(Type.SHOW);
			Inventory inv = Bukkit.getServer().createInventory(pane, 54, target.getName() + " "
					+ TimeUtil.millisToString(System.currentTimeMillis() - entry.getTime()) + " ago.");
			pane.setInventory(inv);
			if (MyPermission.INV_RECOVER.hasPermission(sender)) {
				if (targetO != null) {
					pane.addButton(49, Material.GREEN_STAINED_GLASS_PANE, new Runnable() {
						private long lastClick = 0;

						@Override
						public void run() {
							if (System.currentTimeMillis() - lastClick > 500) {
								lastClick = System.currentTimeMillis();
								return;
							}

							targetO.getInventory().setStorageContents(storage);
							targetO.getInventory().setArmorContents(armor);
							targetO.getInventory().setExtraContents(extra);
							try {
								System.out.println(data[4]);
								Experience.setExp(targetO, Integer.parseInt(data[4]));
							} catch (Exception e) {
								player.sendMessage("§cUnable to recover experience.");
							}
							player.closeInventory();
							player.sendMessage("§aYou recovered " + target.getName() + "'"
									+ (target.getName().endsWith("s") ? "" : "s") + " inventory.");
							targetO.sendMessage("§a" + player.getName() + " recovered your inventory from "
									+ TimeUtil.millisToString(System.currentTimeMillis() - entry.getTime()) + " ago.");
							targetO.playSound(targetO.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1, 1);
							plugin.add(new DbEntry(AuxProtect.getLabel(player), EntryAction.RECOVER, false,
									player.getLocation(), AuxProtect.getLabel(target), "force"));
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
							output[i] = inv.getItem(i);
						}
						String recover = InvSerialization.toBase64(output);
						plugin.data.getData().set("Recoverables." + target.getUniqueId().toString() + ".inv", recover);
						if (data.length >= 5) {
							try {
								plugin.data.getData().set("Recoverables." + target.getUniqueId().toString() + ".xp",
										Integer.parseInt(data[4]));
							} catch (NumberFormatException e) {
								plugin.getLogger()
										.warning("Unable to save EXP: " + data[4] + " for " + target.getName());
							}
						} else {
							player.sendMessage("§cUnable to recover experience.");
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
						plugin.add(new DbEntry(AuxProtect.getLabel(player), EntryAction.RECOVER, false,
								player.getLocation(), AuxProtect.getLabel(target), "regular"));
					}
				}, "§a§lRecover Inventory");
			}
			if (data.length >= 5) {
				pane.addButton(51, Material.GREEN_STAINED_GLASS_PANE, null, "§2§lPlayer had " + data[4] + "xp");
			} else {
				pane.addButton(51, Material.GREEN_STAINED_GLASS_PANE, null, "§8§lNo XP data");
			}
			pane.addButton(52, Material.BLACK_STAINED_GLASS_PANE, new Runnable() {

				@Override
				public void run() {
					player.openInventory(ender);
				}
			}, "§8§lView Enderchest");
			pane.addButton(53, Material.RED_STAINED_GLASS_PANE, new Runnable() {

				@Override
				public void run() {
					player.closeInventory();
				}
			}, "§c§lClose");

			int i1 = 0;
			for (int i = 9; i < storage.length; i++) {
				if (storage[i] != null)
					inv.setItem(i1, storage[i]);
				i1++;
			}
			for (int i = 0; i < 9; i++) {
				if (storage[i] != null)
					inv.setItem(i1, storage[i]);
				i1++;
			}
			for (int i = armor.length - 1; i >= 0; i--) {
				if (armor[i] != null)
					inv.setItem(i1, armor[i]);
				i1++;
			}
			for (int i = 0; i < extra.length; i++) {
				inv.setItem(i1, extra[i]);
				i1++;
			}
			player.openInventory(inv);
		} else if (entry.getData().contains(InvSerialization.itemSeparator)) {
			String data = entry.getData().substring(entry.getData().indexOf(InvSerialization.itemSeparator));
			ItemStack item = InvSerialization.toItemStack(data);
			Pane pane = new Pane(Type.SHOW);
			Inventory inv = Bukkit.createInventory(pane, 9, "Item Viewer");
			pane.setInventory(inv);
			inv.addItem(item);
			player.openInventory(inv);

		}
		return true;
	}
}
