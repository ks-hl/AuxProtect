package dev.heliosares.auxprotect.adapters.sender;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import dev.heliosares.auxprotect.bungee.AuxProtectBungee;
import dev.heliosares.auxprotect.core.PlatformType;
import dev.heliosares.auxprotect.velocity.AuxProtectVelocity;
import net.kyori.adventure.text.Component;

import java.util.UUID;

public class VelocitySenderAdapter extends SenderAdapter {

    private final CommandSource sender;
    private final AuxProtectVelocity plugin;

    public VelocitySenderAdapter(AuxProtectVelocity plugin, CommandSource sender) {
        this.sender = sender;
        this.plugin = plugin;
    }

    public void sendMessageRaw(String message) {
        sender.sendMessage(Component.text(ChatColor.translateAlternateColorCodes('&', message)));
    }

    public void sendMessage(BaseComponent... message) {
        sender.sendMessage(message);
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
    public Object getSender() {
        return sender;
    }

    @Override
    public PlatformType getPlatform() {
        return PlatformType.BUNGEE;
    }

    @Override
    public void executeCommand(String command) {
        plugin.getProxy().getCommandManager().executeAsync(sender, command);
    }

    @Override
    public boolean isConsole() {
        return sender.equals(plugin.getProxy().getConsoleCommandSource());
    }

    @Override
    public void teleport(String world, double x, double y, double z, int pitch, int yaw)
            throws NullPointerException, UnsupportedOperationException {
        throw new UnsupportedOperationException();
    }
}
