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

	private static int[] versionAsIntArray(String version) {
		String[] parts = version.split("\\.");
		if (version.startsWith("v")) {
			version = version.substring(1);
		}
		int array[] = new int[parts.length];
		for (int i = 0; i < parts.length; i++) {
			try {
				array[i] = Integer.parseInt(parts[i]);
			} catch (NumberFormatException ignored) {

			}
		}
		return array;
	}

	public static String getHigherVersion(String ver1, String ver2) {
		int compare = compareVersions(ver1, ver2);
		if (compare == -1) {
			return ver1;
		}
		if (compare == 1) {
			return ver2;
		}
		return "equal";
	}

	/**
	 * @returns -1 if ver1 is greater, 0 if equal, 1 if ver2 i greater
	 */
	public static int compareVersions(String ver1, String ver2) {
		int[] ver1int = versionAsIntArray(ver1);
		int[] ver2int = versionAsIntArray(ver2);
		for (int i = 0; i < ver1int.length && i < ver2int.length; i++) {
			int ver1part = ver1int[i];
			int ver2part = ver2int[i];
			if (ver1part > ver2part) {
				return -1;
			}
			if (ver1part < ver2part) {
				return 1;
			}
		}
		if (ver1int.length > ver2int.length) {
			return -1;
		}
		if (ver1int.length < ver2int.length) {
			return 1;
		}
		return 0;
	}
}