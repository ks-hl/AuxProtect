package dev.heliosares.auxprotect.utils;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Scanner;

public class UpdateChecker {

    /**
     * From:
     * https://www.spigotmc.org/wiki/creating-an-update-checker-that-checks-for-updates
     */
    public static String getVersion(JavaPlugin plugin, int resourceId) throws IOException {
        try (InputStream inputStream = new URL("https://api.spigotmc.org/legacy/update.php?resource=" + resourceId)
                .openStream(); Scanner scanner = new Scanner(inputStream)) {
            if (scanner.hasNext()) {
                return scanner.next();
            }
        }
        return null;
    }

    public static Integer[] versionAsIntArray(String version) {
        String[] parts = version.split("[\\.\\-]+");
        if (version.startsWith("v")) {
            version = version.substring(1);
        }
        ArrayList<Integer> array = new ArrayList<>();
        for (int i = 0; i < parts.length; i++) {
            try {
                int part = 0;
                if (parts[i].startsWith("pre")) {
                    part = Integer.parseInt(parts[i].substring(3)) - 1000000;
                } else if (parts[i].startsWith("rc")) {
                    part = Integer.parseInt(parts[i].substring(3)) - 500000;
                } else {
                    part = Integer.parseInt(parts[i]);
                }
                array.add(part);
            } catch (NumberFormatException ignored) {

            }
        }
        return array.toArray(new Integer[0]);
    }

    /**
     * @returns -1 if ver1 is greater, 0 if equal, 1 if ver2 i greater
     */
    public static int compareVersions(String ver1, String ver2) {
        Integer[] ver1int = versionAsIntArray(ver1);
        Integer[] ver2int = versionAsIntArray(ver2);
        for (int i = 0; i < ver1int.length || i < ver2int.length; i++) {
            int ver1part = 0;
            int ver2part = 0;
            if (i < ver1int.length) {
                ver1part = ver1int[i];
            }
            if (i < ver2int.length) {
                ver2part = ver2int[i];
            }
            if (ver1part > ver2part) {
                return -1;
            }
            if (ver1part < ver2part) {
                return 1;
            }
        }
        return 0;
    }
}