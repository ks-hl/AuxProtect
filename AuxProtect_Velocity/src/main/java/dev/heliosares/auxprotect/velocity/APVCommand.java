package dev.heliosares.auxprotect.velocity;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import dev.heliosares.auxprotect.adapters.sender.VelocitySenderAdapter;
import dev.heliosares.auxprotect.core.commands.APCommand;

import java.util.List;

public class APVCommand implements SimpleCommand {
    private final AuxProtectVelocity plugin;
    private final APCommand<CommandSource, AuxProtectVelocity, VelocitySenderAdapter> apcommand;

    public APVCommand(AuxProtectVelocity plugin, String label, String... aliases) {
        this.plugin = plugin;
        this.apcommand = new APCommand<>(plugin, label, aliases);
    }

    @Override
    public void execute(Invocation invocation) {
        apcommand.onCommand(new VelocitySenderAdapter(plugin, invocation.source()), invocation.alias(), invocation.arguments());
    }

    @Override
    public List<String> suggest(Invocation invocation) {
        String[] args = invocation.arguments().length == 0 ? new String[]{""} : invocation.arguments();
        return apcommand.onTabComplete(new VelocitySenderAdapter(plugin, invocation.source()), invocation.alias(), args);
    }
}
