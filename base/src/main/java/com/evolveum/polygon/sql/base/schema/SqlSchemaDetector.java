/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.sql.base.schema;

import com.evolveum.polygon.sql.base.SqlBaseContext;
import com.evolveum.polygon.sql.base.connection.SqlConnection;

import java.sql.*;
import java.util.*;

public class SqlSchemaDetector {

    private final SqlBaseContext context;
    
    // Cached table name mapping to avoid repeated JDBC metadata queries
    private Map<String, String> tableNameToExact;

    public SqlSchemaDetector(SqlBaseContext context) {
        this.context = context;
    }

    /**
     * Discovers the database schema and sets it on the context.
     * This method is idempotent and safe to call multiple times.
     */
public void discover() throws SQLException {
        try (SqlConnection wrapper = context.getConnection()) {
            Connection conn = wrapper.getConnection();
            tableNameToExact = new LinkedHashMap<>();
            
            // Single metadata query to get all tables and their exact names
            List<String> tableNames = getTableNames(conn);

            Map<String, List<SqlColumnMeta>> colMap = new LinkedHashMap<>();

            // For each table, collect column metadata (no repeated getTables() calls)
            for (String tableName : tableNames) {
                if (colMap.containsKey(tableName)) {
                    continue;
                }
                String exactName = getExactTableName(conn, tableName);
                if (exactName == null) {
                    continue;
                }
                List<SqlColumnMeta> cols = getColumnMetas(conn, exactName);
                if (!cols.isEmpty()) {
                    colMap.put(tableName, cols);
                }
            }

            // Build and set the schema
            List<SqlTableInfo> tables = new ArrayList<>();
            for (Map.Entry<String, List<SqlColumnMeta>> entry : colMap.entrySet()) {
                tables.add(SqlTableInfo.builder()
                        .name(entry.getKey())
                        .tableType("TABLE")
                        .columns(entry.getValue())
                        .build());
            }

            context.schema(new SqlSchema(tables));
            tableNameToExact = null;
        }
    }

    /**
     * Retrieves all user table names (lowercase) from the database,
     * filtering out system/INFORMATION_SCHEMA tables.
     */
    private List<String> getTableNames(Connection conn) throws SQLException {
        List<String> names = new ArrayList<>();
        try (ResultSet rs = conn.getMetaData().getTables(null, null, "%", new String[]{"TABLE"})) {
            ResultSetMetaData meta = rs.getMetaData();
            while (rs.next()) {
                String schema = resolveColumn(rs, meta, "TABLE_SCHEM");
                if (schema != null && schema.equalsIgnoreCase("information_schema")) {
                    continue;
                }
                String name = resolveColumn(rs, meta, "TABLE_NAME");
                if (name != null) {
                    String lowerName = name.toLowerCase();
                    names.add(lowerName);
                    if (tableNameToExact != null) {
                        tableNameToExact.put(lowerName, name);
                    }
                }
            }
        }
        return names;
    }

    /**
     * Resolves a column from ResultSet using column metadata without throwing on missing columns.
     */
    private String resolveColumn(ResultSet rs, ResultSetMetaData meta, String columnName) throws SQLException {
        try {
            int colIndex = 0;
            for (int i = 1; i <= meta.getColumnCount(); i++) {
                if (columnName.equalsIgnoreCase(meta.getColumnName(i))) {
                    colIndex = i;
                    break;
                }
            }
            if (colIndex > 0) {
                return rs.getString(colIndex);
            }
        } catch (SQLException e) {
            // Column not supported by this driver, return null
        }
        return null;
    }

    /**
     * Resolves the exact-cased table name from the cache populated during getTableNames().
     * Falls back to querying if not cached (defensive).
     */
    private String getExactTableName(Connection conn, String lowerName) throws SQLException {
        // First try the cache
        if (tableNameToExact != null) {
            String cached = tableNameToExact.get(lowerName);
            if (cached != null) {
                return cached;
            }
        }
        // Fallback: re-query (should not happen in normal flow)
        try (ResultSet rs = conn.getMetaData().getTables(null, null, "%", new String[]{"TABLE"})) {
            while (rs.next()) {
                String schema = resolveColumn(rs, rs.getMetaData(), "TABLE_SCHEM");
                if (schema != null && schema.equalsIgnoreCase("information_schema")) {
                    continue;
                }
                String name = resolveColumn(rs, rs.getMetaData(), "TABLE_NAME");
                if (name != null && name.toLowerCase().equals(lowerName)) {
                    return name;
                }
            }
        }
        return null;
    }

    /**
     * Collects column metadata (name, type, nullable, PK, auto-increment, unique)
     * for a specific table.
     */
    private List<SqlColumnMeta> getColumnMetas(Connection conn, String exactName) throws SQLException {
        List<SqlColumnMeta> cols = new ArrayList<>();
        // Use try-with-resources: multiple ResultSets on the same Connection is supported in JDBC 4.0+
        try (ResultSet pkRs = conn.getMetaData().getPrimaryKeys(null, null, exactName);
             ResultSet idxRs = conn.getMetaData().getIndexInfo(null, null, exactName, false, false)) {

            List<String> pkList = new ArrayList<>();
            while (pkRs.next()) {
                String colName = resolveColumn(pkRs, pkRs.getMetaData(), "COLUMN_NAME");
                if (colName != null) {
                    pkList.add(colName.toLowerCase());
                }
            }

            Set<String> uniqueCols = collectUniqueConstraintColumns(conn, exactName);

            try (ResultSet colsRs = conn.getMetaData().getColumns(null, null, exactName, null)) {
                ResultSetMetaData meta = colsRs.getMetaData();
                while (colsRs.next()) {
                    String colName = resolveColumn(colsRs, meta, "COLUMN_NAME");
                    if (colName == null) {
                        continue;
                    }

                    String typeName = resolveColumn(colsRs, meta, "TYPE_NAME");
                    String rawNullable = resolveColumn(colsRs, meta, "IS_NULLABLE");
                    String rawAutoInc = resolveColumn(colsRs, meta, "IS_AUTOINCREMENT");

                    String colLower = colName.toLowerCase();
                    boolean isPk = pkList.contains(colLower);

                    cols.add(SqlColumnMeta.builder()
                            .name(colLower)
                            .typeName(resolverType(typeName))
                            .nullable(isNullable(rawNullable))
                            .primaryKey(isPk)
                            .autoIncrement(isAutoInc(rawAutoInc))
                            .unique(isPk || uniqueCols.contains(colLower))
                            .defaultValue(null)
                            .build());
                }
            }
        }
        return cols;
    }

    /**
     * Collects column names that have unique constraints by scanning index metadata.
     */
    private Set<String> collectUniqueConstraintColumns(Connection conn, String exactName) throws SQLException {
        Set<String> uniqueCols = new HashSet<>();
        try (ResultSet rs = conn.getMetaData().getIndexInfo(null, null, exactName, false, false)) {
            ResultSetMetaData meta = rs.getMetaData();
            while (rs.next()) {
                String isUniqueStr = resolveColumn(rs, meta, "NON_UNIQUE");
                boolean isNonUnique = resolveNonUnique(isUniqueStr);
                if (!isNonUnique) {  // This is a unique index
                    String colName = resolveColumn(rs, meta, "COLUMN_NAME");
                    if (colName != null) {
                        uniqueCols.add(colName.toLowerCase());
                    }
                }
            }
        }
        return uniqueCols;
    }

    /**
     * Resolves JDBC getIndexes NON_UNIQUE column value.
     * JDBC spec says: 0 (false/zero) = unique, 1 (true/non-zero or non-zero numeric) = not unique.
     * Various drivers return different types: Integer, String "0"/"1"/"false"/"true", etc.
     */
    private boolean resolveNonUnique(String raw) {
        if (raw == null) {
            return true;  // default to non-unique (safe assumption)
        }
        String trimmed = raw.trim();
        // Numeric check: "0" or "FALSE" or "0.0" = unique, "1" or "TRUE" or any other = non-unique
        try {
            double numVal = Double.parseDouble(trimmed);
            return !(numVal == 0.0);
        } catch (NumberFormatException e) {
            // String values: "false", "FALSE", "False" are unique. "true", "TRUE", "True" are non-unique
            // Everything else defaults to non-unique (safe)
            return !trimmed.equalsIgnoreCase("false");
        }
    }

    private static boolean isNullable(String raw) {
        if (raw == null) {
            return true;
        }
        return raw.toUpperCase().equals("YES");
    }

    private static boolean isAutoInc(String raw) {
        if (raw == null) {
            return false;
        }
        return raw.toUpperCase().equals("YES");
    }

    /**
     * Maps JDBC TYPE_NAME values (which vary by driver) to standard SQL type names.
     * Common type names used by JDBC metadata include: INTEGER, VARCHAR, BLOB, TIMESTAMP, etc.
     */
    private String resolverType(String dt) {
        if (dt == null) {
            return "VARCHAR";
        }
        String u = dt.toUpperCase().trim();

        switch (u) {
            case "INTEGER":
            case "INT": return "INT";
            case "BIGINT": return "BIGINT";
            case "SMALLINT": return "SMALLINT";
            case "TINYINT": return "TINYINT";
            case "TIMESTAMP": return "TIMESTAMP";
            case "DATE": return "DATE";
            case "TIME": return "TIME";
            case "BOOLEAN": return "BOOLEAN";
            case "BLOB": return "BLOB";
            case "CLOB": return "CLOB";
            case "DOUBLE PRECISION": return "DOUBLE";
            case "DECIMAL": return "DECIMAL";
            case "NUMERIC": return "NUMERIC";
            case "CHARACTER VARYING": return "VARCHAR";
            case "CHARACTER": return "VARCHAR";
            case "VARBINARY": return "VARBINARY";
            default: return "VARCHAR";
        }
    }
}