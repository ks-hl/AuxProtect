package dev.heliosares.auxprotect.velocity;

import com.velocitypowered.api.proxy.Player;
import dev.heliosares.auxprotect.core.APPlayer;
import dev.heliosares.auxprotect.core.IAuxProtect;

import javax.annotation.Nullable;

public class APPlayerVelocity extends APPlayer<Player> {
    public APPlayerVelocity(IAuxProtect plugin, Player player) {
        super(plugin, player);
    }

    public String getName() {
        return player.getUsername();
    }

    @Nullable
    public String getIPAddress() {
        String ip_ = player.getRemoteAddress().getAddress().toString();
        if (ip_.startsWith("/")) ip_ = ip_.substring(1);
        return ip_;
    }
}
