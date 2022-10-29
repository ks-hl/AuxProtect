package dev.heliosares.auxprotect.core.commands;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import dev.heliosares.auxprotect.adapters.SenderAdapter;
import dev.heliosares.auxprotect.core.APPermission;
import dev.heliosares.auxprotect.core.Command;
import dev.heliosares.auxprotect.core.CommandException;
import dev.heliosares.auxprotect.core.IAuxProtect;
import dev.heliosares.auxprotect.core.Language;
import dev.heliosares.auxprotect.core.PlatformType;
import dev.heliosares.auxprotect.database.DbEntry;
import dev.heliosares.auxprotect.database.EntryAction;
import dev.heliosares.auxprotect.database.InvDiffManager;
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

public class InvCommand extends Command {
	public InvCommand(IAuxProtect plugin) {
		super(plugin, "inv", APPermission.INV);
	}

	@Override
	public void onCommand(SenderAdapter sender, String label, String[] args) throws CommandException {
		if (args.length < 2) {
			throw new CommandException.SyntaxException();
		}
		if (sender.getPlatform() != PlatformType.SPIGOT) {
			throw new CommandException.PlatformException();
		}
		if (sender.getSender() instanceof Player player && plugin instanceof AuxProtectSpigot spigot) {
			int index = -1;
			try {
				index = Integer.parseInt(args[1]);
			} catch (NumberFormatException e) {

			}
			if (index < 0) {
				throw new CommandException.SyntaxException();
			}
			Results results = LookupCommand.results.get(player.getUniqueId().toString());
			if (results == null || index >= results.getSize()) {
				throw new CommandException.SyntaxException();
			}

			DbEntry entry = results.get(index);
			if (entry == null) {
				throw new CommandException.SyntaxException();
			}
			plugin.runAsync(() -> {
				if (entry.getAction().equals(EntryAction.INVENTORY)) {
					PlayerInventoryRecord inv_ = null;
					try {
						inv_ = InvSerialization.toPlayerInventory(entry.getBlob());
					} catch (Exception e1) {
						plugin.warning("Error serializing inventory lookup");
						plugin.print(e1);
						sender.sendLang("error");
						return;
					}
					final PlayerInventoryRecord inv = inv_;
					OfflinePlayer target = Bukkit.getOfflinePlayer(UUID.fromString(entry.getUserUUID().substring(1)));
					openSync(plugin, player, makeInventory(plugin, player, target, inv, entry.getTime()));
				} else if (entry.hasBlob()) {
					Pane pane = new Pane(Type.SHOW);
					try {
						Inventory inv = InvSerialization.toInventory(entry.getBlob(), pane, "Item Viewer");
						pane.setInventory(inv);
						openSync(plugin, player, inv);
					} catch (Exception e1) {
						plugin.warning("Error serializing itemviewer");
						plugin.print(e1);
						sender.sendLang("error");
						return;
					}
				}
			});
		} else {
			throw new CommandException.PlatformException();
		}
	}

	public static void openSync(IAuxProtect plugin, Player player, Inventory inventory) {
		plugin.runSync(() -> {
			player.openInventory(inventory);
		});
	}

	public static Inventory makeInventory(IAuxProtect plugin_, Player player, OfflinePlayer target,
			PlayerInventoryRecord inv, long when) {
		if (plugin_ instanceof AuxProtectSpigot plugin) {
			final Player targetO = target.getPlayer();
			Pane enderpane = new Pane(Type.SHOW);

			Inventory enderinv = Bukkit.getServer().createInventory(enderpane, 27, target.getName() + " Ender Chest - "
					+ TimeUtil.millisToString(System.currentTimeMillis() - when) + " ago.");
			enderinv.setContents(inv.ender());

			Pane pane = new Pane(Type.SHOW);
			Inventory mainInv = Bukkit.getServer().createInventory(pane, 54,
					target.getName() + " " + TimeUtil.millisToString(System.currentTimeMillis() - when) + " ago.");
			pane.setInventory(mainInv);

			if (APPermission.INV_RECOVER.hasPermission(player)) {
				if (targetO != null) {
					pane.addButton(49, Material.GREEN_STAINED_GLASS_PANE, new Runnable() {
						private long lastClick = 0;

						@Override
						public void run() {
							if (System.currentTimeMillis() - lastClick > 500) {
								lastClick = System.currentTimeMillis();
								return;
							}

							PlayerInventoryRecord inv_;
							inv_ = InvDiffManager.listToPlayerInv(Arrays.asList(mainInv.getContents()), inv.exp());

							targetO.getInventory().setStorageContents(inv_.storage());
							targetO.getInventory().setArmorContents(inv_.armor());
							targetO.getInventory().setExtraContents(inv_.extra());
							try {
								Experience.setExp(targetO, inv_.exp());
							} catch (Exception e) {
								player.sendMessage("§cUnable to recover experience.");
							}
							player.closeInventory();
							player.sendMessage("§aYou recovered " + target.getName() + "'"
									+ (target.getName().endsWith("s") ? "" : "s") + " inventory.");
							targetO.sendMessage("§a" + player.getName() + " recovered your inventory from "
									+ TimeUtil.millisToString(System.currentTimeMillis() - when) + " ago.");
							targetO.playSound(targetO.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1, 1);
							plugin.add(new DbEntry(AuxProtectSpigot.getLabel(player), EntryAction.RECOVER, false,
									player.getLocation(), AuxProtectSpigot.getLabel(target), "force"));
						}
					}, "§2§lForce Recover Inventory", "", "§a§lDouble click", "",
							"§7This will §c§loverwrite §7the player's", "§7current inventory and exp with",
							"§7what is in the view above.");
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
							player.sendMessage(Language.translate("error"));
						}
						//TODO move to AP table
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
									+ TimeUtil.millisToString(System.currentTimeMillis() - when) + " ago.");
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
				}, "§a§lRecover Inventory", "", "§7This will give the player a", "§7prompt to claim this inventory as",
						"§7if they were opening a chest with", "§7the above contents. They will also get",
						"§7the exp stated here.", "", "§cThis will not overwrite anything and may",
						"§cduplicate items");
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
			// TODO zz backpack goes here
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

			enderpane.onClose(() -> {
				plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
					player.openInventory(mainInv);
				}, 1);
			});
			return mainInv;
		}
		return null;
	}

	@Override
	public boolean exists() {
		return plugin.getPlatform() == PlatformType.SPIGOT;
	}

	@Override
	public List<String> onTabComplete(SenderAdapter sender, String label, String[] args) {
		return null;
	}
}
