package dev.heliosares.auxprotect.core.commands;

import java.util.List;

import dev.heliosares.auxprotect.adapters.SenderAdapter;
import dev.heliosares.auxprotect.core.APPermission;
import dev.heliosares.auxprotect.core.Command;
import dev.heliosares.auxprotect.core.CommandException;
import dev.heliosares.auxprotect.core.IAuxProtect;

public class HelpCommand extends Command {
	// TODO
	@SuppressWarnings("unused")
	private final List<Command> commands;

	public HelpCommand(IAuxProtect plugin, List<Command> commands) {
		super(plugin, "help", APPermission.HELP);
		this.commands = commands;
	}

	@Override
	public void onCommand(SenderAdapter sender, String label, String[] args) throws CommandException {
		if (args.length < 2) {
			sender.sendLang("command-help-1");
			sender.sendLang("command-help-2");
			sender.sendLang("command-help-3");
			sender.sendLang("command-help-4");
		} else if (args[1].equalsIgnoreCase("lookup")) {
			// TODO generalize
			sender.sendLang("command-help-lookup-1");
			sender.sendLang("command-help-lookup-2");
			sender.sendLang("command-help-lookup-3");
			sender.sendLang("command-help-lookup-4");
			sender.sendLang("command-help-lookup-5");
			sender.sendLang("command-help-lookup-6");
			sender.sendLang("command-help-lookup-7");
		} else if (args[1].equalsIgnoreCase("purge")) {
			sender.sendLang("command-help-purge-1");
			sender.sendLang("command-help-purge-2");
		} else {
			sender.sendLang("command-help-unknown-subcommand");
		}
	}

	@Override
	public List<String> onTabComplete(SenderAdapter sender, String label, String[] args) {
		return null;
	}

	@Override
	public boolean exists() {
		return true;
	}

}
