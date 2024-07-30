package dev.heliosares.auxprotect.adapters.message;

import dev.heliosares.auxprotect.adapters.sender.BungeeComponentSender;
import dev.heliosares.auxprotect.adapters.sender.SenderAdapter;
import dev.heliosares.auxprotect.exceptions.NotPlayerException;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.ItemTag;
import net.md_5.bungee.api.chat.hover.content.Item;
import net.md_5.bungee.api.chat.hover.content.Text;

import java.util.List;

public class SpigotMessageBuilder extends MessageBuilder {
    @Override
    protected void send(SenderAdapter<?, ?> to, List<GenericComponent> line) throws NotPlayerException {
        if (!(to instanceof BungeeComponentSender bungeeComponentSender)) throw new NotPlayerException();

        ComponentBuilder builder = new ComponentBuilder();
        for (GenericComponent component : line) {
            String text = component.text;
            if (to.isConsole()) {
                text = ColorTranslator.stripColor(text);
            }
            builder.append(text)
                    .bold(component.bold)
                    .italic(component.italics)
                    .underlined(component.underlined)
                    .strikethrough(component.strikethrough)
                    .obfuscated(component.magic);

            if (component.color != null) {
                if (component.color.getHex() == null) {
                    builder.color(ChatColor.getByChar(component.color.getColorChar()));
                } else {
                    builder.color(ChatColor.of(component.color.getHex()));
                }
            }
            if (component.clickEvent == null) {
                builder.event((net.md_5.bungee.api.chat.ClickEvent) null);
            } else {
                builder.event(new net.md_5.bungee.api.chat.ClickEvent(switch (component.clickEvent.action) {
                    case OPEN_URL -> net.md_5.bungee.api.chat.ClickEvent.Action.OPEN_URL;
                    case RUN_COMMAND -> net.md_5.bungee.api.chat.ClickEvent.Action.RUN_COMMAND;
                    case SUGGEST_COMMAND -> net.md_5.bungee.api.chat.ClickEvent.Action.SUGGEST_COMMAND;
                    case COPY_TO_CLIPBOARD -> net.md_5.bungee.api.chat.ClickEvent.Action.COPY_TO_CLIPBOARD;
                }, component.clickEvent.value));
            }
            if (component.hoverEvent == null) {
                builder.event((net.md_5.bungee.api.chat.HoverEvent) null);
            } else {
                switch (component.hoverEvent.action) {
                    case SHOW_TEXT ->
                            builder.event(new net.md_5.bungee.api.chat.HoverEvent(net.md_5.bungee.api.chat.HoverEvent.Action.SHOW_TEXT, new Text(component.hoverEvent.value)));
                    case SHOW_ITEM ->
                            builder.event(new net.md_5.bungee.api.chat.HoverEvent(net.md_5.bungee.api.chat.HoverEvent.Action.SHOW_ITEM, new Item( //
                                    component.hoverEvent.itemKey, //
                                    component.hoverEvent.quantity, //
                                    ItemTag.ofNbt(component.hoverEvent.nbt) //
                            )));
                }
            }
        }
        bungeeComponentSender.sendMessage(builder.create());
    }
}
