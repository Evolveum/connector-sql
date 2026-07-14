package com.evolveum.polygon.sql.base.test;

import com.evolveum.polygon.sql.base.SqlBaseContext;
import com.evolveum.polygon.sql.base.SqlConnectorConfiguration;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Duration;
import java.util.function.Consumer;

/**
 * Embedded PostgreSQL (v16) initializer for integration tests.
 * <p>
 * Starts and manages an embedded PostgreSQL 16 instance backed by zonkyio binaries,
 * creates a dedicated test database, and provides access to JDBC URLs and SqlBaseContext.
 * </p>
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * // Basic initialization
 * try (PostgresDatabaseInitializer pg = PostgresDatabaseInitializer.create()) {
 *     SqlBaseContext ctx = pg.createContext(true);
 *     // run tests against ctx...
 * }
 *
 * // Custom initialization with builder options
 * try (PostgresDatabaseInitializer pg = PostgresDatabaseInitializer.create(builder -> {
 *         builder.setPGStartupWait(Duration.ofSeconds(30));
 *         builder.setServerConfig("jit", "off");
 *     })) {
 *     // use pg for tests...
 * }
 * }</pre>
 */
public final class PostgresDatabaseInitializer implements AutoCloseable {

    private static final String SCHEMA_RESOURCE = "postgresql/basic/schema.sql";
    private static final String DATA_RESOURCE = "postgresql/basic/data.sql";

    private final EmbeddedPostgres postgres;

    private PostgresDatabaseInitializer(EmbeddedPostgres postgres) {
        this.postgres = postgres;
    }

    /**
     * Creates a fully initialized PostgresDatabaseInitializer on a random port.
     * Starts a PostgreSQL 16 server and ensures the 'postgres' database exists.
     *
     * @return a fully started embedded PostgreSQL initializer
     * @throws RuntimeException if the embedded PostgreSQL instance cannot be started
     */
    public static PostgresDatabaseInitializer create() {
        try {
            var builder = EmbeddedPostgres.builder()
                    .setPGStartupWait(Duration.ofSeconds(30));
            var pg = builder.start();
            ensurePostgresDbExists(pg);
            return new PostgresDatabaseInitializer(pg);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create embedded PostgreSQL instance", e);
        }
    }

    /**
     * Creates a fully initialized PostgresDatabaseInitializer with custom builder configuration.
     *
     * @param customizer function to customize the EmbeddedPostgres builder
     * @return a fully started embedded PostgreSQL initializer
     */
    public static PostgresDatabaseInitializer create(Consumer<EmbeddedPostgres.Builder> customizer) {
        try {
            var builder = EmbeddedPostgres.builder()
                    .setPGStartupWait(Duration.ofSeconds(30));
            customizer.accept(builder);
            var pg = builder.start();
            ensurePostgresDbExists(pg);
            return new PostgresDatabaseInitializer(pg);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create embedded PostgreSQL instance", e);
        }
    }

    private static void ensurePostgresDbExists(EmbeddedPostgres pg) throws SQLException {
        String port = String.valueOf(pg.getPort());

        try (var conn = DriverManager.getConnection(
                "jdbc:postgresql://localhost:" + port + "/template1", "postgres", "postgres")) {
            try (var stmt = conn.createStatement()) {
                try {
                    stmt.execute("CREATE DATABASE \"postgres\"");
                } catch (SQLException e) {
                    if (!e.getMessage().toLowerCase().contains("already exists")) {
                        throw e;
                    }
                }
            }
        }
    }

    /**
     * Creates a SqlBaseContext configured to connect to this embedded PostgreSQL instance
     * with the default schema and test data loaded, suitable for full connector integration tests.
     *
     * @param autoDiscoverSchema whether to auto-discover schema from database tables
     * @return a configured SqlBaseContext ready for testing
     * @throws Exception if the context cannot be initialized
     */
    public SqlBaseContext createContext(boolean autoDiscoverSchema) throws Exception {
        var url = postgres.getJdbcUrl("postgres", "postgres");

        var config = new SqlConnectorConfiguration();
        config.setJdbcUrl(url);
        config.setUsername("postgres");
        config.setPassword("postgres");
        config.setPoolSize(5);
        config.setConnectionTimeout(10000);
        config.setIdleTimeout(30);
        config.setValidateConnectionOnBorrow(true);
        config.setAutoDiscoverSchema(autoDiscoverSchema);

        var ctx = new SqlBaseContext(config);
        ctx.initializeConnectionPool();

        try (var conn = ctx.getConnection()) {
            executeSql(conn.getConnection(), SCHEMA_RESOURCE);
            executeSql(conn.getConnection(), DATA_RESOURCE);
        } catch (Exception e) {
            ctx.close();
            throw e;
        }

        return ctx;
    }

    /**
     * Provides the JDBC URL for this embedded instance. Useful for tests that need direct JDBC access.
     */
    public String getJdbcUrl() {
        return postgres.getJdbcUrl("postgres", "postgres");
    }

    /**
     * Provides the username for connecting to this embedded instance.
     */
    public String getUsername() {
        return "postgres";
    }

    /**
     * Provides the password for connecting to this embedded instance.
     */
    public String getPassword() {
        return "postgres";
    }

    /**
     * Provides the port for this embedded instance. Useful for direct JDBC access.
     */
    public int getPort() {
        return postgres.getPort();
    }

    /**
     * Provides direct EmbeddedPostgres instance reference if tests need fine-grained control.
     */
    public EmbeddedPostgres getEmbeddedPostgres() {
        return postgres;
    }

    @Override
    public void close() {
        try {
            postgres.close();
        } catch (IOException e) {
            throw new RuntimeException("Failed to close embedded PostgreSQL", e);
        }
    }

    private static void executeSql(Connection conn, String resourcePath) throws SQLException, IOException {
        try (var is = Thread.currentThread().getContextClassLoader()
                .getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new IOException("Resource not found: " + resourcePath);
            }
            var sql = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            try (var stmt = conn.createStatement()) {
                stmt.execute(sql);
            }
        }
    }
}