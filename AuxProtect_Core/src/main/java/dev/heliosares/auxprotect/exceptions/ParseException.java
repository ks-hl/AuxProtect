package dev.heliosares.auxprotect.exceptions;

import dev.heliosares.auxprotect.core.Language.L;

public class ParseException extends AuxProtectException {

    public ParseException(L l, Object... format) {
        super(l, format);
    }
}