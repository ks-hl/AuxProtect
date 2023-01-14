package dev.heliosares.auxprotect.utils;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class HasteBinAPI {
    // Partial credit to mfnalex - https://www.spigotmc.org/threads/how-to-use-the-pastebin-api-in-java.500953/

    /**
     * hastebin is blocking pastes for some reason, using files only for now.
     */
    @Deprecated
    public static String post(String post) throws Exception {
//		From https://hastebin.com/about.md:
//		$ curl -s -X POST https://hastebin.com/documents -d "Hello World!"
//		{"key":"aeiou"}
//		$ curl https://hastebin.com/raw/aeiou
        URL url = new URL("https://hastebin.com/documents");
        URLConnection con = url.openConnection();
        HttpURLConnection http = (HttpURLConnection) con;
        http.setRequestMethod("POST");
        http.setDoOutput(true);
        http.setDoInput(true);

        byte[] out = post.getBytes(StandardCharsets.UTF_8);
        int length = out.length;
        http.setFixedLengthStreamingMode(length);
        http.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
        http.connect();
        OutputStream os = http.getOutputStream();
        os.write(out);
        InputStream is = http.getInputStream();

        Pattern pattern = Pattern.compile("\\{\\\"key\\\":\\\"(\\w+)\\\"\\}");
        Matcher matcher = pattern.matcher(new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8)).lines()
                .collect(Collectors.joining("\n")));
        matcher.find();

        return "https://hastebin.com/raw/" + matcher.group(1);
    }
}
