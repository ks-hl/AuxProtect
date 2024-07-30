package dev.heliosares.auxprotect.adapters.message;

import dev.heliosares.auxprotect.adapters.sender.KyoriSender;
import dev.heliosares.auxprotect.adapters.sender.SenderAdapter;
import dev.heliosares.auxprotect.exceptions.NotPlayerException;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.nbt.api.BinaryTagHolder;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;

import java.util.List;

public class VelocityMessageBuilder extends MessageBuilder {
    @Override
    protected void send(SenderAdapter<?, ?> to, List<GenericComponent> line) throws NotPlayerException {
        if (!(to instanceof KyoriSender kyoriSender)) throw new NotPlayerException();

        TextComponent.Builder builder = Component.text();
        for (GenericComponent component : line) {
            String text = component.text;
            if (to.isConsole()) {
                text = ColorTranslator.stripColor(text);
            }
            Component component1 = Component.text(text);
            if (component.bold) component1 = component1.decorate(TextDecoration.BOLD);
            if (component.italics) component1 = component1.decorate(TextDecoration.ITALIC);
            if (component.underlined) component1 = component1.decorate(TextDecoration.UNDERLINED);
            if (component.strikethrough) component1 = component1.decorate(TextDecoration.STRIKETHROUGH);
            if (component.magic) component1 = component1.decorate(TextDecoration.OBFUSCATED);
            if (component.color != null) {
                if (component.color.getHex() == null) {
                    component1 = component1.color(switch (component.color.getColorChar()) {
                        case '0' -> NamedTextColor.BLACK;
                        case '1' -> NamedTextColor.DARK_BLUE;
                        case '2' -> NamedTextColor.DARK_GREEN;
                        case '3' -> NamedTextColor.DARK_AQUA;
                        case '4' -> NamedTextColor.DARK_RED;
                        case '5' -> NamedTextColor.DARK_PURPLE;
                        case '6' -> NamedTextColor.GOLD;
                        case '7' -> NamedTextColor.GRAY;
                        case '8' -> NamedTextColor.DARK_GRAY;
                        case '9' -> NamedTextColor.BLUE;
                        case 'a' -> NamedTextColor.GREEN;
                        case 'b' -> NamedTextColor.AQUA;
                        case 'c' -> NamedTextColor.RED;
                        case 'd' -> NamedTextColor.LIGHT_PURPLE;
                        case 'e' -> NamedTextColor.YELLOW;
                        case 'f' -> NamedTextColor.WHITE;
                        default -> throw new IllegalArgumentException("Invalid color code");
                    });
                } else {
                    component1 = component1.color(TextColor.fromHexString(component.color.getHex()));
                }
            }
            if (component.clickEvent != null) {
                component1 = component1.clickEvent(switch (component.clickEvent.action) {
                    case OPEN_URL -> ClickEvent.openUrl(component.clickEvent.value);
                    case RUN_COMMAND -> ClickEvent.runCommand(component.clickEvent.value);
                    case SUGGEST_COMMAND -> ClickEvent.suggestCommand(component.clickEvent.value);
                    case COPY_TO_CLIPBOARD -> ClickEvent.copyToClipboard(component.clickEvent.value);
                });
            }
            if (component.hoverEvent != null) {
                switch (component.hoverEvent.action) {
                    case SHOW_TEXT -> {
                        assert component.hoverEvent.value != null;
                        component1 = component1.hoverEvent(HoverEvent.showText(Component.text(component.hoverEvent.value)));
                    }
                    case SHOW_ITEM -> component1 = component1.hoverEvent(HoverEvent.showItem( //
                            Key.key(component.hoverEvent.itemKey), //
                            component.hoverEvent.quantity, //
                            BinaryTagHolder.binaryTagHolder(component.hoverEvent.nbt) //
                    ));
                }
            }
            builder.append(component1);
        }
        kyoriSender.sendMessage(builder.build());
    }
}
