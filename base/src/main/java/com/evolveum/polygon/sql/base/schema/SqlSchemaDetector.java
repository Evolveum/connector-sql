/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.sql.base.schema;

import com.evolveum.polygon.sql.base.SqlBaseContext;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Detects database schema from JDBC metadata.
 * Populates SqlSchema with tables and columns.
 */
public class SqlSchemaDetector {

    private final SqlBaseContext context;
    private Connection connection;

    public SqlSchemaDetector(SqlBaseContext context) {
        this.context = context;
    }

    public void discover() {
        try {
            connection = context.getConnection().getConnection();
            DatabaseMetaData metaData = connection.getMetaData();

            List<SqlTableInfo> tables = new ArrayList<>();

            try (ResultSet rs = metaData.getTables(
                    context.configuration().getJdbcUrl(), 
                    "%", "%", 
                    new String[]{"TABLE"})) {
                while (rs.next()) {
                    String tableName = rs.getString("TABLE_NAME");
                    String tableSchema = rs.getString("TABLE_SCHEM");
                    String tableType = rs.getString("TABLE_TYPE");
                    String remarks = rs.getString("REMARKS");

                    SqlTableInfo table = discoverTableMetaData(tableName, tableSchema, metaData);
                    tables.add(table);
                }
            }

            SqlSchema schema = new SqlSchema(tables);
            context.schema(schema);

        } catch (SQLException e) {
            throw new RuntimeException("Failed to detect schema: " + e.getMessage(), e);
        } finally {
            if (connection != null) {
                try {
                    connection.close();
                } catch (SQLException e) {
                    // Ignore
                }
            }
        }
    }

    private SqlTableInfo discoverTableMetaData(String tableName, String schema, DatabaseMetaData metaData) 
            throws SQLException {
        List<SqlColumnMeta> columns = new ArrayList<>();

        try (ResultSet rs = metaData.getColumns(
                null, schema, tableName, "%")) {
            while (rs.next()) {
                String columnName = rs.getString("COLUMN_NAME");
                String typeName = rs.getString("TYPE_NAME");
                int typeCode = rs.getInt("DATA_TYPE");
                int size = rs.getInt("COLUMN_SIZE");
                int nullableCode = rs.getInt("NULLABLE");
                boolean nullable = nullableCode == DatabaseMetaData.columnNullable || 
                                   nullableCode == DatabaseMetaData.columnNullableUnknown;
                String isNullableStr = rs.getString("IS_NULLABLE");
                if ("YES".equals(isNullableStr)) {
                    nullable = true;
                }

                boolean primaryKey = isPrimaryKey(tableName, schema, columnName, metaData);
                boolean unique = isUnique(tableName, schema, columnName, metaData);
                Object defaultValue = rs.getObject("COLUMN_DEF");
                boolean autoIncrement = "YES".equalsIgnoreCase(rs.getString("IS_AUTOINCREMENT"));

                SqlColumnMeta column = new SqlColumnMeta(
                        columnName, typeName, typeCode, size, nullable, 
                        primaryKey, unique, defaultValue, autoIncrement
                );
                columns.add(column);
            }
        }

        return SqlTableInfo.builder()
                .name(tableName)
                .schema(schema)
                .tableType("TABLE")
                .columns(columns)
                .build();
    }

    private boolean isPrimaryKey(String tableName, String schema, String columnName, DatabaseMetaData metaData) 
            throws SQLException {
        try (ResultSet rs = metaData.getPrimaryKeys(null, schema, tableName)) {
            while (rs.next()) {
                if (columnName.equals(rs.getString("COLUMN_NAME"))) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isUnique(String tableName, String schema, String columnName, DatabaseMetaData metaData) 
            throws SQLException {
        try (ResultSet rs = metaData.getIndexInfo(null, schema, tableName, false, false)) {
            while (rs.next()) {
                if (columnName.equals(rs.getString("COLUMN_NAME")) && 
                    "PSI".equals(rs.getString("TYPE"))) {
                    return rs.getInt("NON_UNIQUE") == 0;
                }
            }
        }
        return false;
    }
}