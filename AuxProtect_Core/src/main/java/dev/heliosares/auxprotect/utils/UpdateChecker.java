package dev.heliosares.auxprotect.utils;

import jakarta.annotation.Nonnull;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Scanner;

public class UpdateChecker {

    /**
     * <a href="https://www.spigotmc.org/wiki/creating-an-update-checker-that-checks-for-updates">Source</a>
     */
    public static String getVersion(int resourceId) throws IOException {
        try (InputStream inputStream = new URL("https://api.spigotmc.org/legacy/update.php?resource=" + resourceId).openStream(); Scanner scanner = new Scanner(inputStream)) {
            if (scanner.hasNext()) return scanner.next();
        }
        return null;
    }

    /**
     * @return -1 if ver1 is greater, 0 if equal, 1 if ver2 i greater
     */
    public static int compareVersions(String ver1, String ver2) {
        return new Version(ver2).compareTo(new Version(ver1));
    }

    public static class Version implements Comparable<Version> {
        private final List<Integer> version;

        public Version(String versionString) {
            if (versionString.startsWith("v")) versionString = versionString.substring(1);

            String[] parts = versionString.split("[.-]+");
            ArrayList<Integer> array = new ArrayList<>();
            for (String s : parts) {
                try {
                    int part;
                    if (s.startsWith("pre")) part = -1000000;
                    else if (s.startsWith("rc")) part = -500000;
                    else part = 0;

                    s = s.replaceAll("\\D", "");
                    part += Integer.parseInt(s);
                    array.add(part);
                } catch (NumberFormatException ignored) {
                    array.add(0);
                }
            }
            version = Collections.unmodifiableList(array);
        }

        @Override
        public int compareTo(@Nonnull Version other) {
            for (int i = 0; i < version.size() || i < other.version.size(); i++) {
                int versionIntThis = 0;
                int versionIntOther = 0;
                if (i < version.size()) versionIntThis = version.get(i);
                if (i < other.version.size()) versionIntOther = other.version.get(i);
                if (versionIntThis < versionIntOther) return -1;
                if (versionIntThis > versionIntOther) return 1;
            }
            return 0;
        }
    }
}