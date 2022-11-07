package dev.heliosares.auxprotect.exceptions;

import dev.heliosares.auxprotect.core.Language.L;

public class AlreadyExistsException extends AuxProtectException {
    private static final long serialVersionUID = -4118326876128319175L;

    // TODO lang
    public AlreadyExistsException() {
        super(L.ERROR);
    }
}