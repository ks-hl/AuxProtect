package dev.heliosares.auxprotect.velocity;

import dev.heliosares.auxprotect.adapters.sender.BungeeSenderAdapter;
import dev.heliosares.auxprotect.core.commands.APCommand;

public class APVCommand extends Command implements TabExecutor {

    private final AuxProtectVelocity plugin;
    private final APCommand apcommand;
    private final String label;

    public APVCommand(AuxProtectVelocity plugin, String label) {
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
