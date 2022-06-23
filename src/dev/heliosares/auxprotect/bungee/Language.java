package dev.heliosares.auxprotect.bungee;

import java.util.HashMap;

import dev.heliosares.auxprotect.utils.ColorTranslate;
import net.md_5.bungee.config.Configuration;

public class Language {
	private Configuration lang;

	HashMap<String, String> langMap;

	public Language(Configuration lang) {
		this.lang = lang;
		langMap = new HashMap<>();
	}

	public String translate(String namespace) {
		if (langMap.containsKey(namespace)) {
			return langMap.get(namespace);
		}
		String message = lang.getString(namespace);
		if (message == null)
			return null;
		message = ColorTranslate.cc(message);
		langMap.put(namespace, message);
		return message;
	}
}
