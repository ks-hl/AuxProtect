package dev.heliosares.auxprotect.adapters.sender;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import dev.heliosares.auxprotect.velocity.AuxProtectVelocity;
import dev.heliosares.auxprotect.velocity.ColorTranslator;
import net.kyori.adventure.text.Component;

import java.util.UUID;

public class VelocitySenderAdapter extends SenderAdapter<CommandSource, AuxProtectVelocity> implements KauriSender {

    public VelocitySenderAdapter(AuxProtectVelocity plugin, CommandSource sender) {
        super(sender, plugin);
    }

    public void sendMessageRaw(String message) {
        sender.sendMessage(Component.text(ColorTranslator.translateAlternateColorCodes(message)));
    }

    public boolean hasPermission(String node) {
        return sender.hasPermission(node);
    }

    public String getName() {
        return sender instanceof Player player ? player.getUsername() : sender.getClass().getName();
    }

    public UUID getUniqueId() {
        if (sender instanceof Player player) {
            return player.getUniqueId();
        }
        return UUID.fromString("00000000-0000-0000-0000-000000000000");
    }

    @Override
    public void sendMessage(Component message) {
        getSender().sendMessage(message);
    }

    @Override
    public void executeCommand(String command) {
        plugin.getProxy().getCommandManager().executeAsync(sender, command);
    }

    @Override
    public boolean isConsole() {
        return sender.equals(plugin.getProxy().getConsoleCommandSource());
    }

}
