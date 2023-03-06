package dev.heliosares.auxprotect.core;

import dev.heliosares.auxprotect.adapters.config.ConfigAdapter;
import dev.heliosares.auxprotect.api.AuxProtectAPI;
import dev.heliosares.auxprotect.utils.ColorTranslate;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.function.Supplier;

public class Language {
    private static Supplier<ConfigAdapter> langSupplier;
    private static Supplier<ConfigAdapter> langDefaultSupplier;
    private static IAuxProtect plugin;
    private static ConfigAdapter lang;
    private static String c1;
    private static String c2;
    private static String c3;

    public static void load(IAuxProtect plugin, Supplier<ConfigAdapter> langSupplier, Supplier<ConfigAdapter> englishLangSupplier) throws IOException {
        Language.plugin = plugin;
        Language.langSupplier = langSupplier;
        Language.langDefaultSupplier = englishLangSupplier;
        reload();
    }

    public static void reload() throws IOException {
        lang = langSupplier.get();
        lang.load();

        int resourceVersion = lang.getDefaults().getInt("version");
        int fileVersion = lang.getInt("version");

        if (resourceVersion > 0 && resourceVersion > fileVersion) {
            AuxProtectAPI.getInstance().info("Resetting language file from v" + fileVersion + " to v" + resourceVersion);
            File newFile;
            int i = 0;
            do {
                newFile = new File(lang.getFile().getParentFile(),
                        "old/" + lang.getFile().getName().replace(".yml", "." + fileVersion + (i++ > 0 ? "." + i : "") + ".yml"));
            } while (newFile.exists());
            lang.save(newFile);
            lang.reset();
            lang.load();
        }

        if (lang != null) {
            c1 = "&" + lang.getString("color.p");
            c2 = "&" + lang.getString("color.s");
            c3 = "&" + lang.getString("color.t");

            boolean modified = false;
            ConfigAdapter englishLang = null;
            for (L l : L.values()) {
                Object line = lang.get(l.name);
                if (line == null || line instanceof String str && str.isEmpty()) {
                    if (englishLang == null) {
                        englishLang = langDefaultSupplier.get();
                        englishLang.load();
                    }
                    lang.set(l.name, englishLang.get(l.name));
                    modified = true;
                    plugin.warning("Lang file does not contain " + l.name);
                }
            }
            if (modified) lang.save();
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
        ACTIONS,
        ACTION_DISABLED,
        BACKUP_SQLITEONLY,
        COMMAND__AP__BACKUP_CREATED("file"),
        COMMAND__AP__DEVELOPED_BY,
        COMMAND__AP__HELP,
        COMMAND__AP__CONFIG_RELOADED,
        COMMAND__AP__LANG_RELOADED("locale"),
        COMMAND__AP__LANG_NOT_FOUND("file"),
        COMMAND__CLAIMINV__CANCELLED,
        COMMAND__CLAIMINV__CANCELLED_OTHER("target", "optional_s"),
        COMMAND__CLAIMINV__CLAIM_BUTTON__HOVER,
        COMMAND__CLAIMINV__CLAIM_BUTTON__LABEL,
        COMMAND__CLAIMINV__HEADER,
        COMMAND__CLAIMINV__OTHERHASNONE,
        COMMAND__CLAIMINV__YOUHAVENONE,
        COMMAND__HELP,
        COMMAND__INV__FORCE_RECOVERED("admin", "target", "time"),
        COMMAND__INV__ITEM_VIEWER,
        COMMAND__INV__NOTIFY_PLAYER("admin", "time"),
        COMMAND__INV__NOTIFY_PLAYER_ENSURE_ROOM,
        COMMAND__INV__RECOVERED("admin", "target", "optional_s", "time"),
        COMMAND__INV__SUCCESS("target", "optional_s"),
        COMMAND__LOOKUP__ACTION_NEGATE,
        COMMAND__LOOKUP__ACTION_NONE,
        COMMAND__LOOKUP__ACTION_PERM,
        COMMAND__LOOKUP__COUNT("results"),
        COMMAND__LOOKUP__INCOMPATIBLE_TABLES,
        COMMAND__LOOKUP__INVALID_TIME_PARAMETER("specifier"),
        COMMAND__LOOKUP__LOOKING,
        COMMAND__LOOKUP__NODATA,
        COMMAND__LOOKUP__NOPAGE,
        COMMAND__LOOKUP__NORESULTS,
        COMMAND__LOOKUP__NO_RESULTS_SELECTED,
        COMMAND__LOOKUP__PAGE_FOOTER("page_number", "page_count", "entry_count"),
        COMMAND__LOOKUP__PLAYBACK__STARTING,
        COMMAND__LOOKUP__PLAYBACK__STOPPED,
        COMMAND__LOOKUP__PLAYBACK__TOOLONG("limit"),
        COMMAND__LOOKUP__PLAYTIME__NOUSER,
        COMMAND__LOOKUP__PLAYTIME__TOOMANYUSERS,
        COMMAND__LOOKUP__RATING_WRONG,
        COMMAND__LOOKUP__TOOMANY("count", "max"),
        COMMAND__LOOKUP__UNKNOWN_WORLD("world"),
        COMMAND__PURGE__COMPLETE_COUNT("rows"),
        COMMAND__PURGE__ERROR,
        COMMAND__PURGE__NOPURGE,
        COMMAND__PURGE__NOTVACUUM("time"),
        COMMAND__PURGE__PURGING("table"),
        COMMAND__PURGE__SKIPAUTO("time"),
        COMMAND__PURGE__TABLE,
        COMMAND__PURGE__TIME,
        COMMAND__PURGE__UIDS,
        COMMAND__PURGE__VACUUM,
        COMMAND__SAVEINV__SUCCESS("target", "optional_s", "time"),
        COMMAND__SAVEINV__TOOSOON,
        DATABASE_BUSY,
        ERROR,
        INACTIVE_ALERT("user", "inactive_minutes", "total_minutes"),
        INVALID_NOTENOUGH,
        INVALID_PARAMETER("invalid_parameter"),
        INVALID_SYNTAX,
        INV_RECOVER_MENU__BUTTON__CLOSE,
        INV_RECOVER_MENU__BUTTON__ENDER_CHEST,
        INV_RECOVER_MENU__BUTTON__FORCE__HOVER,
        INV_RECOVER_MENU__BUTTON__FORCE__LABEL,
        INV_RECOVER_MENU__BUTTON__RECOVER__HOVER,
        INV_RECOVER_MENU__BUTTON__RECOVER__LABEL,
        INV_RECOVER_MENU__BUTTON__XP__ERROR,
        INV_RECOVER_MENU__BUTTON__XP__HAD("xp"),
        INV_RECOVER_MENU__ENDER_HEADER("target", "optional_s", "time"),
        INV_RECOVER_MENU__MAIN_HEADER("target", "optional_s", "time"),
        INV_RECOVER_MENU__XP_ERROR,
        LOOKUP_PLAYERNOTFOUND("target"),
        LOOKUP_UNKNOWNACTION("action"),
        NOTPLAYERERROR,
        NO_PERMISSION,
        NO_PERMISSION_FLAG,
        NO_PERMISSION_NODE("node"),
        PLAYERNOTFOUND,
        PROTOCOLLIB_NOT_LOADED,
        RESULTS__CLICK_TO_COPY,
        RESULTS__CLICK_TO_COPY_TIME("time"),
        RESULTS__CLICK_TO_VIEW,
        RESULTS__HEADER,
        RESULTS__PAGE__FIRST,
        RESULTS__PAGE__PREVIOUS,
        RESULTS__PAGE__NEXT,
        RESULTS__PAGE__LAST,
        RESULTS__TIME("time"),
        RESULTS__TIME_NOW,
        RESULTS__VIEW,
        RESULTS__VIEW_INV,
        UNKNOWN_SUBCOMMAND,
        UPDATE("current_version", "new_version"),
        XRAY_ALREADY_RATED,
        XRAY_CLICK_TO_CHANGE,
        XRAY_DONE,
        XRAY_NOTFOUND,
        XRAY_RATE_NOCHANGE,
        XRAY_RATE_WRITTEN,
        XRAY_TOOMANY,
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
            return translate();
        }

        public String translate(Object... format) {
            return translateSubcategory(null, format);
        }

        public String translateSubcategory(String subcategory, @Nullable Object... format) {
            String message = null;
            try {
                String name = this.name;
                if (subcategory != null && subcategory.length() > 0) {
                    name += "." + subcategory.toLowerCase();
                }
                if (lang != null && !lang.isNull()) {
                    message = lang.getString(name);
                }
                if (message == null) throw new IllegalArgumentException("Message not found");
                message = convert(message);


                if (format != null) {
                    if (this.format.length != format.length) {
                        throw new IllegalArgumentException("Mismatched format lengths " + this.format.length + "!=" + format.length);
                    }
                    for (int i = 0; i < format.length; i++) {
                        String key = '<' + this.format[i] + '>';
                        String var = format[i] == null ? "null" : format[i].toString();

                        message = message.replace(key, var);
                    }
                }
                return message.replace("\\n", "\n");
            } catch (IllegalArgumentException e) {
                plugin.print(e);
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
            String name = this.name;
            if (subcategory != null && subcategory.length() > 0) {
                name += "." + subcategory.toLowerCase();
            }
            if (lang != null && !lang.isNull()) {
                message = lang.getStringList(name);
            }

            if (message == null || message.size() == 0) {
                return null;
            }
            return message.stream().map(Language::convert).toList();
        }
    }

    public static String getOptionalS(String name) {
        if (name == null) return "";
        if (name.toLowerCase().endsWith("s")) return "";
        return "s";
    }
}
