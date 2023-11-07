package dev.heliosares.auxprotect.core;

import dev.heliosares.auxprotect.api.AuxProtectAPI;
import dev.heliosares.auxprotect.utils.IPService;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.util.TimeZone;

public abstract class APPlayer<T> {
    protected final IAuxProtect plugin;

    protected final T player;

    private TimeZone timeZone;

    public APPlayer(IAuxProtect plugin, T player) {
        this.player = player;
        this.plugin = plugin;
    }

    public T getPlayer() {
        return player;
    }

    @Nonnull
    public TimeZone getTimeZone() {
        if (timeZone != null) return timeZone;

        if (getIPAddress() != null) try {
            timeZone = IPService.getTimeZoneForIP(getIPAddress());
            if (timeZone != null) return timeZone;
        } catch (IOException ex) {
            AuxProtectAPI.getInstance().warning("Failed to get timezone for " + getName() + ", " + ex.getMessage());
            if (AuxProtectAPI.getInstance().getAPConfig().getDebug() > 0) AuxProtectAPI.getInstance().print(ex);
        }

        return timeZone = TimeZone.getDefault();
    }

    public abstract String getName();

    @Nullable
    public abstract String getIPAddress();

}