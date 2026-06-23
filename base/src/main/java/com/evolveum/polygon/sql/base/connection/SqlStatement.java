/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.sql.base.connection;

import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * Represents an immutable SQL statement with all components.
 * Used for query construction and execution.
 */
public class SqlStatement {

    private final String tableName;
    private final List<String> selectColumns;
    private final Map<String, Object> insertValues;
    private final Map<String, Object> updateValues;
    private final String whereClause;
    private final List<String> orderBy;
    private final Integer limit;
    private final Integer offset;

    private SqlStatement(Builder builder) {
        this.tableName = builder.tableName;
        this.selectColumns = List.copyOf(builder.selectColumns);
        this.insertValues = Map.copyOf(builder.insertValues);
        this.updateValues = Map.copyOf(builder.updateValues);
        this.whereClause = builder.whereClause;
        this.orderBy = List.copyOf(builder.orderBy);
        this.limit = builder.limit;
        this.offset = builder.offset;
    }

    public String getTableName() {
        return tableName;
    }

    public List<String> getSelectColumns() {
        return selectColumns;
    }

    public Map<String, Object> getInsertValues() {
        return insertValues;
    }

    public Map<String, Object> getUpdateValues() {
        return updateValues;
    }

    public String getWhereClause() {
        return whereClause;
    }

    public List<String> getOrderBy() {
        return orderBy;
    }

    public Integer getLimit() {
        return limit;
    }

    public Integer getOffset() {
        return offset;
    }

    public boolean hasPagination() {
        return limit != null || offset != null;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private String tableName;
        private final List<String> selectColumns = new LinkedList<>();
        private final Map<String, Object> insertValues = new LinkedHashMap<>();
        private final Map<String, Object> updateValues = new LinkedHashMap<>();
        private String whereClause;
        private final List<String> orderBy = new LinkedList<>();
        private Integer limit;
        private Integer offset;

        public Builder tableName(String tableName) {
            this.tableName = tableName;
            return this;
        }

        public Builder selectColumn(String column) {
            this.selectColumns.add(column);
            return this;
        }

        public Builder selectColumns(List<String> columns) {
            this.selectColumns.addAll(columns);
            return this;
        }

        public Builder insert(String column, Object value) {
            this.insertValues.put(column, value);
            return this;
        }

        public Builder insertAll(Map<String, Object> values) {
            this.insertValues.putAll(values);
            return this;
        }

        public Builder update(String column, Object value) {
            this.updateValues.put(column, value);
            return this;
        }

        public Builder updateAll(Map<String, Object> values) {
            this.updateValues.putAll(values);
            return this;
        }

        public Builder where(String whereClause) {
            this.whereClause = whereClause;
            return this;
        }

        public Builder orderBy(String column) {
            this.orderBy.add(column);
            return this;
        }

        public Builder orderBy(List<String> columns) {
            this.orderBy.addAll(columns);
            return this;
        }

        public Builder limit(Integer limit) {
            this.limit = limit;
            return this;
        }

        public Builder offset(Integer offset) {
            this.offset = offset;
            return this;
        }

        public SqlStatement build() {
            return new SqlStatement(this);
        }
    }
}