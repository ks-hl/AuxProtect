package dev.heliosares.auxprotect.adapters.sender;

public interface PositionedSender {
    void teleport(String world, double x, double y, double z, int pitch, int yaw) throws NullPointerException, UnsupportedOperationException;
}
