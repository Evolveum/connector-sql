/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.sql.base.connection;

import com.evolveum.polygon.sql.base.SqlBaseContext;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Executes SQL statements and maps results to ConnId-compatible structures.
 * Handles parameter binding, result set mapping, and exception translation.
 */
public class SqlQueryExecutor {

    private final SqlBaseContext context;
    private final SqlDialect dialect;

    public SqlQueryExecutor(SqlBaseContext context) {
        this.context = context;
        this.dialect = context.configuration().getDialect() != null 
            ? SqlDialect.create(context.configuration().getDialect()) 
            : SqlDialect.detectFromUrl(context.configuration().getJdbcUrl());
    }

    public List<Map<String, Object>> executeQuery(SqlStatement statement) throws SQLException {
        try (SqlConnection conn = context.getConnection();
             PreparedStatement ps = buildPreparedStatement(conn.getConnection(), statement)) {
            List<Map<String, Object>> results = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    results.add(mapResultSet(rs, statement.getSelectColumns()));
                }
            }
            return results;
        }
    }

    public int executeUpdate(SqlStatement statement) throws SQLException {
        try (SqlConnection conn = context.getConnection();
             PreparedStatement ps = buildPreparedStatement(conn.getConnection(), statement)) {
            return ps.executeUpdate();
        }
    }

    public Object executeInsert(SqlStatement statement, String[] returnColumns) throws SQLException {
        try (SqlConnection conn = context.getConnection();
             PreparedStatement ps = buildPreparedStatement(conn.getConnection(), statement)) {
            int affected = ps.executeUpdate();
            if (affected > 0 && returnColumns != null && returnColumns.length > 0) {
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    if (rs.next()) {
                        Map<String, Object> result = new HashMap<>();
                        for (String col : returnColumns) {
                            result.put(col, rs.getObject(col));
                        }
                        return result;
                    }
                }
            }
            return null;
        }
    }

    public Object executeScalar(SqlStatement statement) throws SQLException {
        try (SqlConnection conn = context.getConnection();
             PreparedStatement ps = buildPreparedStatement(conn.getConnection(), statement);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return rs.getObject(1);
            }
            return null;
        }
    }

    private PreparedStatement buildPreparedStatement(Connection connection, SqlStatement statement) throws SQLException {
        String sql = toSqlWithParameters(statement);
        PreparedStatement ps = connection.prepareStatement(sql, ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
        
        int paramIndex = 1;
        for (Object value : statement.getInsertValues().values()) {
            ps.setObject(paramIndex++, value);
        }
        for (Object value : statement.getUpdateValues().values()) {
            ps.setObject(paramIndex++, value);
        }
        
        return ps;
    }

    private String toSqlWithParameters(SqlStatement statement) {
        return dialect.toSql(statement);
    }

    private Map<String, Object> mapResultSet(ResultSet rs, List<String> columns) throws SQLException {
        Map<String, Object> result = new HashMap<>();
        if (columns.isEmpty()) {
            int colCount = rs.getMetaData().getColumnCount();
            for (int i = 1; i <= colCount; i++) {
                String colName = rs.getMetaData().getColumnName(i);
                Object value = rs.getObject(i);
                result.put(colName, value);
            }
        } else {
            for (String column : columns) {
                result.put(column, rs.getObject(column));
            }
        }
        return result;
    }
}