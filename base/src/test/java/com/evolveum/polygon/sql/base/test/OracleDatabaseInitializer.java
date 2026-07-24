/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2.
 */
package com.evolveum.polygon.sql.base.test;

import com.evolveum.polygon.sql.base.SqlBaseContext;
import com.evolveum.polygon.sql.base.SqlConnectorConfiguration;
import org.identityconnectors.common.security.GuardedString;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Connects to an external Oracle database running on localhost:1521, service FREEPDB1, user oracle, password oracle123.
 * Manages schema + data directly on the oracle user's schema.
 * On init, loads the schema.sql from Oracle basic resources (CREATE TABLE + INSERT).
 * On close, closes the oracle connection.
 *
 * Usage:
 * <pre>{@code
 * try (OracleDatabaseInitializer oracle = OracleDatabaseInitializer.create()) {
 *     oracle.init();     // loads and applies schema.sql (DROP all + CREATE + INSERT)
 *     SqlBaseContext ctx = oracle.createContext();
 *     // Run tests against the ctx...
 * }
 * }</pre>
 */
public final class OracleDatabaseInitializer implements AutoCloseable {

    private static final String RESOURCE_SCHEMA = "oracle/basic/schema.sql";

    private final Connection conn;

    private OracleDatabaseInitializer() throws SQLException {
        this.conn = DriverManager.getConnection(
                "jdbc:oracle:thin:@//localhost:1521/FREEPDB1",
                "oracle", "oracle123");
        this.conn.setAutoCommit(true);
    }

    /**
     * Creates an OracleDatabaseInitializer.
     * Establishes a jdbc connection to the oracle DB.
     */
    public static OracleDatabaseInitializer create() throws SQLException {
        return new OracleDatabaseInitializer();
    }

    /**
     * Drops and recreates all tables by running schema.sql from scratch.
     * The schema.sql file itself contains BEGIN blocks to drop all tables, then CREATE TABLE, then INSERT.
     */
    public void init() throws SQLException, IOException {
        // Extract all table names from user_tables and drop them in a safe way
        try (var st = conn.createStatement();
             var rs = st.executeQuery(
                     "SELECT table_name FROM user_tables WHERE table_name LIKE 'DIR_%' OR table_name LIKE 'ORGCHART_%'")) {
            while (rs.next()) {
                var tb = rs.getString(1);
                try (var s = conn.createStatement()) {
                    s.execute("DROP TABLE " + tb + " CASCADE CONSTRAINTS");
                } catch (SQLException ignore) {
                    // Table might not exist
                }
            }
        }
        
        // Now run the schema.sql which will CREATE TABLE + INSERT
        // No table should exist at this point, so INSERT will succeed
        try (var sr = new InputStreamReader(
                Thread.currentThread().getContextClassLoader()
                    .getResourceAsStream(RESOURCE_SCHEMA), StandardCharsets.UTF_8)) {
            executeScript(sr, RESOURCE_SCHEMA);
        }
    }

    /**
     * Executes the SQL content from an InputStreamReader, splitting it into individual statements.
     * Handles PL/SQL blocks (BEGIN...END;) which cannot be split by semicolons.
     */
    private void executeScript(InputStreamReader ir, String resourceName) throws SQLException, IOException {
        String sql = toString(ir);
        // Strip line comments but keep the newlines for line-by-line processing
        sql = sql.replaceAll("(?m)^\\s*--.*$", "").replaceAll("(?m);\\s*-.*?$", ";") + "\n";

        List<String> statements = splitStatements(sql);
        for (String stmt : statements) {
            try (var st = conn.createStatement()) {
                st.execute(stmt);
            } catch (SQLException e) {
                // Skip "table already exists" (ORA-955) errors - tables may already be created
                if (e.getErrorCode() == 955) {
                    continue;
                }
                throw new SQLException("Failed executing in " + resourceName + ": " + stmt, e);
            }
        }
    }

    /**
     * Splits SQL content into individual statements suitable for Oracle JDBC.
     * Handles PL/SQL blocks (BEGIN...END;) which cannot be split by semicolons.
     */
    private List<String> splitStatements(String sql) {
        List<String> result = new ArrayList<>();
        List<String> lines = Arrays.asList(sql.split("\n"));

        var block = new StringBuilder();
        for (String line : lines) {
            var trimmed = line.trim();
            if (trimmed.isEmpty()) continue;
            var upper = trimmed.toUpperCase();

            if (upper.startsWith("BEGIN")) {
                // Single-line PL/SQL block (e.g. BEGIN EXECUTE IMMEDIATE ...; END;)
                if (upper.endsWith("END;")) {
                    result.add(trimmed);
                    continue;
                }
                // Multi-line block - accumulate until END;
                block = new StringBuilder(trimmed);
            } else if (upper.startsWith("END") && upper.endsWith(";")) {
                // End of PL/SQL block - add the accumulated block
                block.append("\n").append(trimmed);
                result.add(block.toString().trim());
                block = new StringBuilder();
            } else {
                // Accumulate into current block if accumulating, otherwise treat as standalone
                if (block.length() > 0) {
                    block.append("\n").append(trimmed);
                    if (trimmed.endsWith(";")) {
                        result.add(block.toString().trim());
                        block = new StringBuilder();
                    }
                } else {
                    if (trimmed.endsWith(";")) {
                        // Full statement on one line (e.g., INSERT INTO ... VALUES (...);)
                        result.add(trimmed);
                    } else {
                        // Start of multi-line statement (e.g., CREATE TABLE ( ... );)
                        block = new StringBuilder(trimmed);
                    }
                }
            }
        }
        // Handle any remaining block
        if (block.length() > 0) {
            result.add(block.toString());
        }
        return result;
    }

    /**
     * Creates a {@link SqlBaseContext} configured to connect to the Oracle database.
     */
    public SqlBaseContext createContext(boolean autoDiscoverSchema) throws Exception {
        var config = new SqlConnectorConfiguration();
        config.setJdbcUrl("jdbc:oracle:thin:@//localhost:1521/FREEPDB1");
        config.setUsername("oracle");
        config.setPassword(new GuardedString("oracle123".toCharArray()));
        config.setPoolSize(5);
        config.setConnectionTimeout(10000);
        config.setValidateConnectionOnBorrow(true);
        config.setAutoDiscoverSchema(autoDiscoverSchema);

        var ctx = new SqlBaseContext(config);
        ctx.initializeConnectionPool();
        return ctx;
    }

    /**
     * Closes the oracle connection.
     */
    @Override
    public void close() {
        try {
            if (conn != null) conn.close();
        } catch (Exception e) {
            // ignore close errors
        }
    }

    private static String toString(InputStreamReader ir) throws IOException {
        var sb = new StringBuilder();
        int c;
        while ((c = ir.read()) != -1) {
            sb.append((char) c);
        }
        return sb.toString();
    }
}