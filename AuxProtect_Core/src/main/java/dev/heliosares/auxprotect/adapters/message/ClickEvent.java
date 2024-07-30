package dev.heliosares.auxprotect.adapters.message;

public class ClickEvent {
    final Action action;
    final String value;

    public enum Action {
        OPEN_URL, RUN_COMMAND, SUGGEST_COMMAND, COPY_TO_CLIPBOARD;
    }

    public ClickEvent(Action action, String value) {
        this.action = action;
        this.value = value;
    }
}
