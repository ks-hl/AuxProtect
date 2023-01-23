package dev.heliosares.auxprotect.exceptions;

import dev.heliosares.auxprotect.core.Language.L;

public class PlatformException extends CommandException {
    public PlatformException() {
        super(L.UNKNOWN_SUBCOMMAND);
    }
}