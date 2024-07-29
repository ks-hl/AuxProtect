package dev.heliosares.auxprotect.adapters.message;

import dev.heliosares.auxprotect.adapters.sender.BungeeComponentSender;
import dev.heliosares.auxprotect.adapters.sender.KyoriSender;
import dev.heliosares.auxprotect.adapters.sender.SenderAdapter;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.nbt.api.BinaryTagHolder;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.ItemTag;
import net.md_5.bungee.api.chat.hover.content.Item;
import net.md_5.bungee.api.chat.hover.content.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class GenericBuilder {
    private ArrayList<GenericComponent> currentLine = new ArrayList<>();
    private final ArrayList<ArrayList<GenericComponent>> lines = new ArrayList<>() {{
        add(currentLine);
    }};
    private GenericComponent lastAdded;

    public GenericBuilder append(GenericComponent genericComponent) {
        currentLine.add(lastAdded = genericComponent);
        return this;
    }

    /**
     * Breaks this builder into a completely new builder such that succeeding appendages are sent in a completely separate message. For a simple line break, use {@link this#newLine()}
     */
    public GenericBuilder builderBreak() {
        lines.add(currentLine = new ArrayList<>());
        return this;
    }

    /**
     * Appends a new line character (\n) onto the message. To send following components in a new message entirely, use {@link this#builderBreak()}
     */
    public GenericBuilder newLine() {
        append("\n");
        return this;
    }

    public GenericBuilder append(String text) {
        return append(new GenericComponent(text));
    }

    public GenericBuilder append(Object value) {
        return append(Objects.toString(value));
    }

    private void checkNonNullLastAdded() {
        if (lastAdded == null) {
            throw new NullPointerException("Can not modify component before adding a component");
        }
    }

    public GenericBuilder click(dev.heliosares.auxprotect.adapters.message.ClickEvent clickEvent) {
        checkNonNullLastAdded();
        lastAdded.click(clickEvent);
        return this;
    }

    public GenericBuilder hover(dev.heliosares.auxprotect.adapters.message.HoverEvent event) {
        checkNonNullLastAdded();
        lastAdded.hover(event);
        return this;
    }

    public GenericBuilder hover(String text) {
        checkNonNullLastAdded();
        lastAdded.hover(text);
        return this;
    }

    public GenericBuilder event(dev.heliosares.auxprotect.adapters.message.ClickEvent event) {
        return click(event);
    }

    public GenericBuilder event(dev.heliosares.auxprotect.adapters.message.HoverEvent event) {
        return hover(event);
    }

    public GenericBuilder color(GenericTextColor color) {
        checkNonNullLastAdded();
        lastAdded.color(color);
        return this;
    }

    public GenericBuilder color(String hex) {
        return color(new GenericTextColor(hex));
    }


    public GenericBuilder bold(boolean bold) {
        checkNonNullLastAdded();
        lastAdded.bold(bold);
        return this;
    }

    public GenericBuilder italics(boolean italics) {
        checkNonNullLastAdded();
        lastAdded.italics(italics);
        return this;
    }

    public GenericBuilder underlined(boolean underlined) {
        checkNonNullLastAdded();
        lastAdded.underlined(underlined);
        return this;
    }

    public GenericBuilder strikethrough(boolean strikethrough) {
        checkNonNullLastAdded();
        lastAdded.strikethrough(strikethrough);
        return this;
    }

    public GenericBuilder magic(boolean magic) {
        checkNonNullLastAdded();
        lastAdded.magic(magic);
        return this;
    }

    public void send(SenderAdapter<?, ?> to) {
        for (ArrayList<GenericComponent> line : lines) {
            send(to, line);
        }
    }

    private static void send(SenderAdapter<?, ?> to, List<GenericComponent> line) {
        if (to instanceof BungeeComponentSender bungeeComponentSender) {
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
        } else if (to instanceof KyoriSender kyoriSender) {
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
                        case OPEN_URL -> net.kyori.adventure.text.event.ClickEvent.openUrl(component.clickEvent.value);
                        case RUN_COMMAND ->
                                net.kyori.adventure.text.event.ClickEvent.runCommand(component.clickEvent.value);
                        case SUGGEST_COMMAND ->
                                net.kyori.adventure.text.event.ClickEvent.suggestCommand(component.clickEvent.value);
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
                                HoverEvent.ShowItem.showItem(Key.key(component.hoverEvent.itemKey), //
                                        component.hoverEvent.quantity, //
                                        BinaryTagHolder.binaryTagHolder(component.hoverEvent.nbt) //
                                )));
                    }
                }
                builder.append(component1);
            }
            kyoriSender.sendMessage(builder.build());
        } else {
            StringBuilder out = new StringBuilder();
            for (GenericComponent component : line) {
                String text = component.text;
                if (to.isConsole()) {
                    text = ColorTranslator.stripColor(text);
                }
                out.append(text);
            }
            to.sendMessageRaw(out.toString());
        }
    }
}
