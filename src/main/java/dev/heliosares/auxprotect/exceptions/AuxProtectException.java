package dev.heliosares.auxprotect.exceptions;

import dev.heliosares.auxprotect.core.Language.L;

public class AuxProtectException extends Exception {
    private final L l;

    public AuxProtectException(L l, Object... format) {
        super(l.translate(format));
        this.l = l;
    }

    public L getLang() {
        return l;
    }

}
