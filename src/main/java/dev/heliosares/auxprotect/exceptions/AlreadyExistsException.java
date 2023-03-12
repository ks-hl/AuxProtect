package dev.heliosares.auxprotect.exceptions;

import dev.heliosares.auxprotect.core.Language.L;
import dev.heliosares.auxprotect.database.EntryAction;

public class AlreadyExistsException extends AuxProtectException {
    private final EntryAction preexistingAction;

    public AlreadyExistsException(EntryAction preexistingAction) {
        super(L.ERROR);
        this.preexistingAction = preexistingAction;
    }

    public EntryAction getPreexistingAction() {
        return preexistingAction;
    }
}