package dev.heliosares.auxprotect.core.commands;

import dev.heliosares.auxprotect.adapters.sender.SenderAdapter;
import dev.heliosares.auxprotect.core.APPermission;
import dev.heliosares.auxprotect.core.Command;
import dev.heliosares.auxprotect.core.IAuxProtect;
import dev.heliosares.auxprotect.core.Language;
import dev.heliosares.auxprotect.exceptions.CommandException;
import dev.heliosares.auxprotect.exceptions.SyntaxException;
import dev.heliosares.auxprotect.utils.TimeUtil;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.hover.content.Text;
import org.bukkit.ChatColor;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.Consumer;

public class TimeCommand extends Command {

    public TimeCommand(IAuxProtect plugin) {
        super(plugin, "time", APPermission.LOOKUP, false, "t");
    }

    @Override
    public void onCommand(SenderAdapter sender, String label, String[] args) throws CommandException {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("ddMMMyy HH:mm.ss");
        boolean now = args.length == 1;
        if (now || args.length == 2) {
            long time;
            boolean add = false;
            ComponentBuilder builder;
            if (now) {
                builder = new ComponentBuilder(Language.L.COMMAND__TIME__SERVER_TIME.translate());
                time = 0;
            } else {
                add = args[1].startsWith("+");
                String timeStr = args[1];
                if (add || timeStr.startsWith("-")) timeStr = timeStr.substring(1);
                try {
                    if (timeStr.matches("\\d+e")) {
                        time = Long.parseLong(timeStr.substring(0, timeStr.length() - 1));
                    } else {
                        time = TimeUtil.stringToMillis(timeStr);
                    }
                } catch (NumberFormatException e) {
                    throw new SyntaxException();
                }
                builder = new ComponentBuilder(Language.convert("&9" + timeStr + "&f " + (add ? "from now" : "ago") + ":"));
            }
            Consumer<String> consume = ln -> {
                ln = Language.convert(ln);
                builder.append("\n").append(ln) //
                        .event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(Language.L.RESULTS__CLICK_TO_COPY.translate()))) //
                        .event(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, ChatColor.stripColor(ln)));
            };
            consume.accept("&7" + LocalDateTime.now().atZone(plugin.getAPPlayer(sender).getTimeZone().toZoneId()).plusSeconds((add ? 1 : -1) * (time / 1000)).format(formatter));
            if (!now) {
                String fromNowAgo = add ? "from now" : "ago";
                consume.accept(String.format("&7%s %s", TimeUtil.millisToString(time), fromNowAgo));
                consume.accept(String.format("&7%s %s", TimeUtil.millisToStringExtended(time), fromNowAgo));
            }
            consume.accept("&7" + (System.currentTimeMillis() + (add ? 1 : -1) * time) + "e");

            sender.sendMessage(builder.create());

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
