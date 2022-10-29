package dev.heliosares.auxprotect.core.commands;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import dev.heliosares.auxprotect.adapters.SenderAdapter;
import dev.heliosares.auxprotect.core.APPermission;
import dev.heliosares.auxprotect.core.Command;
import dev.heliosares.auxprotect.core.IAuxProtect;
import dev.heliosares.auxprotect.utils.TimeUtil;

import dev.heliosares.auxprotect.core.Language;
import dev.heliosares.auxprotect.exceptions.CommandException;
import dev.heliosares.auxprotect.exceptions.SyntaxException;

public class TimeCommand extends Command {

	public TimeCommand(IAuxProtect plugin) {
		super(plugin, "time", APPermission.LOOKUP, "t");
	}

	@Override
	public void onCommand(SenderAdapter sender, String label, String[] args) throws CommandException {
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("ddMMMYY HH:mm.ss");
		if (args.length == 1) {
			sender.sendMessageRaw("§9Server time:");
			sender.sendMessageRaw("§7" + LocalDateTime.now().format(formatter));
			return;
		} else if (args.length == 2) {
			if (args[1].startsWith("+") || args[1].startsWith("-")) {
				boolean add = args[1].startsWith("+");
				long time;
				try {
					time = TimeUtil.stringToMillis(args[1].substring(1));
				} catch (NumberFormatException e) {
					sender.sendLang(Language.L.INVALID_SYNTAX);
					return;
				}
				sender.sendMessageRaw("§9Server time " + (add ? "plus" : "minus") + " " + args[1].substring(1) + ":");
				sender.sendMessageRaw(
						"§7" + LocalDateTime.now().plusSeconds((add ? 1 : -1) * (time / 1000)).format(formatter));
				sender.sendMessageRaw(
						String.format("§7%s %s", TimeUtil.millisToString(time), add ? "from now" : "ago"));
				sender.sendMessageRaw(
						String.format("§7%s %s", TimeUtil.millisToStringExtended(time), add ? "from now" : "ago"));

				return;
			}
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
