package dev.heliosares.auxprotect.core.commands;

import dev.heliosares.auxprotect.adapters.sender.SenderAdapter;
import dev.heliosares.auxprotect.core.APPermission;
import dev.heliosares.auxprotect.core.Command;
import dev.heliosares.auxprotect.core.IAuxProtect;
import dev.heliosares.auxprotect.core.Language;
import dev.heliosares.auxprotect.exceptions.CommandException;
import dev.heliosares.auxprotect.exceptions.SyntaxException;
import dev.heliosares.auxprotect.utils.TimeUtil;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class TimeCommand extends Command {

    public TimeCommand(IAuxProtect plugin) {
        super(plugin, "time", APPermission.LOOKUP, false, "t");
    }

    @Override
    public void onCommand(SenderAdapter sender, String label, String[] args) throws CommandException {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("ddMMMyy HH:mm.ss");
        if (args.length == 1) {
            sender.sendMessageRaw("&9Server time:");
            sender.sendMessageRaw("&7" + LocalDateTime.now().format(formatter));
            return;
        } else if (args.length == 2) {
            boolean add = args[1].startsWith("+");
            String timeStr = args[1];
            if (add) timeStr = timeStr.substring(1);
            long time;
            try {
                if (timeStr.matches("\\d+e")) {
                    time = Long.parseLong(timeStr.substring(0, timeStr.length() - 1));
                } else {
                    time = TimeUtil.stringToMillis(timeStr);
                }
            } catch (NumberFormatException e) {
                sender.sendLang(Language.L.INVALID_SYNTAX);
                return;
            }
            sender.sendMessageRaw("&9" + args[1].substring(1) + "&f " + (add ? "from now" : "ago") + ":");
            sender.sendMessageRaw(
                    "&7" + LocalDateTime.now().atZone(plugin.getAPPlayer(sender).getTimeZone().toZoneId()).plusSeconds((add ? 1 : -1) * (time / 1000)).format(formatter));
            sender.sendMessageRaw(
                    String.format("&7%s %s", TimeUtil.millisToString(time), add ? "from now" : "ago"));
            sender.sendMessageRaw(
                    String.format("&7%s %s", TimeUtil.millisToStringExtended(time), add ? "from now" : "ago"));
            sender.sendMessageRaw("&7" + (System.currentTimeMillis() + time) + "e");

            return;
        }
        throw new SyntaxException();
    }

    @Override
    public List<String> onTabComplete(SenderAdapter sender, String label, String[] args) {
        // TODO
        return null;
    }

    @Override
    public boolean exists() {
        return true;
    }

}
