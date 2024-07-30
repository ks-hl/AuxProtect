package dev.heliosares.auxprotect.bungee;

import dev.heliosares.auxprotect.adapters.sender.BungeeSenderAdapter;
import dev.heliosares.auxprotect.core.APPlayer;
import jakarta.annotation.Nullable;
import net.md_5.bungee.api.connection.ProxiedPlayer;

import java.net.InetSocketAddress;

public class APPlayerBungee extends APPlayer<ProxiedPlayer> {
    public APPlayerBungee(AuxProtectBungee plugin, ProxiedPlayer player) {
        super(plugin, player);
    }

    public String getName() {
        return player.getName();
    }

    @Nullable
    public String getIPAddress() {
        String ip_ = ((InetSocketAddress) player.getSocketAddress()).getAddress().toString();
        if (ip_.startsWith("/")) ip_ = ip_.substring(1);
        return ip_;
    }

    @Override
    public BungeeSenderAdapter getSenderAdapter() {
        return new BungeeSenderAdapter(getPlugin(), getPlayer());
    }

    @Override
    protected AuxProtectBungee getPlugin() {
        return (AuxProtectBungee) plugin;
    }
}
