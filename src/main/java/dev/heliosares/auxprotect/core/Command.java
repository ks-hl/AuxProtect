package dev.heliosares.auxprotect.core;

import dev.heliosares.auxprotect.adapters.sender.SenderAdapter;
import dev.heliosares.auxprotect.exceptions.CommandException;

import javax.annotation.Nullable;
import java.util.List;

public abstract class Command<S, P extends IAuxProtect, SA extends SenderAdapter<S, P>> {
    protected final P plugin;
    protected final String label;
    private final boolean async;
    protected final String[] aliases;
    protected final APPermission permission;
    protected boolean tabComplete = true;

    public Command(P plugin, String label, APPermission permission, boolean async, String... aliases) {
        this.plugin = plugin;
        this.label = label;
        this.permission = permission;
        this.async = async;
        this.aliases = aliases;
    }

    public abstract void onCommand(SA sender, String label, String[] args) throws CommandException;

    public abstract @Nullable List<String> onTabComplete(SA sender, String label, String[] args);

    public String getLabel() {
        return label;
    }

    public String[] getAliases() {
        return aliases;
    }

    public boolean hasPermission(SA sender) {
        return permission.hasPermission(sender);
    }

    public boolean matches(String label) {
        if (label.equalsIgnoreCase(this.label)) {
            return true;
        }
        if (aliases == null) {
            return false;
        }
        for (String alias : aliases) {
            if (alias.equalsIgnoreCase(label)) {
                return true;
            }
        }
        return false;
    }

    public void setTabComplete(boolean tabComplete) {
        this.tabComplete = tabComplete;
    }

    public boolean doTabComplete() {
        return tabComplete;
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public abstract boolean exists();

    public boolean isAsync() {
        return async;
    }
}
