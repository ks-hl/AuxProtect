package dev.heliosares.auxprotect.core;

public class CommandException extends Exception {
	private static final long serialVersionUID = 5997352707046239L;

	public static class SyntaxException extends CommandException {
		private static final long serialVersionUID = 8762369959571348211L;
	}

	public static class PlatformException extends CommandException {
		private static final long serialVersionUID = 259350605635468408L;
	}

	public static class NotPlayerException extends CommandException {
		private static final long serialVersionUID = -6057939403621317005L;
	}
}
