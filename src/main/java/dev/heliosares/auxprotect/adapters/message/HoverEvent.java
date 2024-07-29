package dev.heliosares.auxprotect.adapters.message;

public class HoverEvent {
    final Action action;
    final String value;
    final String itemKey;
    final int quantity;
    final String nbt;

    public enum Action {
        SHOW_TEXT, SHOW_ITEM;
    }

    private HoverEvent(Action action, String text, String itemKey, int quantity, String nbt) {
        this.action = action;
        this.value = text;
        this.itemKey = itemKey;
        this.quantity = quantity;
        this.nbt = nbt;
    }

    public static HoverEvent showText(String value) {
        return new HoverEvent(Action.SHOW_TEXT, ColorTranslator.translateAlternateColorCodes(value), null, 0, null);
    }

    public static HoverEvent showItem(String itemKey, int quantity, String nbt) {
        return new HoverEvent(Action.SHOW_ITEM, null, itemKey, quantity, nbt);
    }
}
