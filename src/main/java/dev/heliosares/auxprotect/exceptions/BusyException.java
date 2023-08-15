package dev.heliosares.auxprotect.exceptions;

public class BusyException extends IllegalStateException {
    public BusyException(StackTraceElement[] stack, long threadID) {
        super("Database busy, currently held by " + (threadID < 0 ? "none" : ("Thread #" + threadID)));
    }
}
