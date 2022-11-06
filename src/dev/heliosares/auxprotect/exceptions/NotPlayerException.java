package dev.heliosares.auxprotect.exceptions;

import dev.heliosares.auxprotect.core.Language.L;

public class NotPlayerException extends CommandException {
    public NotPlayerException() {
        super(L.NOTPLAYERERROR);
    }

    private static final long serialVersionUID = -6057939403621317005L;
}