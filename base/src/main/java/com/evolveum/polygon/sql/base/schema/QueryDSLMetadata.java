/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.sql.base.schema;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Holds all the information needed to build type-safe QueryDSL queries
 * for a specific database table. Each instance represents one discovered table.
 */
public class QueryDSLMetadata {

    private final String tableName;
    private final Map<String, ColumnMeta> columns;

    /**
     * Create a new QueryDSL metadata holder.
     * @param tableName the table name
     * @param columns map of column name -> column metadata (non-nullable)
     */
    public QueryDSLMetadata(String tableName, Map<String, ColumnMeta> columns) {
        this.tableName = tableName;
        this.columns = new LinkedHashMap<>(columns);
    }

    /**
     * Get the table name.
     */
    public String getTableName() {
        return tableName;
    }

    /**
     * Get all column metadata keyed by lowercased column name.
     */
    public Map<String, ColumnMeta> getColumns() {
        return new LinkedHashMap<>(columns);
    }

    /**
     * Get column metadata for a specific column by name.
     * @param columnName case-insensitive column name
     * @return ColumnMeta or null if not found
     */
    public ColumnMeta getColumn(String columnName) {
        for (Map.Entry<String, ColumnMeta> entry : columns.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(columnName)) {
                return entry.getValue();
            }
        }
        return null;
    }

    /**
     * Get the number of columns in this table.
     */
    public int columnCount() {
        return columns.size();
    }

    /**
     * Metadata for a single column, providing type information
     * needed for QueryDSL path construction.
     */
    public static class ColumnMeta {

        private final String name;
        private final String typeName;
        private final int typeCode;
        private final int size;
        private final Class<?> javaType;
        private final boolean nullable;
        private final boolean primaryKey;
        private final boolean unique;
        private final boolean autoIncrement;
        private final String referencedTable;
        private final String referencedColumn;
        private final String foreignKeyName;

        public ColumnMeta(String name, String typeName, int typeCode, int size, Class<?> javaType,
                          boolean nullable, boolean primaryKey, boolean unique, boolean autoIncrement,
                          String referencedTable, String referencedColumn, String foreignKeyName) {
            this.name = name;
            this.typeName = typeName;
            this.typeCode = typeCode;
            this.size = size;
            this.javaType = javaType;
            this.nullable = nullable;
            this.primaryKey = primaryKey;
            this.unique = unique;
            this.autoIncrement = autoIncrement;
            this.referencedTable = referencedTable;
            this.referencedColumn = referencedColumn;
            this.foreignKeyName = foreignKeyName;
        }

        /**
         * The SQL column name.
         */
        public String getName() {
            return name;
        }

        /**
         * The SQL type name (e.g. "VARCHAR", "INT", "TIMESTAMP").
         */
        public String getTypeName() {
            return typeName;
        }

        /**
         * The JDBC type code (java.sql.Types constant).
         */
        public int getTypeCode() {
            return typeCode;
        }

        /**
         * The column size.
         */
        public int getSize() {
            return size;
        }

        /**
         * The Java type derived from JDBC type information.
         * Used to determine which QueryDSL Path type to use (StringPath, NumberPath, etc.).
         */
        public Class<?> getJavaType() {
            return javaType;
        }

        /**
         * Whether the column is nullable.
         */
        public boolean isNullable() {
            return nullable;
        }

        /**
         * Whether the column is a primary key.
         */
        public boolean isPrimaryKey() {
            return primaryKey;
        }

        /**
         * Whether the column has a unique constraint.
         */
        public boolean isUnique() {
            return unique;
        }

        /**
         * Whether the column is auto-increment.
         */
        public boolean isAutoIncrement() {
            return autoIncrement;
        }

        /**
         * If this column is a foreign key, the referenced table name, or null.
         */
        public String getReferencedTable() {
            return referencedTable;
        }

        /**
         * If this column is a foreign key, the referenced column name, or null.
         */
        public String getReferencedColumn() {
            return referencedColumn;
        }

        /**
         * If this column is a foreign key, the FK constraint name, or null.
         */
        public String getForeignKeyName() {
            return foreignKeyName;
        }
    }
}