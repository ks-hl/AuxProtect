package dev.heliosares.auxprotect.exceptions;

import dev.heliosares.auxprotect.utils.StackUtil;

import java.sql.SQLException;

public class BusyException extends SQLException {
    public final StackTraceElement[] stack;

    public BusyException(StackTraceElement[] stack) {
        super("Database busy, currently held by " + (stack == null ? "none" : StackUtil.format(stack, 0)));
        this.stack = stack;
    }
}
