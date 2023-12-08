package dev.heliosares.auxprotect.adapters.config;

import dev.heliosares.auxprotect.api.AuxProtectAPI;
import dev.heliosares.auxprotect.core.PlatformType;

import javax.annotation.Nullable;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

public abstract class ConfigAdapter {
    @Nullable
    protected final Supplier<File> file;
    @Nullable
    protected final String path;
    @Nullable
    protected final Function<String, InputStream> defaults;
    protected final boolean createBlank;

    @Nullable
    protected final InputStream in;

    public ConfigAdapter(@Nullable File parent, @Nullable String path, @Nullable Function<String, InputStream> defaults, boolean createBlank) {
        this(Objects.requireNonNull(parent), Objects.requireNonNull(path), defaults, createBlank, null);
    }

    public ConfigAdapter(InputStream in) {
        this(null, null, null, false, in);
    }

    private ConfigAdapter(@Nullable File parent, @Nullable String path, @Nullable Function<String, InputStream> defaults, boolean createBlank, @Nullable InputStream in) {
        this.file = parent == null || path == null ? null : () -> new File(parent, path);
        this.path = path;
        this.defaults = defaults;
        this.createBlank = createBlank;
        this.in = in;
    }

    public abstract String getString(String key);

    public abstract String getString(String key, String def);

    public abstract long getLong(String key);

    public abstract long getLong(String key, long def);

    public abstract boolean getBoolean(String key);

    public abstract boolean getBoolean(String key, boolean def);

    public abstract int getInt(String path);

    public abstract int getInt(String path, int def);

    public abstract Object get(String key);

    public abstract Object get(String key, Object def);

    public abstract void set(String key, Object value);

    public abstract boolean isSection(String key);

    public abstract Set<String> getKeys(boolean recur);

    public abstract Set<String> getKeys(String key, boolean recur);

    public abstract void save() throws IOException;

    public abstract void save(File file) throws IOException;

    public void load() throws IOException {
        if (file == null) return;

        File file = this.file.get();
        if (!file.getParentFile().exists())
            file.getParentFile().mkdirs();

        if (!file.exists()) {
            if (defaults != null) {
                try (InputStream in = defaults.apply(path)) {
                    if (in != null) {
                        Files.copy(in, file.toPath());
                        AuxProtectAPI.getInstance().info("Generated default " + path);
                        return;
                    }
                }
            }
            if (createBlank) {
                file.createNewFile();
                AuxProtectAPI.getInstance().info("Created " + path);
            } else {
                throw new FileNotFoundException(file.getAbsolutePath());
            }
        }
    }

    public void reset() throws IOException {
        if (file == null) return;

        File file = this.file.get();
        boolean ignored = file.delete();
        if (defaults != null) {
            try (InputStream in = defaults.apply(path)) {
                if (in != null) {
                    Files.copy(in, file.toPath());
                    AuxProtectAPI.getInstance().info("Generated default " + path);
                }
            }
        }
    }

    public abstract List<String> getStringList(String key);

    protected abstract ConfigAdapter fromInputStream(InputStream stream);

    public File getFile() {
        if (file == null) return null;
        return file.get();
    }

    public abstract PlatformType getPlatform();

    public ConfigAdapter getDefaults() throws IOException {
        if (defaults == null) return null;
        try (InputStream in = defaults.apply(path)) {
            if (in == null) return new EmptyConfigAdapter();
            ConfigAdapter out = fromInputStream(defaults.apply(path));
            out.load();
            return out;
        }
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public abstract boolean isNull();
}
