package dev.heliosares.auxprotect.spigot;

import dev.heliosares.auxprotect.adapters.sender.SpigotSenderAdapter;
import dev.heliosares.auxprotect.core.commands.APCommand;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;

import javax.annotation.Nonnull;
import java.util.List;

public class APSCommand implements CommandExecutor, TabExecutor {

    private final AuxProtectSpigot plugin;
    private final APCommand apcommand;

    public APSCommand(AuxProtectSpigot plugin) {
        this.plugin = plugin;
        this.apcommand = new APCommand(plugin, plugin.getCommandPrefix());
    }

    @Override
    public boolean onCommand(@Nonnull CommandSender sender, @Nonnull Command command, @Nonnull String label, @Nonnull String[] args) {
        apcommand.onCommand(new SpigotSenderAdapter(plugin, sender), label, args);
        return true;
    }

    @Override
    public List<String> onTabComplete(@Nonnull CommandSender sender, @Nonnull Command command, @Nonnull String label, @Nonnull String[] args) {
        return apcommand.onTabComplete(new SpigotSenderAdapter(plugin, sender), label, args);
    }

}
