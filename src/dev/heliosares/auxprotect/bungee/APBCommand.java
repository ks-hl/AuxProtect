package dev.heliosares.auxprotect.bungee;

import dev.heliosares.auxprotect.adapters.BungeeSenderAdapter;
import dev.heliosares.auxprotect.core.commands.APCommand;
import dev.heliosares.auxprotect.exceptions.CommandException;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.plugin.Command;
import net.md_5.bungee.api.plugin.TabExecutor;

public class APBCommand extends Command implements TabExecutor {

    private final AuxProtectBungee plugin;
    private final APCommand apcommand;
    private final String label;

    public APBCommand(AuxProtectBungee plugin, String label) {
        super(label);
        this.plugin = plugin;
        this.label = label;
        this.apcommand = new APCommand(plugin, label);
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        apcommand.onCommand(new BungeeSenderAdapter(plugin, sender), label, args);
    }

    @Override
    public Iterable<String> onTabComplete(CommandSender sender, String[] args) {
        return apcommand.onTabComplete(new BungeeSenderAdapter(plugin, sender), label, args);
    }
}
