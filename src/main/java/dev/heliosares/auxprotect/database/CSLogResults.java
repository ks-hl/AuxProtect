package dev.heliosares.auxprotect.database;

import dev.heliosares.auxprotect.adapters.sender.SenderAdapter;
import dev.heliosares.auxprotect.core.IAuxProtect;
import dev.heliosares.auxprotect.core.Parameters;

import java.util.List;

public class CSLogResults extends Results {
    public CSLogResults(IAuxProtect plugin, List<DbEntry> entries, SenderAdapter player, Parameters params) {
        super(plugin, entries, player, params);
    }

    @Override
    public String getCommandPrefix() {
        return "/cslogs ";
    }

    @Override
    protected String getCommand(int which) {
        return switch (which) {
            case -2 -> "first";
            case -1 -> "prev";
            case 1 -> "next";
            case 2 -> "last";
            default -> throw new IllegalArgumentException();
        };
    }

    @Override
    public void sendHeader() {
        String headerColor = "&7";
        StringBuilder line = new StringBuilder("&m");
        line.append(String.valueOf((char) 65293).repeat(6));
        line.append("&7");
        player.sendMessageRaw(headerColor + line + "  &9ChestShop Logs&7  " + line);
    }
}
