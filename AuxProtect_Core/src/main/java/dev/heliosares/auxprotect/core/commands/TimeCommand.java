package dev.heliosares.auxprotect.core.commands;

import dev.heliosares.auxprotect.adapters.message.ClickEvent;
import dev.heliosares.auxprotect.adapters.message.ColorTranslator;
import dev.heliosares.auxprotect.adapters.message.GenericBuilder;
import dev.heliosares.auxprotect.adapters.message.GenericTextColor;
import dev.heliosares.auxprotect.adapters.sender.SenderAdapter;
import dev.heliosares.auxprotect.core.APPermission;
import dev.heliosares.auxprotect.core.APPlayer;
import dev.heliosares.auxprotect.core.Command;
import dev.heliosares.auxprotect.core.IAuxProtect;
import dev.heliosares.auxprotect.core.Language;
import dev.heliosares.auxprotect.exceptions.CommandException;
import dev.heliosares.auxprotect.exceptions.SyntaxException;
import dev.heliosares.auxprotect.utils.TimeUtil;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.TimeZone;
import java.util.function.BiConsumer;

public class TimeCommand<S, P extends IAuxProtect, SA extends SenderAdapter<S, P>> extends Command<S, P, SA> {

    public TimeCommand(P plugin) {
        super(plugin, "time", APPermission.LOOKUP, false, "t");
    }

    @Override
    public void onCommand(SA sender, String label, String[] args) throws CommandException {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("ddMMMyy HH:mm.ss v");

        if (args.length == 1 || args.length == 2) {
            long epochTime, relativeTime;
            boolean add = false;
            final GenericBuilder builder = new GenericBuilder(plugin);
            if (args.length == 1) {
                builder.append(Language.L.COMMAND__TIME__SERVER_TIME.translate());
                epochTime = System.currentTimeMillis();
                relativeTime = 0;
            } else {
                String timeStr = args[1];
                add = timeStr.startsWith("+");
                if (add || timeStr.startsWith("-")) timeStr = timeStr.substring(1);
                boolean exact = timeStr.matches("\\d+e");
                try {
                    if (exact) {
                        epochTime = Long.parseLong(timeStr.substring(0, timeStr.length() - 1));
                        relativeTime = System.currentTimeMillis() - epochTime;
                    } else {
                        relativeTime = TimeUtil.stringToMillis(timeStr);
                        if (!add) relativeTime *= -1;
                        epochTime = System.currentTimeMillis() - relativeTime;
                    }
                } catch (NumberFormatException e) {
                    throw new SyntaxException();
                }
                builder.append(Language.convert("&9" + timeStr + "&f "));
                if (!exact) {
                    if (add) {
                        builder.append("from now");
                    } else {
                        builder.append("ago");
                    }
                }
            }
            builder.append(": &7(" + Language.L.COMMAND__TIME__CLICK_TO_COPY.translate() + ")");

            BiConsumer<String, String> consume = (lineLabel, lineValue) -> {
                lineLabel = Language.convert(lineLabel);
                lineValue = Language.convert(lineValue);
                builder.append("\n");
                builder.append(lineLabel).color(GenericTextColor.WHITE).append(": ");
                builder.append(lineValue) //
                        .color(GenericTextColor.GRAY)
                        .hover(Language.L.RESULTS__CLICK_TO_COPY.translate()) //
                        .click(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, ColorTranslator.stripColor(lineValue)));
            };
            TimeZone timeZone = Optional.ofNullable(plugin.getAPPlayer(sender)).map(APPlayer::getTimeZone).orElse(TimeZone.getDefault());
            consume.accept(Language.L.COMMAND__TIME__FORMATTED.translate(), Instant.ofEpochMilli(epochTime).atZone(timeZone.toZoneId()).format(formatter));

            if (relativeTime != 0) {
                Language.L fromNowAgo = (add ? Language.L.RESULTS__TIME_FROM_NOW : Language.L.RESULTS__TIME);
                consume.accept(Language.L.COMMAND__TIME__RELATIVE.translate(), fromNowAgo.translate(TimeUtil.millisToString(relativeTime)));
                consume.accept(Language.L.COMMAND__TIME__RELATIVE_EXPANDED.translate(), fromNowAgo.translate(TimeUtil.millisToStringExtended(relativeTime)));
            }

            consume.accept(Language.L.COMMAND__TIME__EPOCH_TIME.translate(), epochTime + "e");

            if (relativeTime != 0) {
                consume.accept(Language.L.COMMAND__TIME__EPOCH_TIME.translate() + " +/- 1s", (epochTime - 1000) + "e-" + (epochTime + 1000) + "e");
            }

            builder.send(sender);

            return;
        }
        throw new SyntaxException();
    }

    @Override
    public List<String> onTabComplete(SA sender, String label, String[] args) {
        // TODO
        return null;
    }

    @Override
    public boolean exists() {
        return true;
    }

}
