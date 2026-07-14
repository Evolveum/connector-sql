/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.sql.base.schema;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Information about a SQL table including its columns.
 */
public class SqlTableInfo {

    private final String name;
    private final String schema;
    private final String catalog;
    private final String tableType;
    private final String remarks;
    private final List<SqlColumnMeta> columns;

    public SqlTableInfo(String name, String schema, String catalog, String tableType,
                        String remarks, List<SqlColumnMeta> columns) {
        this.name = name;
        this.schema = schema;
        this.catalog = catalog;
        this.tableType = tableType;
        this.remarks = remarks;
        this.columns = columns != null ? new ArrayList<>(columns) : new ArrayList<>();
    }

    public String getName() {
        return name;
    }

    public String getSchema() {
        return schema;
    }

    public String getCatalog() {
        return catalog;
    }

    public String getTableType() {
        return tableType;
    }

    public String getRemarks() {
        return remarks;
    }

    public List<SqlColumnMeta> getColumns() {
        return new ArrayList<>(columns);
    }


    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String name;
        private String schema;
        private String catalog;
        private String tableType;
        private String remarks;
        final List<SqlColumnMeta> columns = new ArrayList<>();

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder schema(String schema) {
            this.schema = schema;
            return this;
        }

        public Builder catalog(String catalog) {
            this.catalog = catalog;
            return this;
        }

        public Builder tableType(String tableType) {
            this.tableType = tableType;
            return this;
        }

        public Builder remarks(String remarks) {
            this.remarks = remarks;
            return this;
        }

        public Builder addColumn(SqlColumnMeta column) {
            columns.add(column);
            return this;
        }

        public SqlTableInfo build() {
            return new SqlTableInfo(name, schema, catalog, tableType, remarks, new ArrayList<>(columns));
        }

        public Builder columns(Collection<SqlColumnMeta> columns) {
            this.columns.addAll(columns);
            return this;
        }
    }
}