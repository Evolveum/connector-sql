package com.evolveum.polygon.sql.base.test;

/**
 * H2 database modes for testing SQL dialects.
 *
 * <p>Provides a comprehensive set of H2 database modes to emulate different
 * SQL database systems. This allows writing tests that exercise SQL generation,
 * schema detection, and query behavior across multiple database targets.</p>
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * // Create initialization using MySQL mode
 * H2DatabaseInitializer init = H2DatabaseInitializer.init(H2Mode.MYSQL);
 * SqlBaseContext context = init.getContext();
 * }</pre>
 *
 * @see H2DatabaseInitializer
 * @since 0.1
 */
public enum H2Mode {

    /**
     * MySQL mode.
     *
     * <p>H2 in MySQL mode uses specific MySQL-compatible SQL syntax,
     * column definitions, and behavior.</p>
     *
     * <p>Suitable for testing MySQL-specific SQL patterns.</p>
     */
    MYSQL("MySQL", true, false, true),

    /**
     * PostgreSQL mode.
     *
     * <p>H2 emulates PostgreSQL syntax and behavior.
     * Most PostgreSQL features are supported, though some
     * advanced features may differ.</p>
     *
     * <p>Suitable for testing PostgreSQL-specific SQL patterns
     * such as RETURNING clauses and array handling.</p>
     */
    POSTGRESQL("PostgreSQL", true, true, false),

    /**
     * MariaDB mode.
     *
     * <p>MariaDB is largely MySQL-compatible, but H2 may
     * apply slightly different behavior or defaults.</p>
     *
     * <p>Use when specifically testing MariaDB-specific features.</p>
     */
    MARIADB("MARIADB", true, false, true),

    /**
     * SQL Server (MSSQL) mode.
     *
     * <p>H2 emulates Microsoft SQL Server syntax with some
     * simplifications for in-memory testing.</p>
     *
     * <p>Suitable for testing T-SQL patterns and SQL Server
     * specific features.</p>
     */
    MSSQLSERVER("MSSQLServer", true, true, true),

    /**
     * Oracle mode.
     *
     * <p>H2 emulates Oracle syntax and behavior, including
     * sequence handling and specific Oracle SQL features.</p>
     *
     * <p>Suitable for testing Oracle-specific SQL patterns,
     * including WITH clauses and specific Oracle date
     * handling.</p>
     */
    ORACLE("Oracle", true, true, false),

    /**
     * SQLite mode.
     *
     * <p>H2 in SQLite mode uses SQLite-compatible syntax and behavior.</p>
     *
     * <p>Suitable for testing SQLite-specific SQL patterns.</p>
     */
    SQLITE("SQLite", true, false, true);

    private final String h2Mode;
    private final boolean databaseToLower;
    private final boolean databaseToUpper;
    private final boolean caseInsensitiveIdentifiers;

    /**
     * Creates a new H2 mode with the specified configuration.
     *
     * @param h2Mode the H2 mode label
     * @param databaseToLower whether database identifiers should be lowercased
     * @param databaseToUpper whether database identifiers should be uppercased
     * @param caseInsensitiveIdentifiers whether identifiers are case-insensitive
     */
    H2Mode(
            String h2Mode,
            boolean databaseToLower,
            boolean databaseToUpper,
            boolean caseInsensitiveIdentifiers) {
        this.h2Mode = h2Mode;
        this.databaseToLower = databaseToLower;
        this.databaseToUpper = databaseToUpper;
        this.caseInsensitiveIdentifiers = caseInsensitiveIdentifiers;
    }

    /**
     * Returns the H2 mode label for use in JDBC URL configuration.
     *
     * @return the mode value (e.g., "MySQL", "PostgreSQL")
     */
    String getH2ModeLabel() {
        return h2Mode;
    }

    /**
     * Returns whether identifiers should be converted to lowercase.
     */
    boolean isDatabaseToLower() {
        return databaseToLower;
    }

    /**
     * Returns whether identifiers should be converted to uppercase.
     */
    boolean isDatabaseToUpper() {
        return databaseToUpper;
    }

    /**
     * Returns whether identifiers are case-insensitive.
     */
    boolean isCaseInsensitiveIdentifiers() {
        return caseInsensitiveIdentifiers;
    }

    /**
     * Builds the JDBC URL parameters for this mode.
     *
     * @return URL parameters string (e.g., "MODE=MySQL;DATABASE_TO_LOWER=true;...")
     */
    String toJdbcUrlParams() {
        return String.format(
                "MODE=%s;DATABASE_TO_LOWER=%s;DATABASE_TO_UPPER=%s;CASE_INSENSITIVE_IDENTIFIERS=%s",
                h2Mode,
                databaseToLower,
                databaseToUpper,
                caseInsensitiveIdentifiers);
    }
}