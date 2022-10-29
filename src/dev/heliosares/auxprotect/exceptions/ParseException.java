package dev.heliosares.auxprotect.exceptions;

import dev.heliosares.auxprotect.core.Language.L;

public class ParseException extends AuxProtectException {
	private static final long serialVersionUID = -3371728414735680055L;

	public ParseException(L l, Object... format) {
		super(l, format);
	}
}