/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.sql.base.connection;

import com.querydsl.core.Tuple;
import com.querydsl.sql.SQLQuery;
import com.querydsl.sql.SQLTemplates;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Wrapper around JDBC Connection with auto-close support.
 * Manages transaction state and connection lifecycle.
 */
public class SqlConnection implements AutoCloseable {

    private final Connection connection;
    private final SQLTemplates templates;
    private boolean autoClose = true;
    private boolean inTransaction = false;

    public SqlConnection(Connection connection, SQLTemplates templates) {
        this.connection = connection;
        this.templates = templates;
        try {
            inTransaction = !connection.getAutoCommit();
        } catch (SQLException e) {
            // If we cannot determine transaction state, we assume outside transaction.
            // This means any explicit commit() will be a no-op (safe default).
            // Rollback() will still work as it always validates against the JDBC driver.
        }
    }

    /**
     * Gets the underlying JDBC Connection.
     * NOTE: Modifying this connection bypasses the wrapper's transaction tracking.
     * @return the raw JDBC Connection
     */
    public Connection getConnection() {
        return connection;
    }

    /** @deprecated Use {@link #getConnection()} instead. */
    @Deprecated
    Connection getRawConnection() {
        return connection;
    }

    public boolean isInTransaction() {
        return inTransaction;
    }

    void setAutoClose(boolean autoClose) {
        this.autoClose = autoClose;
    }

    public void commit() throws SQLException {
        if (inTransaction) {
            try {
                connection.commit();
                inTransaction = false;
            } catch (SQLException e) {
                inTransaction = true; // rollback still pending
                throw e;
            }
        }
    }

    public void rollback() throws SQLException {
        if (inTransaction) {
            try {
                connection.rollback();
                inTransaction = false;
            } catch (SQLException e) {
                inTransaction = true; // rollback failed, still in transaction
                throw e;
            }
        }
    }

    public void setAutoCommit(boolean autoCommit) throws SQLException {
        connection.setAutoCommit(autoCommit);
        inTransaction = !autoCommit;
    }

    public SQLQuery<Tuple> newQuery() {
        return new SQLQuery<>(connection, templates);
    }

    @Override
    public void close() {
        if (autoClose) {
            try {
                connection.close();
            } catch (SQLException e) {
                // Swallow close exception to avoid masking try-with-resources failures.
                // The connection has already been returned to the pool internally by the driver.
            }
        }
    }
}