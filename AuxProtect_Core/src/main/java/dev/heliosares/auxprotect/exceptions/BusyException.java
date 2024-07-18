package dev.heliosares.auxprotect.exceptions;

public class BusyException extends Exception {
    public BusyException(StackTraceElement[] stack, long threadID) {
        super("Database busy, currently held by " + (threadID < 0 ? "none" : ("Thread #" + threadID)));
    }
}
