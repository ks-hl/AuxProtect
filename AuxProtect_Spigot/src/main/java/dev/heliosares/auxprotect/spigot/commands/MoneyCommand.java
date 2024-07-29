package dev.heliosares.auxprotect.spigot.commands;

import dev.heliosares.auxprotect.adapters.sender.SenderAdapter;
import dev.heliosares.auxprotect.core.APPermission;
import dev.heliosares.auxprotect.core.Command;
import dev.heliosares.auxprotect.core.IAuxProtect;
import dev.heliosares.auxprotect.core.PlatformType;
import dev.heliosares.auxprotect.core.commands.APCommand;
import dev.heliosares.auxprotect.exceptions.CommandException;
import dev.heliosares.auxprotect.exceptions.SyntaxException;

import java.util.List;

public class MoneyCommand<S, P extends IAuxProtect, SA extends SenderAdapter<S, P>> extends Command<S, P, SA> {

    public MoneyCommand(P plugin) {
        super(plugin, "money", APPermission.LOOKUP_MONEY, false);
    }

    @Override
    public void onCommand(SA sender, String label, String[] args) throws CommandException {
        if (args.length != 2 && args.length != 3) {
            throw new SyntaxException();
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
        return plugin.getPlatform().getLevel() == PlatformType.Level.SERVER;
    }

    @Override
    public List<String> onTabComplete(SA sender, String label, String[] args) {
        return APCommand.tabCompletePlayerAndTime(plugin, sender, args);
    }
}
