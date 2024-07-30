package dev.heliosares.auxprotect.bungee;

import dev.heliosares.auxprotect.core.APPlayer;
import dev.heliosares.auxprotect.core.IAuxProtect;
import net.md_5.bungee.api.connection.ProxiedPlayer;

import jakarta.annotation.Nullable;
import java.net.InetSocketAddress;

public class APPlayerBungee extends APPlayer<ProxiedPlayer> {
    public APPlayerBungee(IAuxProtect plugin, ProxiedPlayer player) {
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
}
