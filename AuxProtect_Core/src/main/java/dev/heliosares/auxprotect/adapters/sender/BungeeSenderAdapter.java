package dev.heliosares.auxprotect.adapters.sender;

import dev.heliosares.auxprotect.bungee.AuxProtectBungee;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.connection.ProxiedPlayer;

import java.util.UUID;

public class BungeeSenderAdapter extends SenderAdapter<CommandSender, AuxProtectBungee> {

    public BungeeSenderAdapter(AuxProtectBungee plugin, CommandSender sender) {
        super(sender, plugin);
    }

    public void sendMessageRaw_(String message) {
        sender.sendMessage(TextComponent.fromLegacyText(message));
    }

    public boolean hasPermission(String node) {
        return sender.hasPermission(node);
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
    public void executeCommand(String command) {
        plugin.getProxy().getPluginManager().dispatchCommand(sender, command);
    }

    @Override
    public boolean isConsole() {
        return sender.equals(plugin.getProxy().getConsole());
    }
}
