package dev.heliosares.auxprotect.core.commands;

import dev.heliosares.auxprotect.adapters.sender.SenderAdapter;
import dev.heliosares.auxprotect.core.APPermission;
import dev.heliosares.auxprotect.core.Command;
import dev.heliosares.auxprotect.core.IAuxProtect;
import dev.heliosares.auxprotect.core.PlatformType;
import dev.heliosares.auxprotect.exceptions.CommandException;
import dev.heliosares.auxprotect.exceptions.SyntaxException;

import java.util.ArrayList;
import java.util.List;

public class RetentionCommand extends Command {

    public RetentionCommand(IAuxProtect plugin) {
        super(plugin, "retention", APPermission.LOOKUP_RETENTION, false);
    }

    @Override
    public void onCommand(SenderAdapter sender, String label, String[] args) throws CommandException {
        if (args.length != 2) {
            throw new SyntaxException();
        }
        sender.executeCommand(String.format("ap lookup #retention time:%s action:username", args[1]));
    }

    @Override
    public boolean exists() {
        return plugin.getPlatform() .getLevel() == PlatformType.Level.SERVER && plugin.getAPConfig().isPrivate();
    }

    @Override
    public List<String> onTabComplete(SenderAdapter sender, String label, String[] args) {
        String currentArg = args[args.length - 1];
        if (args.length == 2 && currentArg.matches("\\d+")) {
            List<String> out = new ArrayList<>();
            out.add(currentArg + "ms");
            out.add(currentArg + "s");
            out.add(currentArg + "m");
            out.add(currentArg + "h");
            out.add(currentArg + "d");
            out.add(currentArg + "w");
            return out;
        }
        return null;

    }
}
