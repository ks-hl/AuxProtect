package dev.heliosares.auxprotect.core.commands;

import dev.heliosares.auxprotect.adapters.sender.SenderAdapter;
import dev.heliosares.auxprotect.core.APPermission;
import dev.heliosares.auxprotect.core.Command;
import dev.heliosares.auxprotect.core.IAuxProtect;
import dev.heliosares.auxprotect.core.Language;
import dev.heliosares.auxprotect.core.Language.L;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

public class HelpCommand extends Command {
    private final List<Command> commands;

    public HelpCommand(IAuxProtect plugin, List<Command> commands) {
        super(plugin, "help", APPermission.HELP, false);
        this.commands = commands;
    }

    @Override
    public void onCommand(SenderAdapter sender, String label, String[] args) {
        Command helpOn;
        if (args.length < 2) {
            helpOn = this;
        } else {
            String arg = args[1].toLowerCase();
            try {
                helpOn = commands.stream().filter(c -> c.matches(arg)).findAny().orElseThrow();
            } catch (NoSuchElementException e) {
                sender.sendLang(L.UNKNOWN_SUBCOMMAND);
                return;
            }
        }
        if (!helpOn.hasPermission(sender)) {
            sender.sendLang(L.NO_PERMISSION);
            return;
        }
        List<String> help = getHelpFor(helpOn.getLabel());
        if (help == null) {
            sender.sendMessageRaw(Language.L.COMMAND__HELP.translateSubcategory("nohelp"));
        } else {
            sender.sendLang(L.COMMAND__HELP__HEADER, helpOn.getLabel());
            for (String str : help) {
                sender.sendMessageRaw(str);
            }
        }
    }

    public static List<String> getHelpFor(String command) {
        return Language.L.COMMAND__HELP.translateSubcategoryList(command);
    }

    @Override
    public List<String> onTabComplete(SenderAdapter sender, String label, String[] args) {
        return commands.stream().filter(
                        c -> c.hasPermission(sender) && Language.L.COMMAND__HELP.translateSubcategoryList(c.getLabel()) != null)
                .map(c -> c.getLabel().toLowerCase()).collect(Collectors.toList());
    }

    @Override
    public boolean exists() {
        return true;
    }

}
