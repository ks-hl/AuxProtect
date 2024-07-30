package dev.heliosares.auxprotect.adapters.sender;

import dev.heliosares.auxprotect.adapters.location.LocationAdapter;
import dev.heliosares.auxprotect.exceptions.NotPlayerException;

public interface PositionedSender {
    void teleport(String world, double x, double y, double z, int pitch, int yaw) throws NullPointerException, UnsupportedOperationException;

    LocationAdapter getLocation() throws NotPlayerException;
}
