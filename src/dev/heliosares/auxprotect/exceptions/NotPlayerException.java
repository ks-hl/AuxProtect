package dev.heliosares.auxprotect.exceptions;

import dev.heliosares.auxprotect.core.Language.L;

public class NotPlayerException extends CommandException {
    private static final long serialVersionUID = -6057939403621317005L;

    public NotPlayerException() {
        super(L.NOTPLAYERERROR);
    }
}