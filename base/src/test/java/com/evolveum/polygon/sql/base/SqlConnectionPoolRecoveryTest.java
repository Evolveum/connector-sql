/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.sql.base;

import com.evolveum.polygon.sql.base.connection.HikariConnectionPool;
import com.evolveum.polygon.sql.base.test.TestConnectors;
import org.identityconnectors.common.security.GuardedString;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class SqlConnectionPoolRecoveryTest {

    @Test
    public void sameConnectorRecoversAfterRepeatedInitializationFailures() throws Exception {
        var databaseUrl = databaseUrl();
        var connector = TestConnectors.of();
        connector.init(configuration(databaseUrl));
        try {
            // IFEXISTS prevents the connector from creating the unavailable database itself.
            for (int attempt = 0; attempt < 2; attempt++) {
                assertThatThrownBy(connector::test)
                        .isInstanceOf(RuntimeException.class)
                        .hasRootCauseInstanceOf(SQLException.class);
            }

            // Restore availability without changing configuration or calling connector.init() again.
            try (var database = DriverManager.getConnection(databaseUrl, "sa", "")) {
                connector.test();
                var context = connector.context();
                var pool = context.getConnectionPool();
                assertConnectionWorks(context);
                connector.test();
                assertThat(context.getConnectionPool()).isSameAs(pool);
            }
        } finally {
            connector.dispose();
        }
    }

    @DataProvider
    public Object[][] initializationMethods() {
        return new Object[][] {{false}, {true}};
    }

    @Test(dataProvider = "initializationMethods")
    public void failedInitialPoolIsNotPublished(boolean explicitInitialization) throws Exception {
        var databaseUrl = databaseUrl();
        var context = new SqlBaseContext(configuration(databaseUrl));
        Runnable initialize = explicitInitialization
                ? context::initializeConnectionPool : context::ensureConnectionPool;
        try {
            assertThatThrownBy(initialize::run)
                    .isInstanceOf(RuntimeException.class)
                    .hasRootCauseInstanceOf(SQLException.class);
            assertThat(context.getConnectionPool()).isNull();

            try (var database = DriverManager.getConnection(databaseUrl, "sa", "")) {
                initialize.run();
                assertConnectionWorks(context);
            }
        } finally {
            context.close();
        }
    }

    @Test
    public void failedReplacementPreservesPreviousPoolAndSuccessfulReplacementClosesIt() throws Exception {
        var databaseUrl = databaseUrl();
        var configuration = configuration(databaseUrl);
        var context = new SqlBaseContext(configuration);
        try (var database = DriverManager.getConnection(databaseUrl, "sa", "")) {
            try {
                context.initializeConnectionPool();
                var original = context.getConnectionPool();

                configuration.setJdbcUrl(databaseUrl() + ";IFEXISTS=TRUE");
                assertThatThrownBy(context::initializeConnectionPool)
                        .isInstanceOf(RuntimeException.class)
                        .hasRootCauseInstanceOf(SQLException.class);
                assertThat(context.getConnectionPool()).isSameAs(original);
                assertThat(original.isClosed()).isFalse();
                assertConnectionWorks(context);

                configuration.setJdbcUrl(databaseUrl + ";IFEXISTS=TRUE");
                context.initializeConnectionPool();
                assertThat(context.getConnectionPool()).isNotSameAs(original);
                assertThat(original.isClosed()).isTrue();
                assertConnectionWorks(context);
            } finally {
                context.close();
            }
        }
    }

    @Test
    public void concurrentEnsureCallsReuseOneInitializedPool() throws Exception {
        var databaseUrl = databaseUrl();
        var context = new SqlBaseContext(configuration(databaseUrl));
        var executor = Executors.newFixedThreadPool(4);
        var ready = new CountDownLatch(4);
        var start = new CountDownLatch(1);
        try (var database = DriverManager.getConnection(databaseUrl, "sa", "")) {
            var futures = new ArrayList<Future<HikariConnectionPool>>();
            for (int i = 0; i < 4; i++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
                    context.ensureConnectionPool();
                    assertConnectionWorks(context);
                    return context.getConnectionPool();
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            var pool = futures.getFirst().get(10, TimeUnit.SECONDS);
            for (var future : futures) {
                assertThat(future.get(10, TimeUnit.SECONDS)).isSameAs(pool);
            }
            context.ensureConnectionPool();
            assertThat(context.getConnectionPool()).isSameAs(pool);
        } finally {
            start.countDown();
            executor.shutdownNow();
            try {
                assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
            } finally {
                context.close();
            }
        }
    }

    private static String databaseUrl() {
        return "jdbc:h2:mem:pool_recovery_" + UUID.randomUUID();
    }

    private static SqlConnectorConfiguration configuration(String databaseUrl) {
        var configuration = new SqlConnectorConfiguration();
        configuration.setJdbcUrl(databaseUrl + ";IFEXISTS=TRUE");
        configuration.setUsername("sa");
        configuration.setPassword(new GuardedString(new char[0]));
        configuration.setPoolSize(1);
        configuration.setConnectionTimeout(1000);
        configuration.setScanTables(false);
        configuration.setScanViews(false);
        return configuration;
    }

    private static void assertConnectionWorks(SqlBaseContext context) throws SQLException {
        try (var connection = context.getConnection();
                var statement = connection.getConnection().createStatement();
                var result = statement.executeQuery("SELECT 1")) {
            assertThat(result.next()).isTrue();
            assertThat(result.getInt(1)).isEqualTo(1);
        }
    }
}
