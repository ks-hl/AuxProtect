package dev.heliosares.auxprotect.core;

import dev.heliosares.auxprotect.adapters.SenderAdapter;
import dev.heliosares.auxprotect.exceptions.CommandException;

import javax.annotation.Nullable;
import java.util.List;

public abstract class Command {
    protected final IAuxProtect plugin;
    protected final String label;
    private final boolean async;
    protected final String[] aliases;
    protected final APPermission permission;
    protected boolean tabComplete = true;

    public Command(IAuxProtect plugin, String label, APPermission permission, boolean async, String... aliases) {
        this.plugin = plugin;
        this.label = label;
        this.permission = permission;
        this.async = async;
        this.aliases = aliases;
    }

    public abstract void onCommand(SenderAdapter sender, String label, String[] args) throws CommandException;

    public abstract @Nullable List<String> onTabComplete(SenderAdapter sender, String label, String[] args);

    public String getLabel() {
        return label;
    }

    public String[] getAliases() {
        return aliases;
    }

    public boolean hasPermission(SenderAdapter sender) {
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

    public Command setTabComplete(boolean tabComplete) {
        this.tabComplete = tabComplete;
        return this;
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
