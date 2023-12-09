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
}
