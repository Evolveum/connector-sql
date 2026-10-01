/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.sql.base.connection;

import com.evolveum.polygon.sql.base.SqlConnectorConfiguration;
import org.identityconnectors.common.security.GuardedString;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.fail;

public class HikariConnectionPoolTest {

    private SqlConnectorConfiguration configuration;
    private HikariConnectionPool pool;

    @BeforeMethod
    public void setUp() {
        configuration = new SqlConnectorConfiguration();
        configuration.setJdbcUrl("jdbc:h2:mem:hikaritest;DB_CLOSE_DELAY=-1");
        configuration.setUsername("sa");
        configuration.setPassword(new GuardedString("".toCharArray()));
        configuration.setPoolSize(5);
        configuration.setConnectionTimeout(30000);
        configuration.setIdleTimeout(600000);
        configuration.setValidateConnectionOnBorrow(true);
        configuration.setScanTables(false);
        configuration.setScanViews(false);
    }

    @AfterMethod
    public void tearDown() {
        if (pool != null && !pool.isClosed()) {
            pool.close();
        }
    }

    @Test
    public void testPoolInitialization() {
        pool = new HikariConnectionPool(configuration);
        pool.initialize();

        assertThat(pool.isClosed()).isFalse();
        assertThat(pool.getDataSource()).isNotNull();
    }

    @Test
    public void testConnectionAcquisition() throws SQLException {
        pool = new HikariConnectionPool(configuration);
        pool.initialize();

        var conn = pool.getConnection(null);
        assertThat(conn).isNotNull();
        assertThat(conn.getConnection()).isNotNull();
        conn.close();
    }

    @Test
    public void testConnectionPoolSize() throws SQLException {
        configuration.setPoolSize(2);
        pool = new HikariConnectionPool(configuration);
        pool.initialize();

        assertThat(pool.getDataSource().getMaximumPoolSize()).isEqualTo(2);

        // Acquire 2 connections (pool size)
        var conn1 = pool.getConnection(null);
        var conn2 = pool.getConnection(null);
        assertThat(conn1).isNotNull();
        assertThat(conn2).isNotNull();

        conn1.close();
        conn2.close();
    }

    @Test
    public void testMultipleConnections() throws SQLException {
        pool = new HikariConnectionPool(configuration);
        pool.initialize();

        var conn1 = pool.getConnection(null);
        var conn2 = pool.getConnection(null);

        assertThat(conn1 != conn2).withFailMessage("Should return different connection instances").isTrue();

        // Connections should be independent
        var raw1 = conn1.getConnection();
        var raw2 = conn2.getConnection();
        assertThat(raw1 != raw2).isTrue();

        conn1.close();
        conn2.close();
    }

    @Test
    public void testConnectionTest() throws SQLException {
        pool = new HikariConnectionPool(configuration);
        pool.initialize();

        pool.test(); // Should not throw
    }

    @Test
    public void testTestBeforeInitialization() {
        pool = new HikariConnectionPool(configuration);
        // Don't initialize pool

        try {
            pool.test();
            fail("Should throw SQLException");
        } catch (SQLException e) {
            assertThat(e.getMessage().contains("not initialized")).isTrue();
        }
    }

    @Test
    public void testPoolClose() throws SQLException {
        pool = new HikariConnectionPool(configuration);
        pool.initialize();

        assertThat(pool.isClosed()).isFalse();
        pool.close();
        assertThat(pool.isClosed()).isTrue();
    }

    @Test
    public void testDoubleClose() throws SQLException {
        pool = new HikariConnectionPool(configuration);
        pool.initialize();
        pool.close();

        // Second close should not throw
        pool.close();
        assertThat(pool.isClosed()).isTrue();
    }

    @Test
    public void testConfigDefaults() {
        configuration.setPoolSize(null);
        configuration.setValidateConnectionOnBorrow(null);

        pool = new HikariConnectionPool(configuration);
        pool.initialize();

        assertThat(pool.getDataSource().getMaximumPoolSize()).withFailMessage("Default pool size should be 10").isEqualTo(10);

        pool.close();
    }

    @Test
    public void testExplicitDriverClassNameIsUsed() {
        configuration.setJdbcUrl("jdbc:h2:mem:driverexplicit;DB_CLOSE_DELAY=-1");
        configuration.setDriverClassName("org.h2.Driver");

        pool = new HikariConnectionPool(configuration);
        pool.initialize();

        assertThat(pool.getDataSource().getDriverClassName()).isEqualTo("org.h2.Driver");
    }

    @Test
    public void testDriverAutoResolvedWhenUnset() {
        configuration.setJdbcUrl("jdbc:h2:mem:driverauto;DB_CLOSE_DELAY=-1");

        pool = new HikariConnectionPool(configuration);
        pool.initialize();

        assertThat(pool.getDataSource().getDriverClassName()).isEqualTo("org.h2.Driver");
    }

    @DataProvider
    public Object[][] jdbcUrlWhitespace() {
        return new Object[][] {
                { " ", "" },
                { "\n                ", "\n            " },
                { "\r\n\t ", " \t\r\n" },
                { "", " \t" }
        };
    }

    @Test(dataProvider = "jdbcUrlWhitespace")
    public void testOracleDriverResolvedWithWhitespaceAroundJdbcUrl(String prefix, String suffix) {
        configuration.setJdbcUrl(prefix + "jdbc:oracle:thin:@//localhost:1521/service" + suffix);

        // Check explicit driver selection: an already registered driver must not hide the regression.
        assertThat(HikariConnectionPool.resolveDriverClassName(configuration)).isEqualTo("oracle.jdbc.OracleDriver");
    }

    @Test
    public void testPoolInitializationWithWhitespaceAroundJdbcUrl() throws SQLException {
        var jdbcUrl = "jdbc:h2:mem:driverwhitespace;DB_CLOSE_DELAY=-1";
        configuration.setJdbcUrl("\n    " + jdbcUrl + "\n");

        pool = new HikariConnectionPool(configuration);
        pool.initialize();

        assertThat(pool.getDataSource().getDriverClassName()).isEqualTo("org.h2.Driver");
        assertThat(pool.getDataSource().getJdbcUrl()).isEqualTo(jdbcUrl);
        assertThat(configuration.getJdbcUrl()).isEqualTo(jdbcUrl);
        try (var connection = pool.getDataSource().getConnection()) {
            assertThat(connection.isValid(1)).isTrue();
        }
    }

    @DataProvider
    public Object[][] missingJdbcUrls() {
        return new Object[][] { { null }, { "" }, { " \t\r\n" } };
    }

    @Test(dataProvider = "missingJdbcUrls")
    public void testMissingJdbcUrlIsRejected(String jdbcUrl) {
        configuration.setJdbcUrl(jdbcUrl);

        assertThat(configuration.isComplete()).isFalse();
        assertThatIllegalArgumentException().isThrownBy(configuration::validate).withMessage("JDBC URL is required");
    }

    @Test
    public void testBlankDriverClassNameFallsBackToAutoResolution() {
        configuration.setJdbcUrl("jdbc:h2:mem:driverblank;DB_CLOSE_DELAY=-1");
        configuration.setDriverClassName("   ");

        pool = new HikariConnectionPool(configuration);
        pool.initialize();

        assertThat(pool.getDataSource().getDriverClassName()).isEqualTo("org.h2.Driver");
    }

    @Test
    public void testResolveDriverClassNamePrefersExplicit() {
        configuration.setJdbcUrl("jdbc:postgresql://localhost:5432/mydb");
        configuration.setDriverClassName("com.example.CustomDriver");

        assertThat(HikariConnectionPool.resolveDriverClassName(configuration)).isEqualTo("com.example.CustomDriver");
    }

    @Test
    public void testResolveDriverClassNameFallsBackToJdbcUrl() {
        configuration.setJdbcUrl("jdbc:postgresql://localhost:5432/mydb");

        assertThat(HikariConnectionPool.resolveDriverClassName(configuration)).isEqualTo("org.postgresql.Driver");
    }

    @Test
    public void testResolveDriverClassNameBlankFallsBackToJdbcUrl() {
        configuration.setJdbcUrl("jdbc:mysql://localhost:3306/mydb");
        configuration.setDriverClassName("");

        assertThat(HikariConnectionPool.resolveDriverClassName(configuration)).isEqualTo("com.mysql.cj.jdbc.Driver");
    }
}
