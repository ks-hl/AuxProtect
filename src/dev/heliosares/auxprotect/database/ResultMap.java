package dev.heliosares.auxprotect.database;

import java.io.IOException;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Types;
import java.util.*;

public class ResultMap {
    private final Map<String, Integer> labels;
    private final List<Result> results;

    public ResultMap(SQLManager sql, ResultSet rs) throws SQLException, IOException {
        final ResultSetMetaData meta = rs.getMetaData();
        final int columnCount = meta.getColumnCount();
        final int[] types = new int[columnCount];

        {
            final HashMap<String, Integer> labels = new HashMap<>();
            for (int i = 0; i < columnCount; i++) {
                labels.put(meta.getColumnName(i + 1), i + 1);
                types[i] = meta.getColumnType(i + 1);
            }
            this.labels = Collections.unmodifiableMap(labels);
        }

        List<Result> results_ = new ArrayList<>();
        while (rs.next()) {
            final List<Object> values = new ArrayList<>();
            for (int column = 1; column <= columnCount; ++column) {
                Object value;
                if (types[column - 1] == Types.BLOB) {
                    value = sql.getBlob(rs, column);
                } else {
                    value = rs.getObject(column);
                }
                if (rs.wasNull()) value = null;
                values.add(value);
            }
            results_.add(new Result(this, Collections.unmodifiableList(values)));
        }
        results = Collections.unmodifiableList(results_);
    }

    public <T> T getFirstElement(Class<T> clazz) throws NoSuchElementException {
        if (results.isEmpty()) throw new NoSuchElementException();
        return results.get(0).getValue(clazz, 1);
    }

    public <T> T getFirstElementOrNull(Class<T> clazz) {
        try {
            return getFirstElement(clazz);
        } catch (Exception ignored) {
            return null;
        }
    }


    public Map<String, Integer> getLabels() {
        return labels;
    }

    public List<Result> getResults() {
        return results;
    }

    public static class Result {
        private final ResultMap parent;
        private final List<Object> values;

        private Result(ResultMap parent, List<Object> values) {
            this.parent = parent;
            this.values = values;
        }

        public <T> T getValue(Class<T> clazz, int index) throws ClassCastException, IndexOutOfBoundsException {
            return clazz.cast(getValue(index));
        }

        public Object getValue(int index) throws IndexOutOfBoundsException {
            index--;
            if (index >= values.size())
                throw new IndexOutOfBoundsException("Specified index " + index + " for size " + values.size());
            return values.get(index - 1);
        }

        public <T> T getValue(Class<T> clazz, String columnName) throws ClassCastException, NoSuchElementException {
            return clazz.cast(getValue(columnName));
        }

        public Object getValue(String columnName) throws NoSuchElementException {
            Integer index = parent.labels.get(columnName);
            if (index == null) throw new NoSuchElementException("Unknown column: " + columnName);
            return getValue(index);
        }
    }
}
