package dev.heliosares.auxprotect.adapters.message;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.ChatColor;
import org.jetbrains.annotations.NotNull;

public class ColorTranslator {
    @NotNull
    public static String translateAlternateColorCodes(@NotNull String textToTranslate) {
        char[] b = textToTranslate.toCharArray();
        for (int i = 0; i < b.length - 1; i++) {
            if (b[i] == '&' && "0123456789AaBbCcDdEeFfKkLlMmNnOoRrXx".indexOf(b[i + 1]) > -1) {
                b[i] = ChatColor.COLOR_CHAR;
                b[i + 1] = Character.toLowerCase(b[i + 1]);
            }
        }
        return new String(b);
    }

    @NotNull
    public static String stripColor(@NotNull String text) {
        return text.replaceAll("[§&][0-9a-flmnorA-FLMNOR]", "");
    }

    public static String toString(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }
}
