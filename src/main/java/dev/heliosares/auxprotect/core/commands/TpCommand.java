package dev.heliosares.auxprotect.core.commands;

import dev.heliosares.auxprotect.adapters.sender.PositionedSender;
import dev.heliosares.auxprotect.adapters.sender.SenderAdapter;
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
        super(plugin, "tp", APPermission.TP, false);
    }

    @Override
    public void onCommand(SenderAdapter sender, String label, String[] args) throws CommandException {
        if (args.length < 5) {
            throw new SyntaxException();
        }
        if (!(sender instanceof PositionedSender positionedSender)) {
            throw new PlatformException();
        }
        try {
            double x = Double.parseDouble(args[1].replace(',', '.'));
            double y = Double.parseDouble(args[2].replace(',', '.'));
            double z = Double.parseDouble(args[3].replace(',', '.'));
            int pitch = 0, yaw = 180;
            if (args.length == 7) {
                pitch = Integer.parseInt(args[5]);
                yaw = Integer.parseInt(args[6]);
            }
            positionedSender.teleport(args[4], x, y, z, pitch, yaw);
        } catch (NumberFormatException | NullPointerException e) {
            throw new SyntaxException();
        } catch (UnsupportedOperationException e) {
            throw new NotPlayerException();
        }
    }

    @Override
    public boolean exists() {
        return plugin.getPlatform().getLevel() == PlatformType.Level.SERVER;
    }

    @Override
    public List<String> onTabComplete(SenderAdapter sender, String label, String[] args) {
        return null;
    }
}
