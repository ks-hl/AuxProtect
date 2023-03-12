package dev.heliosares.auxprotect.utils;

import java.sql.Connection;
import java.sql.SQLException;

@FunctionalInterface
public interface SQLFunction<T> {
    T apply(Connection connection) throws SQLException;
}
