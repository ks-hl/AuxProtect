package dev.heliosares.auxprotect.spigot;

import dev.heliosares.auxprotect.adapters.sender.SpigotSenderAdapter;
import dev.heliosares.auxprotect.core.commands.APCommand;
import dev.heliosares.auxprotect.core.commands.RetentionCommand;
import dev.heliosares.auxprotect.spigot.commands.ActivityCommand;
import dev.heliosares.auxprotect.spigot.commands.InvCommand;
import dev.heliosares.auxprotect.spigot.commands.InventoryCommand;
import dev.heliosares.auxprotect.spigot.commands.MoneyCommand;
import dev.heliosares.auxprotect.spigot.commands.SaveInvCommand;
import dev.heliosares.auxprotect.spigot.commands.TpCommand;
import dev.heliosares.auxprotect.spigot.commands.XrayCommand;
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
        this.apcommand = new APCommand(plugin, plugin.getCommandPrefix()) {
            {
                commands.add(new TpCommand(plugin).setTabComplete(false));
                commands.add(new InvCommand(plugin).setTabComplete(false));
                commands.add(new InventoryCommand(APSCommand.this.plugin));
                commands.add(new ActivityCommand(plugin));
                commands.add(new MoneyCommand(plugin));
                commands.add(new SaveInvCommand(plugin));
                if (plugin.getAPConfig().isPrivate()) {
                    commands.add(new RetentionCommand(plugin));
                    commands.add(new XrayCommand(APSCommand.this.plugin));
                }
            }
        };
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
