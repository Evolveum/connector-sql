package com.evolveum.polygon.sql.base.test;

import com.evolveum.polygon.sql.base.SqlBaseContext;
import com.evolveum.polygon.sql.base.SqlConnectorConfiguration;
import com.evolveum.polygon.sql.base.connection.SqlConnection;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.ThreadLocalRandom;

/**
 * H2 database initialization helper for tests.
 * <p>
 * Creates and configures an H2 test database with schema and data from resource files.
 * </p>
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * // Basic initialization
 * SqlBaseContext ctx = H2DatabaseInitializer.create();
 *
 * // Custom initialization with mode
 * SqlBaseContext ctx = H2DatabaseInitializer.create(mode -> {
 *     mode.setMode(H2Mode.POSTGRESQL);
 * });
 *
 * // Clean up
 * ctx.close();
 * }</pre>
 */
public final class H2DatabaseInitializer {

    private H2DatabaseInitializer() {
    }

    private static final String SCHEMA_RESOURCE = "h2/basic/schema.sql";
    private static final String DATA_RESOURCE = "h2/basic/data.sql";

    /**
     * Creates a fully initialized SqlBaseContext with default settings.
     * Uses the default H2 mode (standard SQL).
     *
     * @return configured SqlBaseContext ready for testing
     */
    public static SqlBaseContext create() {
        try {
            H2TestMode mode = new H2TestMode();
            return createContext(mode, null);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create test context", e);
        }
    }

    /**
     * Creates a fully initialized SqlBaseContext with the specified dialect class.
     *
     * @param dialectClass the SQL dialect class to use
     * @return configured SqlBaseContext ready for testing
     */
    public static SqlBaseContext create(Class<?> dialectClass) {
        try {
            H2TestMode mode = new H2TestMode();
            return createContext(mode, dialectClass);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create test context", e);
        }
    }

    /**
     * Creates a fully initialized SqlBaseContext with configurable mode.
     *
     * @param modeConfigurer configuration function for the database mode
     * @return configured SqlBaseContext ready for testing
     */
    public static SqlBaseContext create(java.util.function.Consumer<H2TestMode> modeConfigurer) {
        try {
            H2TestMode mode = new H2TestMode();
            if (modeConfigurer != null) {
                modeConfigurer.accept(mode);
            }
            return createContext(mode, null);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create test context", e);
        }
    }

    private static SqlBaseContext createContext(H2TestMode mode, Class<?> dialectClass) throws Exception {
        SqlConnectorConfiguration config = new SqlConnectorConfiguration();
        config.setJdbcUrl(buildUrl(mode));
        config.setUsername("sa");
        config.setPassword("");
        config.setPoolSize(5);
        config.setConnectionTimeout(10000);
        config.setIdleTimeout(30);
        config.setValidateConnectionOnBorrow(true);
        config.setAutoDiscoverSchema(false);

        SqlBaseContext ctx = new SqlBaseContext(config);
        ctx.initializeConnectionPool();

        try (SqlConnection conn = ctx.getConnection()) {
            Connection jdbc = conn.getConnection();
            executeSql(jdbc, SCHEMA_RESOURCE);
            executeSql(jdbc, DATA_RESOURCE);
        } catch (Exception e) {
            ctx.close();
            throw e;
        }

        if (dialectClass != null) {
            java.lang.reflect.Field f = SqlBaseContext.class.getDeclaredField("dialect");
            f.setAccessible(true);
            f.set(ctx, dialectClass.getDeclaredConstructor().newInstance());
        }

        return ctx;
    }

    private static String buildUrl(H2TestMode mode) {
        StringBuilder url = new StringBuilder();
        url.append("jdbc:h2:mem:test_").append(mode.uniqueId())
           .append(";DB_CLOSE_DELAY=-1");

        // Add MODE parameter if it's not the default
        String m = mode.mode();
        if (m != null && !"Standard".equals(m) && !"MySQL".equals(m)) {
            url.append(";MODE=").append(m);
        }
        if ("MySQL".equals(m)) {
            url.append(";MODE=MySQL");
        }

        return url.toString();
    }

    private static void executeSql(Connection conn, String resourcePath) throws SQLException, IOException {
        try (InputStream is = Thread.currentThread().getContextClassLoader()
                .getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new FileNotFoundException(resourcePath);
            }
            String sql = new String(is.readAllBytes());
            try (java.sql.Statement stmt = conn.createStatement()) {
                stmt.execute(sql);
            }
        }
    }

    /**
     * Test mode configuration.
     */
    public static final class H2TestMode {
        private final int id = (int) (ThreadLocalRandom.current().nextDouble() * 2147483647);
        private String mode = "MySQL";
        private boolean databaseToLower = false;
        private boolean databaseToUpper = true;
        private boolean caseInsensitiveIdentifiers = false;

        String mode() {
            return mode;
        }

        int uniqueId() {
            return id;
        }

        boolean databaseToLower() {
            return databaseToLower;
        }

        boolean databaseToUpper() {
            return databaseToUpper;
        }

        boolean caseInsensitiveIdentifiers() {
            return caseInsensitiveIdentifiers;
        }

        /**
         * Sets the H2 mode name (e.g., "MySQL", "PostgreSQL").
         *
         * @param mode the mode name
         * @return this for chaining
         */
        public H2TestMode setMode(String mode) {
            this.mode = mode;
            return this;
        }

        /**
         * Sets the H2 mode from an H2Mode enum value.
         *
         * @param mode the H2Mode value
         * @return this for chaining
         */
        public H2TestMode setMode(H2Mode mode) {
            if (mode != null) {
                switch (mode) {
                    case MYSQL:
                        this.mode = "MySQL";
                        break;
                    case POSTGRESQL:
                        this.mode = "PostgreSQL";
                        break;
                    case MARIADB:
                        this.mode = "MariaDB";
                        break;
                    case MSSQLSERVER:
                        this.mode = "MSSQLServer";
                        break;
                    case ORACLE:
                        this.mode = "Oracle";
                        break;
                    case SQLITE:
                        this.mode = "SQLite";
                        break;
                    default:
                        this.mode = "Standard";
                        break;
                }
            }
            return this;
        }

        /**
         * Sets whether database identifiers should be converted to lowercase.
         * Useful for emulating MySQL behavior.
         *
         * @param value true to convert to lowercase
         * @return this for chaining
         */
        public H2TestMode setDatabaseToLower(boolean value) {
            this.databaseToLower = value;
            return this;
        }

        /**
         * Sets whether database identifiers should be converted to uppercase.
         * Useful for emulating Oracle or PostgreSQL behavior.
         *
         * @param value true to convert to uppercase
         * @return this for chaining
         */
        public H2TestMode setDatabaseToUpper(boolean value) {
            this.databaseToUpper = value;
            return this;
        }

        /**
         * Sets whether identifiers should be case-insensitive.
         *
         * @param value true for case-insensitive identifiers
         * @return this for chaining
         */
        public H2TestMode setCaseInsensitiveIdentifiers(boolean value) {
            this.caseInsensitiveIdentifiers = value;
            return this;
        }
    }
}