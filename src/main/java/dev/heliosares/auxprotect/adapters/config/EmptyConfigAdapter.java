package dev.heliosares.auxprotect.adapters.config;

import dev.heliosares.auxprotect.core.PlatformType;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class EmptyConfigAdapter extends ConfigAdapter {
    public EmptyConfigAdapter() {
        super(null);
    }

    @Override
    public String getString(String key) {
        return null;
    }

    @Override
    public String getString(String key, String def) {
        return def;
    }

    @Override
    public long getLong(String key) {
        return -1;
    }

    @Override
    public long getLong(String key, long def) {
        return def;
    }

    @Override
    public boolean getBoolean(String key) {
        return false;
    }

    @Override
    public boolean getBoolean(String key, boolean def) {
        return def;
    }

    @Override
    public int getInt(String path) {
        return -1;
    }

    @Override
    public int getInt(String path, int def) {
        return def;
    }

    @Override
    public Object get(String key) {
        return null;
    }

    @Override
    public Object get(String key, Object def) {
        return def;
    }

    @Override
    public void set(String key, Object value) {
    }

    @Override
    public boolean isSection(String key) {
        return false;
    }

    @Override
    public Set<String> getKeys(boolean recur) {
        return new HashSet<>();
    }

    @Override
    public Set<String> getKeys(String key, boolean recur) {
        return new HashSet<>();
    }

    @Override
    public void save() {
    }

    @Override
    public void save(File file) {
    }

    @Override
    public List<String> getStringList(String key) {
        return new ArrayList<>();
    }

    @Override
    protected ConfigAdapter fromInputStream(InputStream stream) {
        return new EmptyConfigAdapter();
    }

    @Override
    public PlatformType getPlatform() {
        return null;
    }

    @Override
    public ConfigAdapter getDefaults() {
        return null;
    }

    @Override
    public boolean isNull() {
        return true;
    }
}
