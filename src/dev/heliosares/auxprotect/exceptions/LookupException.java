package dev.heliosares.auxprotect.exceptions;

import dev.heliosares.auxprotect.core.Language.L;

public class LookupException extends AuxProtectException {
    public LookupException(L l, Object... format) {
        super(l, format);
    }
}