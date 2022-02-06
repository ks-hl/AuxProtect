package dev.heliosares.auxprotect.utils;

import java.util.HashMap;

import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;

public class Language {
	private FileConfiguration lang;

	HashMap<String, String> langMap;

	public Language(FileConfiguration lang) {
		this.lang = lang;
		langMap = new HashMap<>();
	}

	public String translate(String namespace) {
		if (langMap.containsKey(namespace)) {
			return langMap.get(namespace);
		}
		String message = lang.getString(namespace);
		if (message == null) {
			return "[error:" + namespace + "]";
		}
		message = ColorTranslate.cc(message);
		langMap.put(namespace, message);
		return message;
	}

	public void send(CommandSender sender, String namespace, Object... args) {
		String msg = translate(namespace);
		msg = String.format(msg, args);
		for (String msg_ : msg.split("\\\\n")) {
			sender.sendMessage(msg_);
		}
	}
}
