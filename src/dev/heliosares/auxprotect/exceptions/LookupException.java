package dev.heliosares.auxprotect.exceptions;

import dev.heliosares.auxprotect.core.Language.L;

public class LookupException extends AuxProtectException {

	private static final long serialVersionUID = -8329753973868577238L;

	public LookupException(L l, Object... format) {
		super(l, format);
	}
}