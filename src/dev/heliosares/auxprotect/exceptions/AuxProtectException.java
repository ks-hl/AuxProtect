package dev.heliosares.auxprotect.exceptions;

import dev.heliosares.auxprotect.core.Language.L;

import java.util.List;

public class AuxProtectException extends Exception {
    private final L l;
    private final List<Object> format;

    public AuxProtectException(L l, Object... format) {
        super(l.translate(format));
        this.l = l;
        if (format == null) {
            this.format = null;
        } else {
            this.format = List.of(format);
        }
    }

    public L getLang() {
        return l;
    }

    public List<Object> getFormat() {
        return format;
    }
}
