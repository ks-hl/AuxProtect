package dev.heliosares.auxprotect.adapters.message;

public class GenericComponent {
    final String text;
    ClickEvent clickEvent;
    HoverEvent hoverEvent;
    GenericTextColor color;
    boolean bold, italics, underlined, strikethrough, magic;

    public GenericComponent(String text) {
        this.text = ColorTranslator.translateAlternateColorCodes(text);
    }

    public GenericComponent(Object o) {
        this.text = o.toString();
    }

    public GenericComponent click(ClickEvent clickEvent) {
        this.clickEvent = clickEvent;
        return this;
    }

    public GenericComponent hover(String text) {
        return hover(HoverEvent.showText(text));
    }

    public GenericComponent hover(HoverEvent event) {
        this.hoverEvent = event;
        return this;
    }

    public GenericComponent color(GenericTextColor color) {
        this.color = color;
        return this;
    }


    public GenericComponent bold(boolean bold) {
        this.bold = bold;
        return this;
    }

    public GenericComponent italics(boolean italics) {
        this.italics = italics;
        return this;
    }

    public GenericComponent underlined(boolean underlined) {
        this.underlined = underlined;
        return this;
    }

    public GenericComponent strikethrough(boolean strikethrough) {
        this.strikethrough = strikethrough;
        return this;
    }

    public GenericComponent magic(boolean magic) {
        this.magic = magic;
        return this;
    }
}
