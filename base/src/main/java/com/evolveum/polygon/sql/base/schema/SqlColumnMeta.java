/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.sql.base.schema;

/**
 * Metadata about a SQL column.
 */
public class SqlColumnMeta {

    private final String name;
    private final String typeName;
    private final int typeCode;
    private final int size;
    private final boolean nullable;
    private final boolean primaryKey;
    private final boolean unique;
    private final Object defaultValue;
    private final boolean autoIncrement;

    private String referencedTable;
    private String referencedColumn;
    private String foreignKeyName;

    public SqlColumnMeta(String name, String typeName, int typeCode, int size,
                         boolean nullable, boolean primaryKey, boolean unique,
                         Object defaultValue, boolean autoIncrement) {
        this.name = name;
        this.typeName = typeName;
        this.typeCode = typeCode;
        this.size = size;
        this.nullable = nullable;
        this.primaryKey = primaryKey;
        this.unique = unique;
        this.defaultValue = defaultValue;
        this.autoIncrement = autoIncrement;
    }

    public String getName() {
        return name;
    }

    public String getTypeName() {
        return typeName;
    }

    public int getTypeCode() {
        return typeCode;
    }

    public int getSize() {
        return size;
    }

    public boolean isNullable() {
        return nullable;
    }

    public boolean isPrimaryKey() {
        return primaryKey;
    }

    public boolean isUnique() {
        return unique;
    }

    public Object getDefaultValue() {
        return defaultValue;
    }

    public boolean isAutoIncrement() {
        return autoIncrement;
    }

    public String getReferencedTable() {
        return referencedTable;
    }

    public String getReferencedColumn() {
        return referencedColumn;
    }

    public String getForeignKeyName() {
        return foreignKeyName;
    }

    /** Marks this column as part of a foreign key pointing to {@code referencedTable.referencedColumn}. */
    public void setForeignKey(String referencedTable, String referencedColumn, String foreignKeyName) {
        this.referencedTable = referencedTable;
        this.referencedColumn = referencedColumn;
        this.foreignKeyName = foreignKeyName;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String name;
        private String typeName;
        private int typeCode;
        private int size;
        private boolean nullable;
        private boolean primaryKey;
        private boolean unique;
        private Object defaultValue;
        private boolean autoIncrement;

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder typeName(String typeName) {
            this.typeName = typeName;
            return this;
        }

        public Builder typeCode(int typeCode) {
            this.typeCode = typeCode;
            return this;
        }

        public Builder size(int size) {
            this.size = size;
            return this;
        }

        public Builder nullable(boolean nullable) {
            this.nullable = nullable;
            return this;
        }

        public Builder primaryKey(boolean primaryKey) {
            this.primaryKey = primaryKey;
            return this;
        }

        public Builder unique(boolean unique) {
            this.unique = unique;
            return this;
        }

        public Builder defaultValue(Object defaultValue) {
            this.defaultValue = defaultValue;
            return this;
        }

        public Builder autoIncrement(boolean autoIncrement) {
            this.autoIncrement = autoIncrement;
            return this;
        }

        public SqlColumnMeta build() {
            return new SqlColumnMeta(name, typeName, typeCode, size, nullable, 
                                     primaryKey, unique, defaultValue, autoIncrement);
        }
    }
}