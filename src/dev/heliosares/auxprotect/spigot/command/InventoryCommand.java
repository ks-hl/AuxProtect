package dev.heliosares.auxprotect.spigot.command;

import java.io.IOException;
import java.sql.SQLException;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.scheduler.BukkitRunnable;

import dev.heliosares.auxprotect.database.InvDiffManager.DiffInventoryRecord;
import dev.heliosares.auxprotect.spigot.AuxProtectSpigot;
import dev.heliosares.auxprotect.utils.TimeUtil;

public class InventoryCommand implements CommandExecutor {

	private AuxProtectSpigot plugin;

	public InventoryCommand(AuxProtectSpigot plugin, APCommand command) {
		this.plugin = plugin;
	}

	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
		if (args.length != 3) {
			sender.sendMessage(plugin.translate("lookup-invalid-syntax"));
			return true;
		}
		if (sender instanceof Player player) {
			String target = args[1];
			String paramtime = args[2];

			long time_;
			try {
				time_ = TimeUtil.stringToMillis(paramtime);
				if (time_ < 0) {
					sender.sendMessage(plugin.translate("lookup-invalid-parameter"), paramtime);
					return true;
				}
			} catch (NumberFormatException e) {
				sender.sendMessage(String.format(plugin.translate("lookup-invalid-parameter"), paramtime));
				return true;
			}

			if (!paramtime.endsWith("e")) {
				time_ = System.currentTimeMillis() - time_;
			}

			final long time = time_;

			new BukkitRunnable() {

				@Override
				public void run() {
					int uid = plugin.getSqlManager().getUIDFromUsername(target);
					String uuid = plugin.getSqlManager().getUUIDFromUID(uid);
					OfflinePlayer targetP = Bukkit.getOfflinePlayer(UUID.fromString(uuid.substring(1)));
					if (uid < 0) {
						sender.sendMessage(String.format(plugin.translate("lookup-playernotfound"), target));
						return;
					}
					DiffInventoryRecord inv = null;
					try {
						inv = plugin.getSqlManager().getInvDiffManager().getContentsAt(uid, time);
					} catch (ClassNotFoundException | SQLException | IOException e) {
						plugin.print(e);
						sender.sendMessage(plugin.translate("error"));
						return;
					}
					if (inv == null) {
						sender.sendMessage(plugin.translate("lookup-noresults"));
						return;
					}

					Inventory output = InvCommand.makeInventory(player, targetP, inv.inventory(), time);

					sender.sendMessage(String.format("§fDisplaying inventory of §9%s§f from §9%s ago §7(%s)",
							targetP.getName(), TimeUtil.millisToString(System.currentTimeMillis() - time), time + "e"));
					sender.sendMessage(
							String.format("§fBased on inventory from §9%s§f ago §7(%s)§f with §9%s§f differences",
									TimeUtil.millisToString(System.currentTimeMillis() - inv.basetime()),
									inv.basetime() + "e", inv.numdiff()));
					;

					new BukkitRunnable() {
						@Override
						public void run() {
							player.openInventory(output);
						}
					}.runTask(plugin);
				}
			}.runTaskAsynchronously(plugin);
		}
		return true;
	}
}
