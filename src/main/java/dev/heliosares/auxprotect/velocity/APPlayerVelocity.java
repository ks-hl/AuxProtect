package dev.heliosares.auxprotect.velocity;

import dev.heliosares.auxprotect.core.APPlayer;
import dev.heliosares.auxprotect.core.IAuxProtect;

import javax.annotation.Nullable;
import java.net.InetSocketAddress;

public class APPlayerVelocity extends APPlayer<ProxiedPlayer> {
    public APPlayerVelocity(IAuxProtect plugin, ProxiedPlayer player) {
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
