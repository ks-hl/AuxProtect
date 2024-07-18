package dev.heliosares.auxprotect.utils;

import java.sql.Connection;

@FunctionalInterface
public interface SQLFunctionWithException<T> {
    T apply(Connection connection) throws Exception;
}
