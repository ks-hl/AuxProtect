package dev.heliosares.auxprotect.exceptions;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import dev.heliosares.auxprotect.core.Language.L;

public class AuxProtectException extends Exception {
	private static final long serialVersionUID = 8845413629243613163L;

	public AuxProtectException(L l, Object... format) {
		super(l.translate(format));
		this.l = l;
		if (format == null) {
			this.format = null;
		} else {
			this.format = Collections.unmodifiableList(Arrays.asList(format));
		}
	}

	private final L l;
	private final List<Object> format;

	public L getLang() {
		return l;
	}

	public List<Object> getFormat() {
		return format;
	}
}
