package dev.heliosares.auxprotect.core.commands;

import java.util.List;
import dev.heliosares.auxprotect.adapters.SenderAdapter;
import dev.heliosares.auxprotect.core.APPermission;
import dev.heliosares.auxprotect.core.Command;
import dev.heliosares.auxprotect.core.CommandException;
import dev.heliosares.auxprotect.core.IAuxProtect;
import dev.heliosares.auxprotect.core.PlatformType;

public class MoneyCommand extends Command {

	public MoneyCommand(IAuxProtect plugin) {
		super(plugin, "money", APPermission.LOOKUP_MONEY);
	}

	@Override
	public void onCommand(SenderAdapter sender, String label, String[] args) throws CommandException {
		if (args.length != 2 && args.length != 3) {
			throw new CommandException.SyntaxException();
		}
		String time = "2w";
		if (args.length == 3) {
			time = args[2];
		}
		sender.executeCommand(String.format(plugin.getCommandPrefix() + " lookup #money user:%s time:%s action:money",
				args[1], time));
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
