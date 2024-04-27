package dev.heliosares.auxprotect.adapters.config;

import dev.heliosares.auxprotect.core.PlatformType;
import dev.kshl.kshlib.yaml.YamlConfig;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public class VelocityConfigAdapter extends ConfigAdapter {
    private YamlConfig config;

    public VelocityConfigAdapter(File parent, String path, @Nullable Function<String, InputStream> defaults, boolean createBlank) {
        super(parent, path, defaults, createBlank);
        this.config = new YamlConfig();
    }

    public VelocityConfigAdapter(InputStream in) {
        super(in);
    }

    @Override
    public String getString(String key) {
        return getString(key, null);
    }

    @Override
    public String getString(String key, String def) {
        return config.getString(key).orElse(def);
    }

    @Override
    public long getLong(String key) {
        return getLong(key, 0);
    }

    @Override
    public long getLong(String key, long def) {
        return config.getLong(key).orElse(def);
    }

    @Override
    public boolean getBoolean(String key) {
        return getBoolean(key, false);
    }

    @Override
    public boolean getBoolean(String key, boolean def) {
        return config.getBoolean(key).orElse(def);
    }

    @Override
    public int getInt(String path) {
        return getInt(path, 0);
    }

    @Override
    public int getInt(String path, int def) {
        return config.getInt(path).orElse(def);
    }

    @Override
    public void set(String key, Object value) {
        config.set(key, value);
    }

    @Override
    public PlatformType getPlatform() {
        return PlatformType.VELOCITY;
    }

    @Override
    public Set<String> getKeys(boolean recur) {
        return config.getKeys().stream().collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public Set<String> getKeys(String key, boolean recur) {
        return config.getSection(key).map(s -> s.getKeys().stream().collect(Collectors.toUnmodifiableSet())).orElse(Set.of());
    }

    @Override
    public Object get(String key) {
        return config.get(key);
    }

    @Override
    public Object get(String key, Object def) {
        return config.get(key).orElse(null);
    }

    @Override
    public boolean isSection(String key) {
        return config.getSection(key).isPresent();
    }

    @Override
    public void save() throws IOException {
        if (file == null) return;

        save(file.get());
    }

    @Override
    public void save(File file) throws IOException {
        if (config != null) {
            config.save(file);
        }
    }

    @Override
    public void load() throws IOException {
        config.load(Objects.requireNonNull(file).get(), defaults == null ? null : defaults.apply(path));
    }

    @Override
    public boolean isNull() {
        return config == null;
    }

    @Override
    public List<String> getStringList(String key) {
        return config.getStringList(key).orElse(List.of());
    }

    @Override
    protected VelocityConfigAdapter fromInputStream(InputStream stream) {
        return new VelocityConfigAdapter(stream);
    }
}
