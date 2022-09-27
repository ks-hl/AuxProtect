package dev.heliosares.auxprotect.spigot.command;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import dev.heliosares.auxprotect.core.APPermission;
import dev.heliosares.auxprotect.spigot.AuxProtectSpigot;
import dev.heliosares.auxprotect.utils.Experience;
import dev.heliosares.auxprotect.utils.InvSerialization;
import dev.heliosares.auxprotect.utils.Pane;
import dev.heliosares.auxprotect.utils.Pane.Type;

public class ClaimInvCommand implements CommandExecutor {

	private AuxProtectSpigot plugin;

	public ClaimInvCommand(AuxProtectSpigot plugin) {
		this.plugin = plugin;
	}

	@SuppressWarnings("deprecation")
	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
		if (args.length == 1 && APPermission.INV_RECOVER.hasPermission(sender)) {
			OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
			if (target == null) {
				sender.sendMessage("§cPlayer not found.");
				return true;
			}
			String data = plugin.data.getData().getString("Recoverables." + target.getUniqueId().toString() + ".inv");
			if (data == null) {
				sender.sendMessage("§cThat player has no claimable inventory to cancel.");
				return true;
			}
			plugin.data.getData().set("Recoverables." + target.getUniqueId().toString(), null);
			plugin.data.save();
			if (target.getPlayer() != null) {
				Player targetP = target.getPlayer();
				if (targetP.getOpenInventory() != null && targetP.getOpenInventory().getTopInventory() != null
						&& targetP.getOpenInventory().getTopInventory().getHolder() != null
						&& targetP.getOpenInventory().getTopInventory().getHolder() instanceof Pane) {
					targetP.closeInventory();
				}
				targetP.sendMessage("§cYour claimable inventory was cancelled.");
			}
			sender.sendMessage("§aYou cancelled " + target.getName() + "'" + (target.getName().endsWith("s") ? "" : "s")
					+ " claimable inventory.");
		} else if (sender instanceof Player) {
			Player player = (Player) sender;
			String data = plugin.data.getData().getString("Recoverables." + player.getUniqueId().toString() + ".inv");
			int xp = plugin.data.getData().getInt("Recoverables." + player.getUniqueId().toString() + ".xp", -1);
			if (data == null) {
				sender.sendMessage("§cYou have no inventory to claim.");
				return true;
			}
			if (xp > 0) {
				Experience.giveExp(player, xp);
			}
			Inventory inv = null;
			try {
				inv = InvSerialization.toInventory(data, new Pane(Type.CLAIM), "Inventory Claim");
			} catch (Exception e1) {
				plugin.warning("Error serializing inventory claim");
				plugin.print(e1);
				sender.sendMessage(plugin.translate("error"));
				return true;
			}
			player.openInventory(inv);
			plugin.data.getData().set("Recoverables." + player.getUniqueId().toString(), null);
			plugin.data.save();
		}
		return true;
	}
}
