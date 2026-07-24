/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.sql.base.schema;

import com.evolveum.polygon.sql.base.SqlBaseContext;
import com.evolveum.polygon.sql.base.connection.SqlSchemaValueMapping;
import com.querydsl.sql.Configuration;
import com.querydsl.sql.H2Templates;
import com.querydsl.sql.SQLTemplates;
import com.querydsl.sql.SQLTemplatesRegistry;

import java.sql.*;
import java.util.*;

public class SqlSchemaDetector {

    // FIXME: Probably should be keyed by product
    private static Set<String> SCHEMAS_TO_SKIP = Set.of(
            "information_schema",
            "sys", // ORACLE system schema
            "system", // ORACLE system schema
            "xdb"
    );


    private final SqlBaseContext context;

    // QueryDSL Configuration for type mapping
    private Configuration querydslConfig;
    
    // QueryDSL SQL templates, captured once during discovery (initialized during discover())
    // Schema name to scope metadata queries (e.g., "ORACLE" for Oracle Free)
    private String userSchema;

    private SQLTemplates templates;

    public SqlSchemaDetector(SqlBaseContext context) {
        this.context = context;
    }

    /**
     * Discovers the database tables (with columns, keys and references) from JDBC metadata.
     * This method is idempotent and safe to call multiple times.
     */
    public List<SqlTableInfo> discover() throws SQLException {
        try (var wrapper =  context.getConnection()) {
            var conn = wrapper.getConnection();
            var meta = conn.getMetaData();
            var templatesFromRegistry = new SQLTemplatesRegistry().getTemplates(meta);
            if (templatesFromRegistry == null) {
                templatesFromRegistry = SQLTemplates.DEFAULT;
            }

            // For H2, use H2Templates with no quoting - unqualified column paths avoid table.column issues
            var productName = meta.getDatabaseProductName();
            if (productName != null && productName.toUpperCase().contains("H2")) {
                templatesFromRegistry = new H2Templates(false);
            }
            templates = templatesFromRegistry;
            querydslConfig = new Configuration(templates);

            List<Table> tableNames = getTableList(conn, null);

            Map<Table, List<SqlColumnMeta>> colMap = new LinkedHashMap<>();

            // For each table, collect column metadata (no repeated getTables() calls)
            for (Table table : tableNames) {
                if (colMap.containsKey(table)) {
                    continue;
                }
                List<SqlColumnMeta> cols = getColumnMetas(conn, table);
                if (!cols.isEmpty()) {
                    colMap.put(table, cols);
                }
            }

            List<SqlTableInfo> tables = new ArrayList<>();
            for (Map.Entry<Table, List<SqlColumnMeta>> entry : colMap.entrySet()) {
                // Use the exact-cased name from metadata to ensure correct QueryDSL SQL generation.
                // For H2 (even MySQL mode), quoted table names like "User" must be referenced
                // with matching case in SQL queries.
                tables.add(SqlTableInfo.builder()
                                .schema(entry.getKey().schema())
                        .name(entry.getKey().table())
                        .tableType("TABLE")
                        .columns(entry.getValue())
                        .build());
            }
            return tables;

        }
    }

    /**
     * Retrieves all user table names (lowercase) from the database,
     * filtering out system/INFORMATION_SCHEMA tables.
     */
    private List<Table> getTableList(Connection conn, String schemaName) throws SQLException {
        List<Table> names = new ArrayList<>();
        try (var rs = conn.getMetaData().getTables(null, schemaName, "%", new String[]{"TABLE", "VIEW"})) {
            var meta = rs.getMetaData();
            while (rs.next()) {
                // TABLE_SCHEM is not typo, but actual column name
                var schema = resolveColumn(rs, meta, "TABLE_SCHEM");
                if (schema != null && SCHEMAS_TO_SKIP.contains(schema.toLowerCase())) {
                    continue;
                }
                var name = resolveColumn(rs, meta, "TABLE_NAME");
                if (name != null) {
                    names.add(new Table(schema,name));
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
     * Collects column metadata (name, type, nullable, PK, auto-increment, unique)
     * for a specific table.
     */
    private List<SqlColumnMeta> getColumnMetas(Connection conn, Table table) throws SQLException {
        List<SqlColumnMeta> cols = new ArrayList<>();
        // Use try-with-resources: multiple ResultSets on the same Connection is supported in JDBC 4.0+
        try (var pkRs = conn.getMetaData().getPrimaryKeys(null, table.schema(), table.table())) {
            List<String> pkList = new ArrayList<>();
            while (pkRs.next()) {
                var colName = resolveColumn(pkRs, pkRs.getMetaData(), "COLUMN_NAME");
                if (colName != null) {
                    pkList.add(colName.toLowerCase());
                }
            }

            Set<String> uniqueCols = collectUniqueConstraintColumns(conn, table);

            try (var colsRs = conn.getMetaData().getColumns(null, table.schema(), table.table(), null)) {
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
                    var javaType = resolveJavaType(dataType, typeName, columnSize, decimalDigits, table.table(), colLower);

                    // Normalize type name using driver-typical TYPE_NAME (with fallback normalization)
                    var normalizedTypeName = normalizeTypeName(typeName);

                    // Resolve value mapping: prefer QueryDSL Java type, fall back to JDBC type code
                    var valueMapping = resolveValueMapping(javaType, dataType);

                    cols.add(SqlColumnMeta.builder()
                            .name(colLower)
                            .typeName(normalizedTypeName)
                            .typeCode(dataType)
                            .size(columnSize)
                            .javaType(javaType)
                            .valueMapping(valueMapping)
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
            try (var fkRs = conn.getMetaData().getImportedKeys(null, table.schema, table.table)) {
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
    private Set<String> collectUniqueConstraintColumns(Connection conn, Table table) throws SQLException {
        Set<String> uniqueCols = new HashSet<>();
        try (var rs = conn.getMetaData().getIndexInfo(null, table.schema(), table.table(), false, false)) {
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

        return switch (u) {
            case "INTEGER" -> "INT";
            case "BIGINT" -> "BIGINT";
            case "DOUBLE PRECISION" -> "DOUBLE";
            case "NUMBER" -> "NUMERIC";
            case "CHAR", "CHAR2", "CHARACTER VARYING", "CHARACTER" -> "VARCHAR";
            case "VARBINARY" -> "VARBINARY";
            default -> u;
        };
    }

    /**
     * Resolves the canonical Java type from JDBC metadata using QueryDSL's
     * driver-aware {@link Configuration}. Returns {@link Object#class} if unavailable.
     */
    private java.lang.reflect.Type resolveJavaType(int dataType, String typeName, int columnSize,
                                                   int decimalDigits, String tableName,
                                                   String columnName) {
        if (querydslConfig == null || dataType == 0) {
            return Types.class;  // fallback when metadata is missing
        }
        try {
            Class<?> clazz = querydslConfig.getJavaType(dataType, typeName, columnSize, decimalDigits, tableName, columnName);
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
     * Resolves the {@link SqlSchemaValueMapping} for a column, using QueryDSL's
     * dialect-aware Java type as the primary lookup key, with JDBC type code as fallback.
     */
    private SqlSchemaValueMapping resolveValueMapping(java.lang.reflect.Type javaType, int dataType) {
        if (javaType instanceof Class<?> clazz) {
            var mapped = SqlSchemaValueMapping.fromQdslJavaType(clazz);
            if (mapped != null) {
                return mapped;
            }
        }
        return SqlSchemaValueMapping.fromJdbcType(dataType);
    }

    /**
     * Returns the QueryDSL SQL templates that were captured during schema discovery,
     * or null if discovery has not yet been run. These templates are needed for
     * QueryDSL query building and must be used together with the discovered tables.
     */
    public SQLTemplates getSQLTemplates() {
        return templates;
    }

    record Table(String schema, String table) {}
}