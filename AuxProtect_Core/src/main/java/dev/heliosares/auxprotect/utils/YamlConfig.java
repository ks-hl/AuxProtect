package dev.heliosares.auxprotect.utils;

import jakarta.annotation.Nullable;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.nodes.Tag;
import org.yaml.snakeyaml.representer.Representer;
import org.yaml.snakeyaml.resolver.Resolver;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.regex.Pattern;

public class YamlConfig {
    @Nullable
    private final YamlConfig parent;
    @Nullable
    private final File file;
    private final Supplier<InputStream> inputStreamSupplier;
    private DataMap data;
    private boolean unsavedChanges;

    public YamlConfig() {
        this(null, null);
    }

    public YamlConfig(File file, @Nullable Supplier<InputStream> inputStreamSupplier) {
        this(null, file, inputStreamSupplier);
    }

    private YamlConfig(YamlConfig parent) {
        this(parent, null, null);
        initializeDataMap();
    }

    private YamlConfig(@Nullable YamlConfig parent, @Nullable File file, @Nullable Supplier<InputStream> inputStreamSupplier) {
        if (parent == null && file == null && inputStreamSupplier == null) {
            throw new NullPointerException("file and inputStreamSupplier cannot both be null.");
        }
        this.parent = parent;
        this.file = file;
        this.inputStreamSupplier = inputStreamSupplier;
    }

    public void initializeDataMap() {
        this.data = new DataMap();
    }

    public YamlConfig load() throws IOException {
        if (file == null) {
            assert inputStreamSupplier != null; // verified during construction
            return loadFromStream(inputStreamSupplier.get());
        }
        if (!file.exists()) {
            if (inputStreamSupplier != null) {
                file.toPath().getParent().toFile().mkdirs();
                Files.copy(inputStreamSupplier.get(), file.toPath());
            } else {
                throw new FileNotFoundException();
            }
        }
        try (FileInputStream fileInputStream = new FileInputStream(file)) {
            return loadFromStream(fileInputStream);
        }
    }

    private YamlConfig loadFromStream(InputStream in) {
        Yaml yaml = new Yaml(new Constructor(new LoaderOptions()), new Representer(new DumperOptions()), new DumperOptions(), new Resolver() {
            @Override
            public void addImplicitResolver(Tag tag, Pattern regexp, String first, int limit) {
                if (tag.equals(Tag.BOOL)) {
                    regexp = Pattern.compile("^(?:true|True|TRUE|false|False|FALSE)$");
                    first = "tTfF";
                }
                super.addImplicitResolver(tag, regexp, first, limit);
            }
        });
        this.data = new DataMap(yaml.load(in));
        return this;
    }

    public void save() throws IOException {
        saveCopyTo(file);
        unsavedChanges = false;
    }

    public void saveCopyTo(File file) throws IOException {
        Objects.requireNonNull(file, "No file to save to");
        final DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);
        try (FileWriter writer = new FileWriter(file)) {
            new Yaml(options).dump(data, writer);
        }
    }

    private Optional<Object> flatGet(String key) {
        if (data == null) return Optional.empty();
        return Optional.ofNullable(data.get(key));
    }

    public Optional<Object> get(String key) {
        return get(key, o -> true, o -> o);
    }

    public Optional<String> getString(String key) {
        return get(key, s -> s instanceof String, o -> (String) o);
    }

    public String getStringOrSet(String key, String def) {
        return getOrSet(key, s -> s instanceof String, o -> (String) o, def);
    }

    public Optional<Integer> getInt(String key) {
        return get(key, s -> s instanceof Integer, o -> (Integer) o);
    }

    public Integer getIntOrSet(String key, Integer def) {
        return getOrSet(key, s -> s instanceof Integer, o -> (Integer) o, def);
    }

    public Optional<Long> getLong(String key) {
        return get(key, s -> s instanceof Long, o -> (Long) o);
    }

    public Long getLongOrSet(String key, Long def) {
        return getOrSet(key, s -> s instanceof Long, o -> (Long) o, def);
    }

    public Optional<Double> getDouble(String key) {
        return get(key, s -> s instanceof Double, o -> (Double) o);
    }

    public Double getDoubleOrSet(String key, Double def) {
        return getOrSet(key, s -> s instanceof Double, o -> (Double) o, def);
    }

    public Optional<Boolean> getBoolean(String key) {
        return get(key, s -> s instanceof Boolean, o -> (Boolean) o);
    }

    public Boolean getBooleanOrSet(String key, Boolean def) {
        return getOrSet(key, s -> s instanceof Boolean, o -> (Boolean) o, def);
    }

    public boolean containsKey(String key) {
        return data.containsKey(key);
    }

    public Optional<List<String>> getStringList(String key) {
        return get(key, o -> o instanceof Collection<?>, o -> {
            List<String> outList = new ArrayList<>();
            for (Object element : (Collection<?>) o) {
                outList.add(Objects.toString(element));
            }
            return outList;
        });
    }

    public Optional<YamlConfig> getSection(String key) {
        return get(key, o -> o instanceof DataMap, o -> {
            YamlConfig config = new YamlConfig(this);
            config.data = (DataMap) o;
            return config;
        });
    }

    public YamlConfig getOrCreateSection(String key) {
        var opt = getSection(key);
        if (opt.isPresent()) return opt.get();
        YamlConfig out = new YamlConfig(this);
        set(key, out);
        return out;
    }

    public void set(String key, Object value) {
        checkKey(key);

        if (value instanceof YamlConfig yamlConfig) {
            set(key, yamlConfig.data);
            return;
        }

        boolean change = false;
        if (key.contains(".")) {
            int index = key.indexOf(".");
            String sectionKey = key.substring(0, index);
            var opt = getSection(sectionKey);
            YamlConfig section;
            if (opt.isEmpty()) {
                if (value == null) return;
                section = new YamlConfig(this);
                change = !Objects.equals(data.put(sectionKey, section.data), section.data);
            } else section = opt.get();

            key = key.substring(index + 1);
            section.set(key, value);
            if (section.isEmpty()) {
                change = data.remove(sectionKey) != null;
            }
        } else {
            if (value == null) {
                change = data.remove(key) != null;
            } else {
                change = !Objects.equals(data.put(key, value), value);
            }
        }
        if (change) setUnsavedChanges();
    }

    public boolean hasUnsavedChanges() {
        return unsavedChanges;
    }

    public Set<String> getKeys(boolean deep) {
        Set<String> keys = new HashSet<>(data.keySet());
        if (!deep) {
            keys.removeIf(key -> key.contains("."));
        }
        return Collections.unmodifiableSet(keys);
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof YamlConfig yamlConfig)) return false;
        return Objects.equals(data, yamlConfig.data);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(data);
    }

    @Override
    public String toString() {
        var data = this.data;
        if (data == null) return "YamlConfig[null]";
        StringBuilder builder = new StringBuilder();
        toString(builder, 0, data);
        return builder.toString();
    }

    private <T> Optional<T> get(String key, Predicate<Object> predicateInstanceOf, Function<Object, T> cast) {
        checkKey(key);
        int index = key.indexOf(".");
        if (index > 0) {
            return getSection(key.substring(0, index)).flatMap(s -> s.get(key.substring(index + 1), predicateInstanceOf, cast));
        }
        return flatGet(key).filter(predicateInstanceOf).map(cast);
    }

    private <T> T getOrSet(String key, Predicate<Object> predicateInstanceOf, Function<Object, T> cast, T def) {
        Optional<T> opt = get(key, predicateInstanceOf, cast);
        return opt.orElseGet(() -> {
            set(key, def);
            return def;
        });
    }

    private void checkKey(String key) {
        if (key == null) throw new IllegalArgumentException("Key can not be null");
        if (key.endsWith(".") || key.startsWith("."))
            throw new IllegalArgumentException("Key can not start or end with '.'");
        if (key.isBlank()) throw new IllegalArgumentException("Key can not be blank");
    }

    private void setUnsavedChanges() {
        if (parent != null) parent.setUnsavedChanges();
        unsavedChanges = true;
    }

    private void toString(StringBuilder builder, int indent, Map<?, ?> data) {
        if (data == null) return;
        for (Map.Entry<?, ?> entry : data.entrySet()) {
            builder.append(" ".repeat(indent));
            builder.append(entry.getKey());
            builder.append(": ");
            if (entry.getValue() instanceof DataMap map) {
                builder.append("\n");
                toString(builder, indent + 2, map);
            } else {
                builder.append(entry.getValue()).append("\n");
            }
        }
    }

    public static final class DataMap extends LinkedHashMap<String, Object> {
        public DataMap(LinkedHashMap<?, ?> handle) {
            for (Map.Entry<?, ?> entry : handle.entrySet()) {
                Object val = entry.getValue();
                if (entry.getValue() instanceof LinkedHashMap<?, ?> linkedHashMap) {
                    val = new DataMap(linkedHashMap);
                }
                put((String) entry.getKey(), val);
            }
        }

        public DataMap() {
        }
    }

    public Map<String, Object> getData() {
        return Collections.unmodifiableMap(data);
    }

    public boolean isEmpty() {
        return data.isEmpty();
    }

    public void delete() {
        //noinspection ResultOfMethodCallIgnored
        Objects.requireNonNull(file).delete();
    }

    public @Nullable File getFile() {
        return file;
    }
}
