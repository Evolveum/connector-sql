/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.sql.base.schema;

import com.evolveum.polygon.sql.base.SqlBaseContext;
import com.querydsl.sql.Configuration;
import com.querydsl.sql.SQLTemplates;
import com.querydsl.sql.SQLTemplatesRegistry;

import java.sql.*;
import java.util.*;

public class SqlSchemaDetector {

    private final SqlBaseContext context;
    
    // Cached table name mapping to avoid repeated JDBC metadata queries
    private Map<String, String> tableNameToExact;
    
    // QueryDSL Configuration for type mapping
    private Configuration querydslConfig;
    
    // QueryDSL SQL templates, captured once during discovery (initialized during discover())
    private SQLTemplates templates;

    public SqlSchemaDetector(SqlBaseContext context) {
        this.context = context;
    }

    /**
     * Discovers the database tables (with columns, keys and references) from JDBC metadata.
     * This method is idempotent and safe to call multiple times.
     */
    public List<SqlTableInfo> discover() throws SQLException {
        try (var wrapper = context.getConnection()) {
            var conn = wrapper.getConnection();
            var meta = conn.getMetaData();
            
// Initialize QueryDSL configuration for driver-aware type mapping
            var templatesFromRegistry = new SQLTemplatesRegistry().getTemplates(meta);
            if (templatesFromRegistry == null) {
                templatesFromRegistry = SQLTemplates.DEFAULT;
            }
// For H2, use H2Templates with no quoting - unqualified column paths avoid table.column issues
            var productName = meta.getDatabaseProductName();
            if (productName != null && productName.toUpperCase().contains("H2")) {
                templatesFromRegistry = new com.querydsl.sql.H2Templates(false);
            }
            templates = templatesFromRegistry;
            querydslConfig = new Configuration(templates);
            
            tableNameToExact = new LinkedHashMap<>();
            
            // Single metadata query to get all tables and their exact names
            List<String> tableNames = getTableNames(conn);

            Map<String, List<SqlColumnMeta>> colMap = new LinkedHashMap<>();

            // For each table, collect column metadata (no repeated getTables() calls)
            for (String tableName : tableNames) {
                if (colMap.containsKey(tableName)) {
                    continue;
                }
                var exactName = getExactTableName(conn, tableName);
                if (exactName == null) {
                    continue;
                }
                List<SqlColumnMeta> cols = getColumnMetas(conn, exactName);
                if (!cols.isEmpty()) {
                    colMap.put(tableName, cols);
                }
            }

            List<SqlTableInfo> tables = new ArrayList<>();
            for (Map.Entry<String, List<SqlColumnMeta>> entry : colMap.entrySet()) {
                // Use the exact-cased name from metadata to ensure correct QueryDSL SQL generation.
                // For H2 (even MySQL mode), quoted table names like "User" must be referenced 
                // with matching case in SQL queries.
                String actualName = tableNameToExact != null 
                        && tableNameToExact.containsKey(entry.getKey())
                        ? tableNameToExact.get(entry.getKey()) : entry.getKey();
                tables.add(SqlTableInfo.builder()
                        .name(actualName)
                        .tableType("TABLE")
                        .columns(entry.getValue())
                        .build());
            }

            tableNameToExact = null;
            querydslConfig = null;  // allow GC after discovery
            return tables;
        }
    }

    /**
     * Retrieves all user table names (lowercase) from the database,
     * filtering out system/INFORMATION_SCHEMA tables.
     */
    private List<String> getTableNames(Connection conn) throws SQLException {
        List<String> names = new ArrayList<>();
        try (var rs = conn.getMetaData().getTables(null, null, "%", new String[]{"TABLE"})) {
            var meta = rs.getMetaData();
            while (rs.next()) {
                var schema = resolveColumn(rs, meta, "TABLE_SCHEM");
                if (schema != null && schema.equalsIgnoreCase("information_schema")) {
                    continue;
                }
                var name = resolveColumn(rs, meta, "TABLE_NAME");
                if (name != null) {
                    var lowerName = name.toLowerCase();
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
            var cached = tableNameToExact.get(lowerName);
            if (cached != null) {
                return cached;
            }
        }
        // Fallback: re-query (should not happen in normal flow)
        try (var rs = conn.getMetaData().getTables(null, null, "%", new String[]{"TABLE"})) {
            while (rs.next()) {
                var schema = resolveColumn(rs, rs.getMetaData(), "TABLE_SCHEM");
                if (schema != null && schema.equalsIgnoreCase("information_schema")) {
                    continue;
                }
                var name = resolveColumn(rs, rs.getMetaData(), "TABLE_NAME");
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
        try (var pkRs = conn.getMetaData().getPrimaryKeys(null, null, exactName);
             var idxRs = conn.getMetaData().getIndexInfo(null, null, exactName, false, false)) {

            List<String> pkList = new ArrayList<>();
            while (pkRs.next()) {
                var colName = resolveColumn(pkRs, pkRs.getMetaData(), "COLUMN_NAME");
                if (colName != null) {
                    pkList.add(colName.toLowerCase());
                }
            }

            Set<String> uniqueCols = collectUniqueConstraintColumns(conn, exactName);

            try (var colsRs = conn.getMetaData().getColumns(null, null, exactName, null)) {
                var meta = colsRs.getMetaData();
                while (colsRs.next()) {
                    var colName = resolveColumn(colsRs, meta, "COLUMN_NAME");
                    if (colName == null) {
                        continue;
                    }

                    var typeName = resolveColumn(colsRs, meta, "TYPE_NAME");
                    int dataType = colsRs.getInt("DATA_TYPE");
                    int columnSize = resolveColumnSize(colsRs);
                    int decimalDigits = resolveColumnDigits(colsRs);
                    var rawNullable = resolveColumn(colsRs, meta, "IS_NULLABLE");
                    var rawAutoInc = resolveColumn(colsRs, meta, "IS_AUTOINCREMENT");

                    var colLower = colName.toLowerCase();
                    boolean isPk = pkList.contains(colLower);

                    // Use QueryDSL for Java type resolution (dialect-aware)
                    var javaType = resolveJavaType(dataType, typeName, columnSize, decimalDigits, exactName, colLower);
                    
                    // Normalize type name using driver-typical TYPE_NAME (with fallback normalization)
                    var normalizedTypeName = normalizeTypeName(typeName);

                    cols.add(SqlColumnMeta.builder()
                            .name(colLower)
                            .typeName(normalizedTypeName)
                            .typeCode(dataType)
                            .size(columnSize)
                            .javaType(javaType)
                            .nullable(isNullable(rawNullable))
                            .primaryKey(isPk)
                            .autoIncrement(isAutoInc(rawAutoInc))
                            .unique(isPk || uniqueCols.contains(colLower))
                            .defaultValue(null)
                            .build());
                }
            }

            // Foreign keys: attach the referenced table/column (grouped by FK name) to the FK columns,
            // so a reference can be expressed on the attribute (supports composite keys).
            try (var fkRs = conn.getMetaData().getImportedKeys(null, null, exactName)) {
                while (fkRs.next()) {
                    var fkColumn = resolveColumn(fkRs, fkRs.getMetaData(), "FKCOLUMN_NAME");
                    var pkTable = resolveColumn(fkRs, fkRs.getMetaData(), "PKTABLE_NAME");
                    var pkColumn = resolveColumn(fkRs, fkRs.getMetaData(), "PKCOLUMN_NAME");
                    var fkName = resolveColumn(fkRs, fkRs.getMetaData(), "FK_NAME");
                    if (fkColumn == null || pkTable == null) {
                        continue;
                    }
                    for (SqlColumnMeta col : cols) {
                        if (col.getName().equalsIgnoreCase(fkColumn)) {
                            col.setForeignKey(pkTable.toLowerCase(),
                                    pkColumn != null ? pkColumn.toLowerCase() : null, fkName);
                        }
                    }
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
        try (var rs = conn.getMetaData().getIndexInfo(null, null, exactName, false, false)) {
            var meta = rs.getMetaData();
            while (rs.next()) {
                var isUniqueStr = resolveColumn(rs, meta, "NON_UNIQUE");
                boolean isNonUnique = resolveNonUnique(isUniqueStr);
                if (!isNonUnique) {  // This is a unique index
                    var colName = resolveColumn(rs, meta, "COLUMN_NAME");
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
        var trimmed = raw.trim();
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
     * Preserves driver-typical type names for SQL type name consistency.
     */
    private String normalizeTypeName(String dt) {
        if (dt == null) {
            return "VARCHAR";
        }
        var u = dt.toUpperCase().trim();

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

    /**
     * Resolves the canonical Java type from JDBC metadata using QueryDSL's
     * driver-aware {@link Configuration}. Returns {@link Object#class} if unavailable.
     */
    private java.lang.reflect.Type resolveJavaType(int dataType, String typeName, int columnSize,
                                                   int decimalDigits, String tableName,
                                                   String columnName) {
        if (querydslConfig == null || dataType == 0) {
            return java.sql.Types.class;  // fallback when metadata is missing
        }
        try {
            int size = (columnSize > 0) ? columnSize : 0;
            int digits = (decimalDigits > 0) ? decimalDigits : 0;
            Class<?> clazz = querydslConfig.getJavaType(dataType, typeName, size, digits, tableName, columnName);
            return clazz != null ? clazz : java.lang.Object.class;
        } catch (Exception e) {
            return java.lang.Object.class;
        }
    }

    private int resolveColumnSize(ResultSet rs) {
        try {
            var val = rs.getObject("COLUMN_SIZE");
            if (val instanceof Number number) {
                return number.intValue();
            } else if (val instanceof String string) {
                try {
                    return Integer.parseInt(string);
                } catch (NumberFormatException e) {
                    return 0;
                }
            }
        } catch (SQLException ignored) {
            // Column not supported by this driver
        }
        return 0;
    }

    private int resolveColumnDigits(ResultSet rs) {
        try {
            var val = rs.getObject("DECIMAL_DIGITS");
            if (val instanceof Number number) {
                return number.intValue();
            } else if (val instanceof String string) {
                try {
                    return Integer.parseInt(string);
                } catch (NumberFormatException e) {
                    return 0;
                }
            }
        } catch (SQLException ignored) {
            // Column not supported by this driver
        }
        return 0;
    }

    /**
     * Returns the QueryDSL SQL templates that were captured during schema discovery,
     * or null if discovery has not yet been run. These templates are needed for
     * QueryDSL query building and must be used together with the discovered tables.
     */
    public SQLTemplates getSQLTemplates() {
        return templates;
    }

}