package dev.heliosares.auxprotect.exceptions;

import dev.heliosares.auxprotect.core.Language.L;

public class CommandException extends AuxProtectException {
    private static final long serialVersionUID = 5997352707046239L;

    public CommandException(L l, Object... format) {
        super(l, format);
    }
}
