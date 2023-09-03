package dev.heliosares.auxprotect.utils;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Scanner;
import java.util.TimeZone;

public class IPService {
    private static final String API_URL = "https://ipapi.co/<ip>/yaml/";

    public static TimeZone getTimeZoneForIP(String ip) throws IOException {
        URL url = new URL(API_URL.replace("<ip>", ip));

        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");

        int responseCode = conn.getResponseCode();

        if (responseCode != 200) {
            throw new IOException("Failed to get response from API. HTTP Code: " + responseCode);
        }

        Scanner scanner = new Scanner(conn.getInputStream());
        String response = scanner.useDelimiter("\\A").next();
        scanner.close();

        for (String line : response.split("[\n\r]")) {
            String[] kv = line.split(":\\s*");
            if (kv[0].equalsIgnoreCase("timezone") && kv.length >= 2) {
                return TimeZone.getTimeZone(kv[1]);
            }
        }

        return null;
    }
}
