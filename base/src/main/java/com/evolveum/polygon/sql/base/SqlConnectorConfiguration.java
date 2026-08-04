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

    @ConfigurationProperty(required = true, order = 0)
    public String getJdbcUrl() {
        return jdbcUrl;
    }

    public void setJdbcUrl(String jdbcUrl) {
        this.jdbcUrl = jdbcUrl;
    }

    @ConfigurationProperty(required = true, order = 20)
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
    @ConfigurationProperty(required = true, order = 30)
    public GuardedString getPassword() {
        return password;
    }

    public void setPassword(GuardedString password) {
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

    public Boolean getValidateConnectionOnBorrow() {
        return validateConnectionOnBorrow;
    }

    public void setValidateConnectionOnBorrow(Boolean validateConnectionOnBorrow) {
        this.validateConnectionOnBorrow = validateConnectionOnBorrow;
    }

    public Boolean getScanTables() {
        return scanTables;
    }

    public void setScanTables(Boolean scanTables) {
        this.scanTables = scanTables;
    }

    public Boolean getScanViews() {
        return scanViews;
    }

    public void setScanViews(Boolean scanViews) {
        this.scanViews = scanViews;
    }

    public String getScanTableFilter() {
        return scanTableFilter;
    }

    public void setScanTableFilter(String scanTableFilter) {
        this.scanTableFilter = scanTableFilter;
    }

    public String getScanViewFilter() {
        return scanViewFilter;
    }

    public void setScanViewFilter(String scanViewFilter) {
        this.scanViewFilter = scanViewFilter;
    }

    public String getScanExcludeTables() {
        return scanExcludeTables;
    }

    public void setScanExcludeTables(String scanExcludeTables) {
        this.scanExcludeTables = scanExcludeTables;
    }

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

    public String getTestConnectionQuery() {
        return testConnectionQuery;
    }

    public void setTestConnectionQuery(String testConnectionQuery) {
        this.testConnectionQuery = testConnectionQuery;
    }
}