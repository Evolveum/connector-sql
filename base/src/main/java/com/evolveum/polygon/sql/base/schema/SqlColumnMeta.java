package com.evolveum.polygon.sql.base.schema;

import com.evolveum.polygon.sql.base.connection.SqlSchemaValueMapping;

/**
 * Represents a column in a JDBC-detected table.
 *
 * <p>Populated from JDBC database metadata during schema discovery.
 * Used by SqlSchemaTranslator to produce ConnId schema attributes,
 * and by SqlObjectClassMapping at runtime to build queries.</p>
 */
public class SqlColumnMeta {

    final String name;
    final String typeName;
    final int typeCode;
    final int size;
    final java.lang.reflect.Type javaType;
    final boolean nullable;
    final boolean primaryKey;
    final boolean unique;
    final Object defaultValue;
    final boolean autoIncrement;

    private String referencedTable;
    private String referencedColumn;
    private String foreignKeyName;
    private SqlSchemaValueMapping valueMapping;

    public SqlColumnMeta(String name, String typeName, int typeCode, int size,
                          java.lang.reflect.Type javaType,
                          boolean nullable, boolean primaryKey, boolean unique,
                          Object defaultValue, boolean autoIncrement) {
        this.name = name;
        this.typeName = typeName;
        this.typeCode = typeCode;
        this.size = size;
        this.javaType = javaType;
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

    public java.lang.reflect.Type getJavaType() {
        return javaType;
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

    /** Resolved value mapping from QueryDSL Java type + JDBC type code. Set by {@link com.evolveum.polygon.sql.base.schema.SqlSchemaDetector}. */
    public SqlSchemaValueMapping getValueMapping() {
        return valueMapping;
    }

    /** Sets the resolved value mapping for this column. */
    public void setValueMapping(SqlSchemaValueMapping valueMapping) {
        this.valueMapping = valueMapping;
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Returns the effective column type name as a human-readable SQL type (normalized).
     */
    public String getEffectiveTypeName() {
        return typeName != null ? typeName : "VARCHAR";
    }

    /**
     * Builder for SqlColumnMeta.
     */
    public static class Builder {
        private String name;
        private String typeName;
        private int typeCode;
        private int size;
        private java.lang.reflect.Type javaType;
        private boolean nullable;
        private boolean primaryKey;
        private boolean unique;
        private Object defaultValue;
        private boolean autoIncrement;
        private String referencedTable;
        private String referencedColumn;
        private String foreignKeyName;
        private SqlSchemaValueMapping valueMapping;

        public Builder name(String name) { this.name = name; return this; }
        public Builder typeName(String typeName) { this.typeName = typeName; return this; }
        public Builder typeCode(int typeCode) { this.typeCode = typeCode; return this; }
        public Builder size(int size) { this.size = size; return this; }
        public Builder javaType(java.lang.reflect.Type javaType) { this.javaType = javaType; return this; }
        public Builder javaType(Class<?> clazz) { this.javaType = clazz; return this; }
        public Builder nullable(boolean nullable) { this.nullable = nullable; return this; }
        public Builder primaryKey(boolean primaryKey) { this.primaryKey = primaryKey; return this; }
        public Builder unique(boolean unique) { this.unique = unique; return this; }
        public Builder defaultValue(Object defaultValue) { this.defaultValue = defaultValue; return this; }
        public Builder autoIncrement(boolean autoIncrement) { this.autoIncrement = autoIncrement; return this; }
        public Builder setForeignKey(String referencedTable, String referencedColumn, String foreignKeyName) {
            this.referencedTable = referencedTable;
            this.referencedColumn = referencedColumn;
            this.foreignKeyName = foreignKeyName;
            return this;
        }
        public Builder referencedTable(String referencedTable) { this.referencedTable = referencedTable; return this; }
        public Builder referencedColumn(String referencedColumn) { this.referencedColumn = referencedColumn; return this; }
        public Builder foreignKeyName(String foreignKeyName) { this.foreignKeyName = foreignKeyName; return this; }
        public Builder valueMapping(SqlSchemaValueMapping valueMapping) { this.valueMapping = valueMapping; return this; }

        public SqlColumnMeta build() {
            var col = new SqlColumnMeta(name, typeName, typeCode, size, javaType,
                    nullable, primaryKey, unique, defaultValue, autoIncrement);
            if (referencedTable != null) {
                col.setForeignKey(referencedTable, referencedColumn, foreignKeyName);
            }
            if (valueMapping != null) {
                col.setValueMapping(valueMapping);
            }
            return col;
        }
    }
}