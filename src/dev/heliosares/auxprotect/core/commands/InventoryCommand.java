package dev.heliosares.auxprotect.core.commands;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.scheduler.BukkitRunnable;

import dev.heliosares.auxprotect.adapters.SenderAdapter;
import dev.heliosares.auxprotect.core.APPermission;
import dev.heliosares.auxprotect.core.Command;
import dev.heliosares.auxprotect.core.CommandException;
import dev.heliosares.auxprotect.core.IAuxProtect;
import dev.heliosares.auxprotect.core.PlatformType;
import dev.heliosares.auxprotect.database.InvDiffManager.DiffInventoryRecord;
import dev.heliosares.auxprotect.spigot.AuxProtectSpigot;
import dev.heliosares.auxprotect.utils.TimeUtil;

public class InventoryCommand extends Command {

	public InventoryCommand(IAuxProtect plugin) {
		super(plugin, "inventory", APPermission.INV);
	}

	@Override
	public void onCommand(SenderAdapter sender, String label, String[] args) throws CommandException {
		if (args.length != 3) {
			throw new CommandException.SyntaxException();
		}
		if (sender.getPlatform() != PlatformType.SPIGOT) {
			throw new CommandException.PlatformException();
		}
		if (sender.getSender() instanceof Player player && plugin instanceof AuxProtectSpigot spigot) {
			String target = args[1];
			String paramtime = args[2];

			long time_;
			try {
				time_ = TimeUtil.stringToMillis(paramtime);
				if (time_ < 0) {
					sender.sendLang("lookup-invalid-parameter", paramtime);
					return;
				}
			} catch (NumberFormatException e) {
				sender.sendLang("lookup-invalid-parameter", paramtime);
				return;
			}

			if (!paramtime.endsWith("e")) {
				time_ = System.currentTimeMillis() - time_;
			}

			final long time = time_;

			plugin.runAsync(() -> {
				int uid = plugin.getSqlManager().getUIDFromUsername(target);
				String uuid = plugin.getSqlManager().getUUIDFromUID(uid);
				OfflinePlayer targetP = Bukkit.getOfflinePlayer(UUID.fromString(uuid.substring(1)));
				if (uid < 0) {
					sender.sendLang("lookup-playernotfound", target);
					return;
				}
				DiffInventoryRecord inv = null;
				try {
					inv = plugin.getSqlManager().getInvDiffManager().getContentsAt(uid, time);
				} catch (ClassNotFoundException | SQLException | IOException e) {
					plugin.print(e);
					sender.sendLang("error");
					return;
				}
				if (inv == null) {
					sender.sendLang("lookup-noresults");
					return;
				}

				Inventory output = InvCommand.makeInventory(plugin, player, targetP, inv.inventory(), time);

				sender.sendMessageRaw(String.format("§fDisplaying inventory of §9%s§f from §9%s ago §7(%s)",
						targetP.getName(), TimeUtil.millisToString(System.currentTimeMillis() - time), time + "e"));
				sender.sendMessageRaw(
						String.format("§fBased on inventory from §9%s§f ago §7(%s)§f with §9%s§f differences",
								TimeUtil.millisToString(System.currentTimeMillis() - inv.basetime()),
								inv.basetime() + "e", inv.numdiff()));
				;

				new BukkitRunnable() {
					@Override
					public void run() {
						player.openInventory(output);
					}
				}.runTask(spigot);
			});
		} else {
			throw new CommandException.PlatformException();
		}
	}

	@Override
	public boolean exists() {
		return plugin.getPlatform() == PlatformType.SPIGOT;
	}

	@Override
	public List<String> onTabComplete(SenderAdapter sender, String label, String[] args) {
		return APCommand.tabCompletePlayerAndTime(plugin, sender, label, args);
	}
}
