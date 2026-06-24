/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.sql.base.connection;

import com.evolveum.polygon.sql.base.SqlConnectorConfiguration;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.sql.Connection;
import java.sql.SQLException;

import static org.testng.Assert.*;

public class HikariConnectionPoolTest {

    private SqlConnectorConfiguration configuration;
    private HikariConnectionPool pool;

    @BeforeMethod
    public void setUp() {
        configuration = new SqlConnectorConfiguration();
        configuration.setJdbcUrl("jdbc:h2:mem:hikaritest;DB_CLOSE_DELAY=-1");
        configuration.setUsername("sa");
        configuration.setPassword("");
        configuration.setPoolSize(5);
        configuration.setConnectionTimeout(30000);
        configuration.setIdleTimeout(600000);
        configuration.setValidateConnectionOnBorrow(true);
        configuration.setAutoDiscoverSchema(false);
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

        assertFalse(pool.isClosed());
        assertNotNull(pool.getDataSource());
    }

    @Test
    public void testConnectionAcquisition() throws SQLException {
        pool = new HikariConnectionPool(configuration);
        pool.initialize();

        SqlConnection conn = pool.getConnection();
        assertNotNull(conn);
        assertNotNull(conn.getConnection());
        conn.close();
    }

@Test
    public void testConnectionPoolSize() throws SQLException {
        configuration.setPoolSize(2);
        pool = new HikariConnectionPool(configuration);
        pool.initialize();

        assertEquals(pool.getDataSource().getMaximumPoolSize(), 2);

        // Acquire 2 connections (pool size)
        SqlConnection conn1 = pool.getConnection();
        SqlConnection conn2 = pool.getConnection();
        assertNotNull(conn1);
        assertNotNull(conn2);

        conn1.close();
        conn2.close();
    }

    @Test
    public void testMultipleConnections() throws SQLException {
        pool = new HikariConnectionPool(configuration);
        pool.initialize();

        SqlConnection conn1 = pool.getConnection();
        SqlConnection conn2 = pool.getConnection();

        assertTrue(conn1 != conn2, "Should return different connection instances");

        // Connections should be independent
        Connection raw1 = conn1.getConnection();
        Connection raw2 = conn2.getConnection();
        assertTrue(raw1 != raw2);

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
            assertTrue(e.getMessage().contains("not initialized"));
        }
    }

    @Test
    public void testPoolClose() throws SQLException {
        pool = new HikariConnectionPool(configuration);
        pool.initialize();

        assertFalse(pool.isClosed());
        pool.close();
        assertTrue(pool.isClosed());
    }

    @Test
    public void testDoubleClose() throws SQLException {
        pool = new HikariConnectionPool(configuration);
        pool.initialize();
        pool.close();

        // Second close should not throw
        pool.close();
        assertTrue(pool.isClosed());
    }

    @Test
    public void testConfigDefaults() {
        configuration.setPoolSize(null);
        configuration.setValidateConnectionOnBorrow(null);
        
        pool = new HikariConnectionPool(configuration);
        pool.initialize();

        assertEquals(pool.getDataSource().getMaximumPoolSize(), 10, "Default pool size should be 10");

        pool.close();
    }
}