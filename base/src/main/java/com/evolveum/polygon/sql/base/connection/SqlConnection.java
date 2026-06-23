/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.sql.base.connection;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Wrapper around JDBC Connection with auto-close support.
 * Manages transaction state and connection lifecycle.
 */
public class SqlConnection implements AutoCloseable {

    private final Connection connection;
    private boolean autoClose;
    private boolean inTransaction;

    public SqlConnection(Connection connection) {
        this.connection = connection;
        this.autoClose = true;
        try {
            this.inTransaction = !connection.getAutoCommit();
        } catch (SQLException e) {
            this.inTransaction = false;
        }
    }

    public Connection getConnection() {
        return connection;
    }

    public boolean isInTransaction() {
        return inTransaction;
    }

    public void setAutoClose(boolean autoClose) {
        this.autoClose = autoClose;
    }

    public void commit() throws SQLException {
        if (!inTransaction) {
            connection.commit();
            inTransaction = true;
        }
    }

    public void rollback() throws SQLException {
        if (inTransaction) {
            connection.rollback();
            inTransaction = false;
        }
    }

    public void setAutoCommit(boolean autoCommit) throws SQLException {
        connection.setAutoCommit(autoCommit);
        inTransaction = !autoCommit;
    }

    @Override
    public void close() throws SQLException {
        if (autoClose) {
            connection.close();
        }
    }
}