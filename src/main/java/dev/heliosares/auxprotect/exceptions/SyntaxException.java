package dev.heliosares.auxprotect.exceptions;

import dev.heliosares.auxprotect.core.Language.L;

public class SyntaxException extends CommandException {
    public SyntaxException() {
        super(L.INVALID_SYNTAX);
    }
}