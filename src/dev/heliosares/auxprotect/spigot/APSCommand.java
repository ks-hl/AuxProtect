package dev.heliosares.auxprotect.spigot;

import dev.heliosares.auxprotect.adapters.SpigotSenderAdapter;
import dev.heliosares.auxprotect.core.commands.APCommand;
import dev.heliosares.auxprotect.exceptions.CommandException;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;

import java.util.List;

public class APSCommand implements CommandExecutor, TabExecutor {

    private final AuxProtectSpigot plugin;
    private final APCommand apcommand;

    public APSCommand(AuxProtectSpigot plugin) {
        this.plugin = plugin;
        this.apcommand = new APCommand(plugin, plugin.getCommandPrefix());
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        apcommand.onCommand(new SpigotSenderAdapter(plugin, sender), label, args);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        return apcommand.onTabComplete(new SpigotSenderAdapter(plugin, sender), label, args);
    }

}
