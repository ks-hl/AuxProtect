package dev.heliosares.auxprotect.utils;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Scanner;

// From: https://www.spigotmc.org/wiki/creating-an-update-checker-that-checks-for-updates
public class UpdateChecker {

	public static String getVersion(JavaPlugin plugin, int resourceId) {
		try (InputStream inputStream = new URL("https://api.spigotmc.org/legacy/update.php?resource=" + resourceId)
				.openStream(); Scanner scanner = new Scanner(inputStream)) {
			if (scanner.hasNext()) {
				return scanner.next();
			}
		} catch (IOException exception) {
			plugin.getLogger().info("Unable to check for updates: " + exception.getMessage());
		}
		return null;
	}
}