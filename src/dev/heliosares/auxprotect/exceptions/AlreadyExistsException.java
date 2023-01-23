package dev.heliosares.auxprotect.exceptions;

import dev.heliosares.auxprotect.core.Language.L;

public class AlreadyExistsException extends AuxProtectException {
    public AlreadyExistsException() {
        super(L.ERROR);
    }
}