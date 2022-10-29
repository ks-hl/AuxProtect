package dev.heliosares.auxprotect.core;

import dev.heliosares.auxprotect.adapters.ConfigAdapter;
import dev.heliosares.auxprotect.adapters.SenderAdapter;
import dev.heliosares.auxprotect.utils.ColorTranslate;

public class Language {
	private static ConfigAdapter lang;

	public static void load(ConfigAdapter lang) {
		Language.lang = lang;
	}

	public static String translate(String namespace, Object... format) {
		String message = lang.getString(namespace);
		if (message == null) {
			return "[error:" + namespace + "]";
		}
		message = ColorTranslate.cc(message);
		if (format == null || format.length == 0) {
			return message;
		}
		return String.format(message, format);
	}

	public static void send(SenderAdapter sender, String namespace, Object... args) {
		String msg = translate(namespace, args);
		for (String msg_ : msg.split("\\\\n")) {
			sender.sendMessageRaw(msg_);
		}
	}
}
