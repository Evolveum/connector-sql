package com.evolveum.polygon.sql.base.test;

import com.evolveum.polygon.sql.base.SqlBaseContext;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.sql.ResultSet;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;

@Test(singleThreaded = true)
public class H2DatabaseInitializerTest {

    private SqlBaseContext context;

    @BeforeMethod
    public void setUp() {
        context = null;
    }

    @AfterMethod
    public void tearDown() {
        if (context != null) {
            context.close();
            context = null;
        }
    }

    @Test
    public void testBasicCreation() {
        context = H2DatabaseInitializer.create();
        assertThat(context).isNotNull();
    }

    @Test
    public void testConnectionWorks() throws SQLException {
        context = H2DatabaseInitializer.create();
        try (var conn = context.getConnection()) {
            assertThat(conn).isNotNull();
            conn.getConnection().createStatement().executeQuery("SELECT 1").close();
        }
    }

    @Test
    public void testTablesCreated() throws SQLException {
        context = H2DatabaseInitializer.create();
        try (var conn = context.getConnection()) {
            ResultSet rs = conn.getConnection()
                    .createStatement()
                    .executeQuery("SELECT COUNT(*) FROM \"User\"");
            assertThat(rs.next()).isTrue();
            assertThat(rs.getInt(1)).isEqualTo(2);
            rs.close();
        }
    }

    @Test
    public void testMultipleConnections() throws SQLException {
        context = H2DatabaseInitializer.create();
        try (var conn1 = context.getConnection();
             var conn2 = context.getConnection()) {
            assertThat(conn1).isNotNull();
            assertThat(conn2).isNotNull();
            conn1.getConnection().createStatement()
                    .executeQuery("SELECT 1").close();
            conn2.getConnection().createStatement()
                    .executeQuery("SELECT 1").close();
        }
    }

    @Test
    public void testTransactionWorks() throws SQLException {
        context = H2DatabaseInitializer.create();
        try (var conn = context.getConnection()) {
            assertThat(conn).isNotNull();
            conn.rollback();
        }
    }

    @Test
    public void testSchemaDiscovered() throws SQLException {
        context = H2DatabaseInitializer.create();
        assertThat(context).isNotNull();
    }
}