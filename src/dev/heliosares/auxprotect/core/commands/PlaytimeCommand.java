package dev.heliosares.auxprotect.core.commands;

import java.util.List;

import dev.heliosares.auxprotect.adapters.SenderAdapter;
import dev.heliosares.auxprotect.core.APPermission;
import dev.heliosares.auxprotect.core.Command;
import dev.heliosares.auxprotect.core.CommandException;
import dev.heliosares.auxprotect.core.IAuxProtect;
import dev.heliosares.auxprotect.core.PlatformType;

public class PlaytimeCommand extends Command {

	public PlaytimeCommand(IAuxProtect plugin) {
		super(plugin, "playtime", APPermission.LOOKUP_PLAYTIME, "pt");
	}

	@Override
	public void onCommand(SenderAdapter sender, String label, String[] args) throws CommandException {
		if (args.length != 2 && args.length != 3) {
			throw new CommandException.SyntaxException();
		}
		String cmd = String.format(plugin.getCommandPrefix() + " lookup #pt user:%s action:session time:", args[1]);
		if (args.length > 2) {
			cmd += args[2];
		} else {
			cmd += "2w";
		}
		sender.executeCommand(cmd);
	}

	@Override
	public boolean exists() {
		// TODO zz bungee?
		return plugin.getPlatform() == PlatformType.SPIGOT;
	}

	@Override
	public List<String> onTabComplete(SenderAdapter sender, String label, String[] args) {
		return APCommand.tabCompletePlayerAndTime(plugin, sender, label, args);
	}
}
