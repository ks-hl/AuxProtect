package dev.heliosares.auxprotect.core.commands;

import java.util.List;
import java.util.stream.Collectors;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import dev.heliosares.auxprotect.adapters.SenderAdapter;
import dev.heliosares.auxprotect.core.APPermission;
import dev.heliosares.auxprotect.core.APPlayer;
import dev.heliosares.auxprotect.core.Command;
import dev.heliosares.auxprotect.core.CommandException;
import dev.heliosares.auxprotect.core.IAuxProtect;
import dev.heliosares.auxprotect.core.PlatformType;
import dev.heliosares.auxprotect.spigot.AuxProtectSpigot;

public class SaveInvCommand extends Command {

	public SaveInvCommand(IAuxProtect plugin) {
		super(plugin, "saveinv", APPermission.INV_SAVE);
	}

	@Override
	public void onCommand(SenderAdapter sender, String label, String[] args) throws CommandException {
		if (args.length != 2) {
			throw new CommandException.SyntaxException();
		}
		Player target = Bukkit.getPlayer(args[1]);
		APPlayer apTarget = null;
		if (target != null) {
			apTarget = plugin.getAPPlayer(sender);
		}
		if (apTarget == null) {
			sender.sendLang("lookup-playernotfound", args[1]);
			return;
		}
		if (!APPermission.ADMIN.hasPermission(sender)
				&& System.currentTimeMillis() - apTarget.lastLoggedInventory < 10000L) {
			sender.sendLang("inv-toosoon");
			return;
		}
		long time = apTarget.logInventory("manual");
		sender.sendLang("inv-manual-success", target.getName(), target.getName().endsWith("s") ? "" : "s", time + "e");
	}

	@Override
	public boolean exists() {
		return plugin.getPlatform() == PlatformType.SPIGOT;
	}

	@Override
	public List<String> onTabComplete(SenderAdapter sender, String label, String[] args) {
		if (args.length == 2 && plugin instanceof AuxProtectSpigot spigot) {
			return spigot.getServer().getOnlinePlayers().stream().map((p) -> p.getName()).collect(Collectors.toList());
		} 
		return null;
	}

}
