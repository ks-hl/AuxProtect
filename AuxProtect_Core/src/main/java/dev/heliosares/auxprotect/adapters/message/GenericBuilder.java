package dev.heliosares.auxprotect.adapters.message;

import dev.heliosares.auxprotect.adapters.sender.SenderAdapter;
import dev.heliosares.auxprotect.core.IAuxProtect;
import dev.heliosares.auxprotect.exceptions.NotPlayerException;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class GenericBuilder {
    private final IAuxProtect plugin;
    private ArrayList<GenericComponent> currentLine = new ArrayList<>();
    private final ArrayList<ArrayList<GenericComponent>> lines = new ArrayList<>() {{
        add(currentLine);
    }};
    private GenericComponent lastAdded;

    public GenericBuilder(IAuxProtect plugin) {
        this.plugin = plugin;
    }

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
            try {
                plugin.getMessageBuilder().send(to, line);
            } catch (NotPlayerException e) {
                sendRaw(to, line);
            }
        }
    }

    protected void sendRaw(SenderAdapter<?, ?> to, List<GenericComponent> line) {
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
