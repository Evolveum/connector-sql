/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.sql.base;

import com.evolveum.polygon.conndev.groovy.BaseGroovyConnectorConfiguration;
import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.framework.spi.ConfigurationProperty;

/**
 * Configuration class for SQL connector.
 * Supports multiple SQL dialects with auto-discovery.
 */
public class SqlConnectorConfiguration extends BaseGroovyConnectorConfiguration {

    private String jdbcUrl;
    private String driverClassName;
    private String username;
    private GuardedString password;
    private Integer poolSize = 10;
    private Integer connectionTimeout = 30000;
    private Integer idleTimeout = 600000;
    private Boolean validateConnectionOnBorrow = true;
    private Boolean scanTables = true;
    private Boolean scanViews = true;
    private String scanTableFilter;
    private String scanViewFilter;
    private String scanExcludeTables;
    private String scanExcludeViews;
    private String testConnectionQuery;
    private String pgDumpPath = "pg_dump";

    @ConfigurationProperty(required = true, groupMessageKey = "sql.basic", order = 0)
    public String getJdbcUrl() {
        return jdbcUrl;
    }

    public void setJdbcUrl(String jdbcUrl) {
        this.jdbcUrl = jdbcUrl;
    }

    /**
     * Returns the fully-qualified JDBC driver class name to use for the connection.
     * When not set (or blank), the driver class is resolved automatically from the
     * {@link #getJdbcUrl() jdbcUrl} scheme. Set this to bundle a different JDBC backend
     * (e.g. a vendor-specific, forked, or otherwise custom driver).
     */
    @ConfigurationProperty(groupMessageKey = "sql.advanced", order = 200)
    public String getDriverClassName() {
        return driverClassName;
    }

    public void setDriverClassName(String driverClassName) {
        this.driverClassName = driverClassName;
    }

    @ConfigurationProperty(required = true, groupMessageKey = "sql.basic", order = 1)
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
    @ConfigurationProperty(required = true, groupMessageKey = "sql.basic", order = 2)
    public GuardedString getPassword() {
        return password;
    }

    public void setPassword(GuardedString password) {
        this.password = password;
    }

    @ConfigurationProperty(groupMessageKey = "sql.advanced", order = 201)
    public Integer getPoolSize() {
        return poolSize;
    }

    public void setPoolSize(Integer poolSize) {
        this.poolSize = poolSize;
    }

    @ConfigurationProperty(groupMessageKey = "sql.advanced", order = 202)
    public Integer getConnectionTimeout() {
        return connectionTimeout;
    }

    public void setConnectionTimeout(Integer connectionTimeout) {
        this.connectionTimeout = connectionTimeout;
    }

    @ConfigurationProperty(groupMessageKey = "sql.advanced", order = 203)
    public Integer getIdleTimeout() {
        return idleTimeout;
    }

    public void setIdleTimeout(Integer idleTimeout) {
        this.idleTimeout = idleTimeout;
    }

    @ConfigurationProperty(groupMessageKey = "sql.advanced", order = 204)
    public Boolean getValidateConnectionOnBorrow() {
        return validateConnectionOnBorrow;
    }

    public void setValidateConnectionOnBorrow(Boolean validateConnectionOnBorrow) {
        this.validateConnectionOnBorrow = validateConnectionOnBorrow;
    }

    @ConfigurationProperty(groupMessageKey = "sql.schemaScanning", order = 100)
    public Boolean getScanTables() {
        return scanTables;
    }

    public void setScanTables(Boolean scanTables) {
        this.scanTables = scanTables;
    }

    @ConfigurationProperty(groupMessageKey = "sql.schemaScanning", order = 101)
    public Boolean getScanViews() {
        return scanViews;
    }

    public void setScanViews(Boolean scanViews) {
        this.scanViews = scanViews;
    }

    @ConfigurationProperty(groupMessageKey = "sql.schemaScanning", order = 102)
    public String getScanTableFilter() {
        return scanTableFilter;
    }

    public void setScanTableFilter(String scanTableFilter) {
        this.scanTableFilter = scanTableFilter;
    }

    @ConfigurationProperty(groupMessageKey = "sql.schemaScanning", order = 103)
    public String getScanViewFilter() {
        return scanViewFilter;
    }

    public void setScanViewFilter(String scanViewFilter) {
        this.scanViewFilter = scanViewFilter;
    }

    @ConfigurationProperty(groupMessageKey = "sql.schemaScanning", order = 104)
    public String getScanExcludeTables() {
        return scanExcludeTables;
    }

    public void setScanExcludeTables(String scanExcludeTables) {
        this.scanExcludeTables = scanExcludeTables;
    }

    @ConfigurationProperty(groupMessageKey = "sql.schemaScanning", order = 105)
    public String getScanExcludeViews() {
        return scanExcludeViews;
    }

    public void setScanExcludeViews(String scanExcludeViews) {
        this.scanExcludeViews = scanExcludeViews;
    }

    /**
     * Returns true if auto-discovery is enabled (scan any tables or views).
     * Kept for backward compatibility with callers that check autoDiscoverSchema.
     */
    public Boolean getAutoDiscoverSchema() {
        return Boolean.TRUE.equals(scanTables) || Boolean.TRUE.equals(scanViews);
    }

    @Override
    public void validate() {
        super.validate();
        if (jdbcUrl == null || jdbcUrl.isEmpty()) {
            throw new IllegalArgumentException("JDBC URL is required");
        }
        if (username == null || username.isEmpty()) {
            throw new IllegalArgumentException("Username is required");
        }
        if (password == null) {
            throw new IllegalArgumentException("Password is required");
        }
    }

    /**
     * Non-throwing counterpart of {@link #validate()}: true if the required connection
     * parameters are present, i.e. connecting to the database is actually possible.
     */
    public boolean isComplete() {
        return jdbcUrl != null && !jdbcUrl.isEmpty()
                && username != null && !username.isEmpty()
                && password != null;
    }

    @ConfigurationProperty(groupMessageKey = "sql.advanced", order = 205)
    public String getTestConnectionQuery() {
        return testConnectionQuery;
    }

    public void setTestConnectionQuery(String testConnectionQuery) {
        this.testConnectionQuery = testConnectionQuery;
    }

    /**
     * Executable used to read native PostgreSQL table and view definitions in development mode.
     * May be an absolute path or a command available on {@code PATH}. A blank value disables it.
     */
    @ConfigurationProperty(groupMessageKey = "sql.advanced", order = 206)
    public String getPgDumpPath() {
        return pgDumpPath;
    }

    public void setPgDumpPath(String pgDumpPath) {
        this.pgDumpPath = pgDumpPath;
    }
}
