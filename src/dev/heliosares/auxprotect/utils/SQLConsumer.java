package dev.heliosares.auxprotect.utils;

import java.sql.Connection;
import java.sql.SQLException;

@FunctionalInterface
public interface SQLConsumer {
    void accept(Connection connection) throws SQLException;
}
