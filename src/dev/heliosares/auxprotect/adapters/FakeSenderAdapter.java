package dev.heliosares.auxprotect.adapters;

import dev.heliosares.auxprotect.core.PlatformType;
import net.md_5.bungee.api.chat.BaseComponent;

import java.util.UUID;

public class FakeSenderAdapter extends SenderAdapter {

    private final String name;
    private final UUID uuid;
    private final PlatformType platform;
    private final boolean permissions;
    private String console;

    public FakeSenderAdapter(String name, UUID uuid, boolean permissions, PlatformType platform) {
        this.name = name;
        this.uuid = uuid;
        this.permissions = permissions;
        this.platform = platform;
    }

    @Override
    public Object getSender() {
        return null;
    }

    @Override
    public String getName() {
        if (name == null)
            return "null";
        return name;
    }

    @Override
    public UUID getUniqueId() {
        if (uuid == null)
            return UUID.fromString("00000000-0000-0000-0000-000000000001");
        return uuid;
    }

    @Override
    public PlatformType getPlatform() {
        return platform == null ? PlatformType.NONE : platform;
    }

    @Override
    public void sendMessage(BaseComponent... message) {
        StringBuilder line = new StringBuilder();
        for (BaseComponent part : message) {
            line.append(part);
        }
        console += line + "\n";
    }

    @Override
    public void sendMessageRaw(String message) {
        console += message + "\n";
    }

    @Override
    public boolean hasPermission(String node) {
        return permissions;
    }

    @Override
    public void executeCommand(String command) {
        sendMessageRaw("**Executing " + command);
    }

    @Override
    public boolean isConsole() {
        return false;
    }

    public String getConsoleLog() {
        return console;
    }

    @Override
    public void teleport(String world, int x, int y, int z, int pitch, int yaw)
            throws NullPointerException, UnsupportedOperationException {
    }

}
