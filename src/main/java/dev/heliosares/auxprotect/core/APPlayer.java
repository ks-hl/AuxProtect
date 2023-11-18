package dev.heliosares.auxprotect.core;

import dev.heliosares.auxprotect.utils.IPService;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
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
        if (timeZone == null) timeZone = IPService.getTimeZoneForIP(getIPAddress());

        return timeZone;
    }

    public abstract String getName();

    @Nullable
    public abstract String getIPAddress();

}