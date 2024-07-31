package dev.heliosares.auxprotect.core;

import dev.heliosares.auxprotect.adapters.message.ColorTranslator;
import dev.heliosares.auxprotect.api.AuxProtectAPI;
import dev.heliosares.auxprotect.utils.YamlConfig;
import jakarta.annotation.Nullable;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

public class Language {
    private static Supplier<YamlConfig> langSupplier;
    private static Supplier<YamlConfig> defaultLangSupplier;
    private static IAuxProtect plugin;
    private static YamlConfig lang;
    private static YamlConfig defaultLang;
    private static String c1;
    private static String c2;
    private static String c3;

    public static void load(IAuxProtect plugin, Supplier<YamlConfig> langSupplier, Supplier<YamlConfig> defaultLangSupplier) throws IOException {
        Language.plugin = plugin;
        Language.langSupplier = langSupplier;
        Language.defaultLangSupplier = defaultLangSupplier;
        reload();
    }

    public static void reload() throws IOException {
        defaultLang = defaultLangSupplier.get();
        defaultLang.load();

        lang = langSupplier.get();
        lang.load();

        int resourceVersion = defaultLang.getInt("version").orElse(0);
        int fileVersion = lang.getInt("version").orElse(0);

        if (resourceVersion > 0 && resourceVersion > fileVersion) {
            AuxProtectAPI.info("Resetting language file from v" + fileVersion + " to v" + resourceVersion);
            lang.delete();
            lang.load();
        }

        if (lang != null) {
            c1 = "&" + lang.getString("color.p").orElse("");
            c2 = "&" + lang.getString("color.s").orElse("");
            c3 = "&" + lang.getString("color.t").orElse("");

            for (L l : L.values()) {
                if (lang.get(l.name).filter(o ->
                        o instanceof String str && !str.isEmpty() ||
                                o instanceof Collection<?> c && !c.isEmpty() ||
                                o instanceof YamlConfig.DataMap dataMap && !dataMap.isEmpty()
                ).isEmpty()) {
                    lang.set(l.name, defaultLang.get(l.name).orElse(null));
                    plugin.warning("Lang file does not contain " + l.name);
                }
            }
            if (lang.hasUnsavedChanges()) lang.save();
        }

    }

    public static String getLocale() {
        try {
            return Objects.requireNonNull(lang.getFile()).getName().split("\\.")[0];
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
        return ColorTranslator.translateAlternateColorCodes(s);
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
        COMMAND__HELP__HEADER("command"),
        COMMAND__INV__FORCE_RECOVERED("admin", "target", "optional_s", "time"),
        COMMAND__INV__ITEM_VIEWER,
        COMMAND__INV__NOTIFY_PLAYER("admin", "time"),
        COMMAND__INV__NOTIFY_PLAYER_CLAIM_BUTTON,
        COMMAND__INV__NOTIFY_PLAYER_CLAIM_HOVER,
        COMMAND__INV__NOTIFY_PLAYER_CLAIM_ALT,
        COMMAND__INV__NOTIFY_PLAYER_ENSURE_ROOM,
        COMMAND__INV__NOTIFY_PLAYER_WAITING,
        COMMAND__INV__RECOVERED("admin", "target", "optional_s", "time"),
        COMMAND__INV__SUCCESS("target", "optional_s"),
        COMMAND__LOOKUP__ACTION_NEGATE,
        COMMAND__LOOKUP__ACTION_NONE,
        COMMAND__LOOKUP__ACTION_PERM("node"),
        COMMAND__LOOKUP__COUNT("results"),
        COMMAND__LOOKUP__INCOMPATIBLE_TABLES,
        COMMAND__LOOKUP__INVALID_TIME_PARAMETER("specifier"),
        COMMAND__LOOKUP__LOOKING,
        COMMAND__LOOKUP__NODATA,
        COMMAND__LOOKUP__NOPAGE,
        COMMAND__LOOKUP__NORESULTS,
        COMMAND__LOOKUP__NO_RESULTS_SELECTED,
        COMMAND__LOOKUP__PAGE_FOOTER("page_number", "page_count", "entry_count"),
        COMMAND__LOOKUP__PAGE_FOOTER_GROUPS("page_number", "page_count", "entry_count"),
        COMMAND__LOOKUP__PLAYBACK__STARTING,
        COMMAND__LOOKUP__PLAYBACK__STOPPED,
        COMMAND__LOOKUP__PLAYBACK__TOOLONG("limit"),
        COMMAND__LOOKUP__PLAYTIME__HEADER("target", "optional_s"),
        COMMAND__LOOKUP__PLAYTIME__HOVER("time", "minutes"),
        COMMAND__LOOKUP__PLAYTIME__NOUSER,
        COMMAND__LOOKUP__PLAYTIME__TOOLONG("days"),
        COMMAND__LOOKUP__PLAYTIME__TOOMANYUSERS,
        COMMAND__LOOKUP__RATING_WRONG,
        COMMAND__LOOKUP__TOOMANY("count", "max"),
        COMMAND__LOOKUP__UNKNOWN_WORLD("world"),
        COMMAND__LOOKUP__WRONG_FLAG("flag", "table"),
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
        COMMAND__TIME__SERVER_TIME,
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
        INV_RECOVER_MENU__BUTTON__FORCE_UNAVAILABLE__HOVER,
        INV_RECOVER_MENU__BUTTON__FORCE_UNAVAILABLE__LABEL,
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
        RESULTS__GROUPING_OF("entry_count"),
        RESULTS__HEADER,
        RESULTS__PAGE__FIRST,
        RESULTS__PAGE__PREVIOUS,
        RESULTS__PAGE__NEXT,
        RESULTS__PAGE__LAST,
        RESULTS__REDACTED,
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

        L(String... format) {
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
                if (subcategory != null && !subcategory.isEmpty()) {
                    name += "." + subcategory.toLowerCase();
                }
                if (lang != null) {
                    message = lang.getString(name).orElse(null);
                }
                if (message == null && defaultLang != null) {
                    message = defaultLang.getString(name).orElse(null);
                }
                if (message == null) throw new IllegalArgumentException("Message not found: " + name);
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
                if (plugin != null) plugin.warning("Failed to find lang `" + name + "`");
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
            if (subcategory != null && !subcategory.isEmpty()) {
                name += "." + subcategory.toLowerCase();
            }
            if (lang != null) {
                message = lang.getStringList(name).orElse(null);
            }

            if (message == null || message.isEmpty()) {
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
