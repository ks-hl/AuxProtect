package dev.heliosares.auxprotect.core.commands;

import dev.heliosares.auxprotect.adapters.SenderAdapter;
import dev.heliosares.auxprotect.core.APPermission;
import dev.heliosares.auxprotect.core.Command;
import dev.heliosares.auxprotect.core.IAuxProtect;
import dev.heliosares.auxprotect.core.PlatformType;
import dev.heliosares.auxprotect.exceptions.CommandException;
import dev.heliosares.auxprotect.exceptions.NotPlayerException;
import dev.heliosares.auxprotect.exceptions.PlatformException;
import dev.heliosares.auxprotect.exceptions.SyntaxException;

import java.util.List;

public class TpCommand extends Command {

    public TpCommand(IAuxProtect plugin) {
        super(plugin, "tp", APPermission.TP);
    }

    @Override
    public void onCommand(SenderAdapter sender, String label, String[] args) throws CommandException {
        if (args.length < 5) {
            throw new SyntaxException();
        }
        if (sender.getPlatform() != PlatformType.SPIGOT) {
            throw new PlatformException();
        }
        try {
            int x = Integer.parseInt(args[1]);
            int y = Integer.parseInt(args[2]);
            int z = Integer.parseInt(args[3]);
            int pitch = 0, yaw = 180;
            if (args.length == 7) {
                pitch = Integer.parseInt(args[5]);
                yaw = Integer.parseInt(args[6]);
            }
            sender.teleport(args[4], x, y, z, pitch, yaw);
        } catch (NumberFormatException | NullPointerException e) {
            throw new SyntaxException();
        } catch (UnsupportedOperationException e) {
            throw new NotPlayerException();
        }
    }

    @Override
    public boolean exists() {
        return plugin.getPlatform() == PlatformType.SPIGOT;
    }

    @Override
    public List<String> onTabComplete(SenderAdapter sender, String label, String[] args) {
        return null;
    }
}
