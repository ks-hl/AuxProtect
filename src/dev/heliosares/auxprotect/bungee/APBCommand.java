package dev.heliosares.auxprotect.bungee;

import dev.heliosares.auxprotect.adapters.BungeeSenderAdapter;
import dev.heliosares.auxprotect.core.CommandException;
import dev.heliosares.auxprotect.core.commands.APCommand;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.plugin.Command;
import net.md_5.bungee.api.plugin.TabExecutor;

public class APBCommand extends Command implements TabExecutor {

	private final AuxProtectBungee plugin;
	private final APCommand apcommand;

	public APBCommand(AuxProtectBungee plugin) {
		super(plugin.getCommandPrefix());
		this.plugin = plugin;
		this.apcommand = new APCommand(plugin, plugin.getCommandPrefix());
	}

	@Override
	public void execute(CommandSender sender, String[] args) {
		try {
			apcommand.onCommand(new BungeeSenderAdapter(plugin, sender), plugin.getCommandPrefix(), args);
		} catch (CommandException ignored) {
		}
	}

	@Override
	public Iterable<String> onTabComplete(CommandSender sender, String[] args) {
		return apcommand.onTabComplete(new BungeeSenderAdapter(plugin, sender), plugin.getCommandPrefix(), args);
	}
}
