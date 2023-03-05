package dev.heliosares.auxprotect.core;

import dev.heliosares.auxprotect.adapters.ConfigAdapter;
import dev.heliosares.auxprotect.api.AuxProtectAPI;
import dev.heliosares.auxprotect.utils.ColorTranslate;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class Language {
    private static Supplier<ConfigAdapter> langSupplier;
    private static IAuxProtect plugin;
    private static ConfigAdapter lang;
    private static String c1;
    private static String c2;
    private static String c3;

    public static final int VERSION = 4;

    public static void load(IAuxProtect plugin, Supplier<ConfigAdapter> langSupplier) throws IOException {
        Language.plugin = plugin;
        Language.langSupplier = langSupplier;
        reload();
    }

    public static void reload() throws IOException {
        lang = langSupplier.get();
        lang.load();

        if (lang.getInt("version") != VERSION) {
            AuxProtectAPI.getInstance().info("Resetting language file to version " + VERSION);
            lang.reset();
            lang.load();
        }

        if (lang != null) {
            c1 = "&" + lang.getString("color.p");
            c2 = "&" + lang.getString("color.s");
            c3 = "&" + lang.getString("color.t");
        }
    }

    public static String getLocale() {
        try {
            return lang.getFile().getName().split("\\.")[0];
        } catch (Exception e) {
            return null;
        }
    }

    public static String translate(L l, Object... format) {
        return l.translate(format);
    }

    public static String convert(String s) {
        if (c1 != null)
            s = s.replace("&p", c1);
        if (c2 != null)
            s = s.replace("&s", c2);
        if (c3 != null)
            s = s.replace("&t", c3);
        s = s.replace("$prefix", plugin.getCommandAlias());
        return ColorTranslate.cc(s);
    }

    public enum L {
        UPDATE("current_version", "new_version"), //
        NO_PERMISSION, //
        NO_PERMISSION_NODE("node"), //
        NO_PERMISSION_FLAG, //
        INVALID_PARAMETER("invalid_parameter"), //
        INVALID_SYNTAX, //
        INVALID_NOTENOUGH, //
        ERROR, //
        ACTION_DISABLED, //
        NOTPLAYERERROR, //
        COMMAND__LOOKUP__UNKNOWN_WORLD("world"), //
        COMMAND__LOOKUP__LOOKING, //
        COMMAND__LOOKUP__NORESULTS, //
        COMMAND__LOOKUP__COUNT("results"), //
        COMMAND__LOOKUP__PAGE_FOOTER("page_number", "page_count", "entry_count"), //
        COMMAND__LOOKUP__NO_RESULTS_SELECTED, //
        COMMAND__LOOKUP__NOPAGE, //
        COMMAND__LOOKUP__TOOMANY("count", "max"), //
        COMMAND__LOOKUP__INCOMPATIBLE_TABLES, //
        COMMAND__LOOKUP__ACTION_NEGATE, //
        COMMAND__LOOKUP__ACTION_PERM, //
        COMMAND__LOOKUP__ACTION_NONE, //
        COMMAND__LOOKUP__RATING_WRONG, //
        COMMAND__LOOKUP__NODATA, //
        COMMAND__LOOKUP__PLAYBACK__STARTING, //
        COMMAND__LOOKUP__PLAYBACK__STOPPED, //
        COMMAND__LOOKUP__PLAYBACK__TOOLONG("limit"), //
        COMMAND__LOOKUP__PLAYTIME__NOUSER, //
        COMMAND__LOOKUP__PLAYTIME__TOOMANYUSERS, //
        COMMAND__LOOKUP__INVALID_TIME_PARAMETER("specifier"), //
        WATCH_NONE, //
        WATCH_ING, //
        WATCH_REMOVED, //
        WATCH_NOW, //
        COMMAND__PURGE__PURGING("table"), //
        COMMAND__PURGE__UIDS, //
        COMMAND__PURGE__VACUUM, //
        COMMAND__PURGE__NOTVACUUM("time"), //
        COMMAND__PURGE__COMPLETE_COUNT("rows"), //
        COMMAND__PURGE__ERROR, //
        COMMAND__PURGE__TIME, //
        COMMAND__PURGE__TABLE, //
        COMMAND__PURGE__NOPURGE, //
        COMMAND__PURGE__SKIPAUTO("time"), //
        BACKUP_SQLITEONLY, //
        UNKNOWN_SUBCOMMAND, //
        COMMAND__HELP, //
        PLAYERNOTFOUND, //
        LOOKUP_PLAYERNOTFOUND("target"), //
        LOOKUP_UNKNOWNACTION("action"), //
        XRAY_RATE_NOCHANGE, //
        XRAY_RATE_WRITTEN, //
        XRAY_DONE, //
        XRAY_NOTFOUND, //
        XRAY_TOOMANY, //
        XRAY_ALREADY_RATED, //
        XRAY_CLICK_TO_CHANGE, //
        INACTIVE_ALERT("user", "inactive_minutes", "total_minutes"), //
        DATABASE_BUSY, //
        ACTIONS, //
        COMMAND__SAVEINV__SUCCESS("target", "optional_s", "time"), //
        COMMAND__SAVEINV__TOOSOON, //
        COMMAND__INV__RECOVERED("admin", "target", "optional_s", "time"), //
        COMMAND__INV__FORCE_RECOVERED("admin", "target", "time"), //
        COMMAND__INV__SUCCESS("target", "optional_s"),
        COMMAND__INV__NOTIFY_PLAYER("admin", "time"),
        COMMAND__INV__NOTIFY_PLAYER_ENSURE_ROOM,
        COMMAND__INV__ITEM_VIEWER,
        COMMAND__CLAIMINV__CANCELLED, //
        COMMAND__CLAIMINV__CANCELLED_OTHER("target", "optional_s"), //
        COMMAND__CLAIMINV__YOUHAVENONE, //
        COMMAND__CLAIMINV__OTHERHASNONE, //
        COMMAND__CLAIMINV__HEADER, //
        COMMAND__CLAIMINV__CLAIM_BUTTON__LABEL, //
        COMMAND__CLAIMINV__CLAIM_BUTTON__HOVER, //
        PROTOCOLLIB_NOT_LOADED, //

        INV_RECOVER_MENU__MAIN_HEADER("target", "optional_s", "time"),
        INV_RECOVER_MENU__ENDER_HEADER("target", "optional_s", "time"),
        INV_RECOVER_MENU__XP_ERROR,
        INV_RECOVER_MENU__BUTTON__FORCE__LABEL,
        INV_RECOVER_MENU__BUTTON__FORCE__HOVER,
        INV_RECOVER_MENU__BUTTON__RECOVER__LABEL,
        INV_RECOVER_MENU__BUTTON__RECOVER__HOVER,
        INV_RECOVER_MENU__BUTTON__CLOSE,
        INV_RECOVER_MENU__BUTTON__ENDER_CHEST,
        INV_RECOVER_MENU__BUTTON__XP__HAD("xp"),
        INV_RECOVER_MENU__BUTTON__XP__ERROR,
        ;

        public final String name;
        private final String[] format;

        L(@Nullable String... format) {
            this.format = format;
            this.name = super.toString().replace("__", ".").replace("_", "-").toLowerCase();
            if (format != null) for (String line : format) {
                if (!line.matches("[a-zA-Z0-9_-]+")) {
                    throw new IllegalArgumentException("Non alphanumeric translation key: " + line + " in " + name);
                }
            }
        }

        @Override
        public String toString() {
            return name;
        }

        public String translate(Object... format) {
            return translateSubcategory(null, format);
        }

        public String translateSubcategory(String subcategory, @Nullable Object... format) {
            String message = null;
            try {
                String name = toString();
                if (subcategory != null && subcategory.length() > 0) {
                    name += "." + subcategory.toLowerCase();
                }
                if (lang != null && !lang.isNull()) {
                    message = lang.getString(name);
                }
                if (message == null) throw new IllegalArgumentException();
                message = convert(message);

                if (format != null) {
                    for (int i = 0; i < format.length; i++) {
                        String key = '<' + this.format[i] + '>';
                        String var = format[i] == null ? "null" : format[i].toString();

                        message = message.replace(key, var);
                    }
                }
                return message.replace("\\n", "\n");
            } catch (IllegalArgumentException e) {
                StringBuilder out = new StringBuilder("[lang:" + name);
                if (format != null) {
                    for (Object part : format) {
                        out.append(", ").append(part);
                    }
                }
                return out + "]";
            }
        }

        public List<String> translateList() {
            return translateSubcategoryList(null);
        }

        public List<String> translateSubcategoryList(String subcategory) {
            List<String> message = null;
            String name = toString();
            if (subcategory != null && subcategory.length() > 0) {
                name += "." + subcategory.toLowerCase();
            }
            if (lang != null && !lang.isNull()) {
                message = lang.getStringList(name);
            }

            if (message == null || message.size() == 0) {
                return null;
            }
            return message.stream().map(Language::convert).collect(Collectors.toList());
        }
    }

    public static String getOptionalS(String name) {
        if (name == null) return "";
        if (name.toLowerCase().endsWith("s")) return "";
        return "s";
    }
}
