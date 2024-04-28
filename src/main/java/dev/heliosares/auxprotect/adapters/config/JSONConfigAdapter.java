package dev.heliosares.auxprotect.adapters.config;

import dev.heliosares.auxprotect.core.PlatformType;
import org.json.JSONObject;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Scanner;
import java.util.Set;

public class JSONConfigAdapter extends ConfigAdapter {

    JSONObject config;

    public JSONConfigAdapter(File parent, String path) {
        super(parent, path, null);
    }

    @Override
    public void load() throws IOException {
        super.load();
        getFile().createNewFile();

        StringBuilder content = new StringBuilder();
        try (Scanner scanner = new Scanner(getFile())) {
            while (scanner.hasNext()) {
                if (!content.isEmpty()) content.append("\n");
                content.append(scanner.nextLine());
            }
        }
        if (content.isEmpty()) config = new JSONObject();
        else config = new JSONObject(content.toString());
    }

    @Override
    public void save() throws IOException {
        if (file == null) return;

        save(file.get());
    }

    @Override
    public void save(File file) throws IOException {
        if (config != null) {
            try (FileWriter writer = new FileWriter(file, false)) {
                writer.write(config.toString(2));
                writer.flush();
            }
        }
    }

    @Override
    public String getString(String key) {
        return getString(key, null);
    }

    @Override
    public String getString(String key, String def) {
        return (String) get(key, def);
    }

    @Override
    public long getLong(String key) {
        return getLong(key, -1);
    }

    @Override
    public long getLong(String key, long def) {
        return (Long) get(key, def);
    }

    @Override
    public boolean getBoolean(String key) {
        return getBoolean(key, false);
    }

    @Override
    public boolean getBoolean(String key, boolean def) {
        return (Boolean) get(key, def);
    }

    @Override
    public int getInt(String path) {
        return getInt(path, -1);
    }

    @Override
    public int getInt(String key, int def) {
        return (Integer) get(key, def);
    }

    @Override
    public Object get(String key) {
        return get(key, null);
    }

    @Override
    public Object get(String key, Object def) {
        return get(config, key, def);
    }

    private static Object get(JSONObject json, final String key, Object def) {
        String key_;
        boolean recur = key.contains(".");
        if (recur) {
            key_ = key.split("\\.")[0];
        } else {
            key_ = key;
        }
        if (!json.has(key_)) return def;
        if (recur) {
            return get(json.getJSONObject(key_), key.substring(key_.length() + 1), def);
        }
        return json.get(key_);
    }

    @Override
    public void set(String key, Object value) {
        config.put(key, value);
    }

    @Override
    public boolean isSection(String key) {
        return config.has(key) && config.get(key) instanceof JSONObject;
    }

    @Override
    public PlatformType getPlatform() {
        return PlatformType.NONE;
    }

    @Override
    public Set<String> getKeys(boolean recur) {
        return config.keySet();
    }

    @Override
    public Set<String> getKeys(String key, boolean recur) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isNull() {
        return false;
    }

    @Override
    public List<String> getStringList(String key) {
        return config.getJSONArray(key).toList().stream().map(Object::toString).toList();
    }

    @Override
    protected ConfigAdapter fromInputStream(InputStream stream) {
        throw new UnsupportedOperationException();
    }

    public boolean isEmpty() {
        return config.isEmpty();
    }
}
