/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.sql.base.connection;

import com.evolveum.polygon.sql.base.SqlConnectorConfiguration;
import com.querydsl.sql.SQLTemplates;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.SQLException;

/**
 * HikariCP connection pool management.
 */
public class HikariConnectionPool {

    private final SqlConnectorConfiguration configuration;
    private HikariDataSource dataSource;

    public HikariConnectionPool(SqlConnectorConfiguration configuration) {
        this.configuration = configuration;
    }

    public void initialize() {
        var config = new HikariConfig();

        config.setJdbcUrl(configuration.getJdbcUrl());
        config.setUsername(configuration.getUsername());
        config.setPassword(configuration.getPassword());

        var poolSize = configuration.getPoolSize();
        if (poolSize != null && poolSize > 0) {
            config.setMaximumPoolSize(poolSize);
        } else {
            config.setMaximumPoolSize(10);
        }

        var connectionTimeout = configuration.getConnectionTimeout();
        if (connectionTimeout != null) {
            config.setConnectionTimeout(connectionTimeout.longValue());
        }

        var idleTimeout = configuration.getIdleTimeout();
        if (idleTimeout != null) {
            config.setIdleTimeout(idleTimeout.longValue());
        }

        config.setAutoCommit(true);
        config.setPoolName("PolygonSQLPool");

        var validateOnBorrow = configuration.isValidateConnectionOnBorrow();
        if (validateOnBorrow != null && validateOnBorrow) {
            config.setConnectionTestQuery("SELECT 1");
            config.setValidationTimeout(3000);
        }

        dataSource = new HikariDataSource(config);
    }

    /**
     * Acquires a connection from this pool.
     * @return a SqlConnection wrapper
     * @throws SQLException if the pool is closed or cannot provide a connection
     */
    public SqlConnection getConnection(SQLTemplates templates) throws SQLException {
        if (dataSource == null || dataSource.isClosed()) {
            throw new SQLException("Connection pool is closed", (Throwable) null);
        }
        try {
            var connection = dataSource.getConnection();
            if (connection == null) {
                throw new SQLException("Pool returned a null connection", (Throwable) null);
            }
            return new SqlConnection(connection, templates);
        } catch (SQLException e) {
            throw e;
        } catch (Exception e) {
            throw new SQLException("Failed to acquire connection from pool: " + e.getMessage(), e);
        }
    }

    public void test() throws SQLException {
        if (dataSource == null || dataSource.isClosed()) {
            throw new SQLException("Connection pool not initialized", (Throwable) null);
        }
        try (var conn = dataSource.getConnection()) {
            conn.createStatement().executeQuery("SELECT 1");
        }
    }

    public void close() {
        HikariDataSource old;
        synchronized (this) {
            old = dataSource;
            dataSource = null;
        }
        
        if (old != null && !old.isClosed()) {
            old.close();
        }
    }

    public boolean isClosed() {
        HikariDataSource ds;
        synchronized (this) {
            ds = dataSource;
        }
        return ds == null || ds.isClosed();
    }

    /**
     * @return the underlying HikariDataSource (for internal use only)
     */
    HikariDataSource getDataSource() {
        HikariDataSource ds;
        synchronized (this) {
            ds = dataSource;
        }
        return ds;
    }
}