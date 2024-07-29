package dev.heliosares.auxprotect.core;

public enum PlatformType {
    SPIGOT, BUNGEE, VELOCITY, NONE;

    public enum Level {
        PROXY, SERVER
    }

    public Level getLevel() {
        return switch (this) {
            case SPIGOT -> Level.SERVER;
            case BUNGEE, VELOCITY -> Level.PROXY;
            default -> null;
        };
    }
}
