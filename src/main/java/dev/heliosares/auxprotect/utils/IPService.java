package dev.heliosares.auxprotect.utils;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Scanner;
import java.util.TimeZone;

public class IPService {
    private static final String API_URL = "https://ipapi.co/<ip>/yaml/";

    @Nonnull
    public static TimeZone getTimeZoneForIP(@Nullable String ip) {
        if (ip != null) try {
            URL url = new URL(API_URL.replace("<ip>", ip));

            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");

            if (conn.getResponseCode() != 200) throw new IOException();

            Scanner scanner = new Scanner(conn.getInputStream());
            String response = scanner.useDelimiter("\\A").next();
            scanner.close();

            for (String line : response.split("[\n\r]")) {
                String[] kv = line.split(":\\s*");
                if (kv[0].equalsIgnoreCase("timezone") && kv.length >= 2) {
                    return TimeZone.getTimeZone(kv[1]);
                }
            }
        } catch (IOException ignored) {
        }
        return TimeZone.getDefault();
    }
}
