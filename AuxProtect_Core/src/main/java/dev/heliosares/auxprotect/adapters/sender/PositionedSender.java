package dev.heliosares.auxprotect.adapters.sender;

import dev.heliosares.auxprotect.exceptions.NotPlayerException;

public interface PositionedSender {
    void teleport(String world, double x, double y, double z, int pitch, int yaw) throws NullPointerException, UnsupportedOperationException;

    int getBlockX() throws NotPlayerException;

    int getBlockY() throws NotPlayerException;

    int getBlockZ() throws NotPlayerException;

    double getX() throws NotPlayerException;

    double getY() throws NotPlayerException;

    double getZ() throws NotPlayerException;

    String getWorldName() throws NotPlayerException;
}
