package dev.heliosares.auxprotect.adapters.sender;

import dev.heliosares.auxprotect.bungee.AuxProtectBungee;
import dev.heliosares.auxprotect.core.PlatformType;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.connection.ProxiedPlayer;

import java.util.UUID;

public class BungeeSenderAdapter extends SenderAdapter {

    private final CommandSender sender;
    private final AuxProtectBungee plugin;

    public BungeeSenderAdapter(AuxProtectBungee plugin, CommandSender sender) {
        this.sender = sender;
        this.plugin = plugin;
    }

    public void sendMessageRaw(String message) {
        sender.sendMessage(TextComponent.fromLegacyText(ChatColor.translateAlternateColorCodes('&', message)));
    }

    public void sendMessage(BaseComponent... message) {
        sender.sendMessage(message);
    }

    public boolean hasPermission(String node) {
        return sender.hasPermission(node);
    }

    @Override
    public void executeCommand(String command) {
        plugin.getProxy().getPluginManager().dispatchCommand(sender, command);
    }

    @Override
    public void teleport(String world, double x, double y, double z, int pitch, int yaw)
            throws NullPointerException, UnsupportedOperationException {
        throw new UnsupportedOperationException();
    }

    public String getName() {
        return sender.getName();
    }

    public UUID getUniqueId() {
        if (sender instanceof ProxiedPlayer player) {
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
    public boolean isConsole() {
        return sender.equals(plugin.getProxy().getConsole());
    }
}
