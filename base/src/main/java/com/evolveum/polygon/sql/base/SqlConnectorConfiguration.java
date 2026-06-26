/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.sql.base;

import org.identityconnectors.framework.common.objects.ConnectorMessages;
import org.identityconnectors.framework.spi.Configuration;

/**
 * Configuration class for SQL connector.
 * Supports multiple SQL dialects with auto-discovery.
 */
public class SqlConnectorConfiguration implements Configuration {

    private ConnectorMessages messages;
    private String jdbcUrl;
    private String dialect; // postgresql, mysql, oracle, sqlite, auto
    private String username;
    private String password;
    private Integer poolSize = 10;
    private Integer connectionTimeout = 30000;
    private Integer idleTimeout = 600000;
    private Boolean validateConnectionOnBorrow = true;
    private Boolean autoDiscoverSchema = true;
    private Boolean logSqlStatements = false;

    @Override
    public ConnectorMessages getConnectorMessages() {
        return messages;
    }

    @Override
    public void setConnectorMessages(ConnectorMessages messages) {
        this.messages = messages;
    }

    public String getJdbcUrl() {
        return jdbcUrl;
    }

    public void setJdbcUrl(String jdbcUrl) {
        this.jdbcUrl = jdbcUrl;
    }

    public String getDialect() {
        return dialect;
    }

    public void setDialect(String dialect) {
        this.dialect = dialect;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    /**
     * Returns the password for database authentication.
     * Note: This value is sensitive and should only be used for database connection setup.
     * It is intentionally omitted from toString() to prevent accidental exposure.
     */
    public String getPassword() {
        return password;
    }

    /** @deprecated Use {@link #getPassword()} instead. */
    @Deprecated
    String getPasswordInternal() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public Integer getPoolSize() {
        return poolSize;
    }

    public void setPoolSize(Integer poolSize) {
        this.poolSize = poolSize;
    }

    public Integer getConnectionTimeout() {
        return connectionTimeout;
    }

    public void setConnectionTimeout(Integer connectionTimeout) {
        this.connectionTimeout = connectionTimeout;
    }

    public Integer getIdleTimeout() {
        return idleTimeout;
    }

    public void setIdleTimeout(Integer idleTimeout) {
        this.idleTimeout = idleTimeout;
    }

    public Boolean isValidateConnectionOnBorrow() {
        return validateConnectionOnBorrow;
    }

    public void setValidateConnectionOnBorrow(Boolean validateConnectionOnBorrow) {
        this.validateConnectionOnBorrow = validateConnectionOnBorrow;
    }

    public Boolean isAutoDiscoverSchema() {
        return autoDiscoverSchema;
    }

    public void setAutoDiscoverSchema(Boolean autoDiscoverSchema) {
        this.autoDiscoverSchema = autoDiscoverSchema;
    }

    public Boolean isLogSqlStatements() {
        return logSqlStatements;
    }

    public void setLogSqlStatements(Boolean logSqlStatements) {
        this.logSqlStatements = logSqlStatements;
    }

    @Override
    public void validate() {
        if (jdbcUrl == null || jdbcUrl.isEmpty()) {
            throw new IllegalArgumentException("JDBC URL is required");
        }
        if (username == null || username.isEmpty()) {
            throw new IllegalArgumentException("Username is required");
        }
    }

    @Override
    public String toString() {
        return """
            SqlConnectorConfiguration{
                jdbcUrl='%s',
                dialect='%s',
                username='%s',
                poolSize=%s,
                connectionTimeout=%s,
                idleTimeout=%s,
                validateConnectionOnBorrow=%s,
                autoDiscoverSchema=%s,
                logSqlStatements=%s
            }"""
            .formatted("jdbcUrl=**hidden**", 
                      dialect,
                      username,
                      poolSize,
                      connectionTimeout,
                      idleTimeout,
                      validateConnectionOnBorrow,
                      autoDiscoverSchema,
                      logSqlStatements);
    }
}