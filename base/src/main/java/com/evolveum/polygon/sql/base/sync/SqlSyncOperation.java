/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.sql.base.sync;

import com.evolveum.polygon.sql.base.SqlBaseContext;
import com.evolveum.polygon.sql.base.build.api.SqlAttributeDefinition;
import com.evolveum.polygon.sql.base.build.api.SqlObjectClassDefinition;
import com.evolveum.polygon.sql.base.connection.SqlQueryEngine;
import com.evolveum.polygon.sql.base.schema.QueryDSLMetadata;
import com.evolveum.polygon.sql.base.schema.SqlQuerydslMetadataFactory;
import org.identityconnectors.framework.common.objects.*;

import java.sql.SQLException;
import java.util.*;

/**
 * SQL-based sync operation for a single table.
 * Polled for changes after the previous token.
 */
public class SqlSyncOperation {

    private static final int PAGE_SIZE = 200;

    private final SqlBaseContext context;
    private final SqlQueryEngine queryEngine;
    private final QueryDSLMetadata metadata;
    private final SqlObjectClassDefinition objectClass;
    private final SyncOperationDefinition syncDef;
    private final String tableName;

    public SqlSyncOperation(SqlBaseContext context,
                            SqlQuerydslMetadataFactory metadataFactory,
                            SqlObjectClassDefinition objectClass,
                            SyncOperationDefinition syncDef) {
        this.context = context;
        this.queryEngine = context.getSqlQueryEngine();
        this.syncDef = syncDef;
        this.objectClass = objectClass;

        String tn = objectClass.sql() != null ? objectClass.sql().getTableName() : null;
        this.tableName = tn != null ? tn : "unknown";
        this.metadata = tn != null ? metadataFactory.getMetadata(tn) : null;
    }

    /**
     * Execute sync for this object class.
     */
    public SyncToken execute(SyncResultsHandler handler,
                             SyncToken token,
                             OperationOptions options) {
        if (!syncDef.enabled()) {
            return null;
        }

        String prevVal = token != null ? token.getValue().toString() : null;
        SyncToken nextToken = null;

        try (var conn = context.getConnection()) {
            var jdbcConn = conn.getConnection();

            int offset = 0;
            while (true) {
                List<Map<String, Object>> rows;
                Object tokenVal;

                switch (syncDef.strategy()) {
                    case TIMESTAMP_POLLING:
                        rows = pollTs(jdbcConn, prevVal, offset);
                        tokenVal = lastVal(rows, colTs());
                        break;

                    case POSTGRESQL_XMIN:
                        rows = pollXmin(jdbcConn, prevVal);
                        tokenVal = lastVal(rows, "xmin");
                        break;

                    case SQLITE_ROWID:
                        rows = pollRowid(jdbcConn, prevVal);
                        tokenVal = lastVal(rows, "rowid");
                        break;

                    case AUDIT_TABLE:
                        rows = pollAudit(jdbcConn, prevVal, offset);
                        tokenVal = lastVal(rows, colTs());
                        break;

                    default:
                        throw new UnsupportedOperationException(
                                "Sync strategy not yet implemented: " + syncDef.strategy());
                }

                if (rows.isEmpty() || rows == null) {
                    break;
                }

                for (Map<String, Object> row : rows) {
                    if (!handleRow(row, tokenVal, handler)) {
                        return new SyncToken(tokenVal);
                    }
                }

                nextToken = new SyncToken(tokenVal);

                if (rows.size() < PAGE_SIZE) {
                    break;
                }

                offset += PAGE_SIZE;
            }

            if (nextToken == null && prevVal != null) {
                return new SyncToken(prevVal);
            }
        } catch (SQLException e) {
            throw new org.identityconnectors.framework.common.exceptions.ConnectorException(
                    "Sync failed for table '" + tableName + "': " + e.getMessage(), e);
        }

        return nextToken;
    }

    /* ── polling (pure JDBC, avoids QueryDSL type-cast issues) ────────── */

    private List<Map<String, Object>> pollTs(java.sql.Connection conn, String prev, int offset) throws SQLException {
        String ts = colTs();
        String delCol = syncDef.deletedAtColumn();

        long prevNum = 0;
        if (prev != null && !prev.isEmpty()) {
            try { prevNum = Long.parseLong(prev); } catch (NumberFormatException ignored) {}
        }

        // Build SELECT with essential columns (id, name) + timestamp + deleted_at
        List<String> selectList = new ArrayList<>();
        selectList.add("id");
        selectList.add("name");
        if (ts != null) selectList.add(ts);
        if (delCol != null && !delCol.isEmpty()) selectList.add(delCol);

        StringBuilder sql = new StringBuilder("SELECT ");
        for (int i = 0; i < selectList.size(); i++) {
            if (i > 0) sql.append(", ");
            sql.append(selectList.get(i));
        }
        sql.append(" FROM " + tableName);

        List<String> conditions = new ArrayList<>();
        if (prev == null || prev.isEmpty()) {
            conditions.add(ts + " IS NOT NULL");
        } else {
            conditions.add(ts + " > " + prevNum);
            if (delCol != null && !delCol.isEmpty()) {
                conditions.add(delCol + " > " + prevNum);
            }
        }
        sql.append(" WHERE ").append(String.join(" OR ", conditions));
        sql.append(" ORDER BY " + ts + " ASC");
        sql.append(" LIMIT " + PAGE_SIZE + " OFFSET " + offset);

        java.sql.Statement stmt = conn.createStatement();
        java.sql.ResultSet rs = stmt.executeQuery(sql.toString());
        List<Map<String, Object>> rows = new ArrayList<>();
        while (rs.next()) {
            Map<String, Object> row = new LinkedHashMap<>();
            for (int i = 1; i <= rs.getMetaData().getColumnCount(); i++) {
                row.put(rs.getMetaData().getColumnName(i).toLowerCase(), rs.getObject(i));
            }
            rows.add(row);
        }
        rs.close();
        stmt.close();
        return rows;
    }

    private List<Map<String, Object>> pollXmin(java.sql.Connection conn, String prev) throws SQLException {
        long v = 0;
        try { v = Long.parseLong(prev); } catch (NumberFormatException ignored) {}

        com.querydsl.core.types.Predicate pred =
                com.querydsl.core.types.dsl.Expressions.numberPath(Long.class, "xmin").gt(v);

        try {
            return queryEngine.select(conn, tableName, metadata, null, pred,
                    Collections.singletonList("xmin"), PAGE_SIZE, 0);
        } catch (SQLException e) {
            throw new SQLException("Poll (POSTGRESQL_XMIN) failed for table '" + tableName + "': " + e.getMessage(), e);
        }
    }

    private List<Map<String, Object>> pollRowid(java.sql.Connection conn, String prev) throws SQLException {
        long v = 0;
        try { v = Long.parseLong(prev); } catch (NumberFormatException ignored) {}

        com.querydsl.core.types.Predicate pred =
                com.querydsl.core.types.dsl.Expressions.numberPath(Long.class, "rowid").gt(v);

        try {
            return queryEngine.select(conn, tableName, metadata, null, pred,
                    Collections.singletonList("rowid"), PAGE_SIZE, 0);
        } catch (SQLException e) {
            throw new SQLException("Poll (SQLITE_ROWID) failed for table '" + tableName + "': " + e.getMessage(), e);
        }
    }

    private List<Map<String, Object>> pollAudit(java.sql.Connection conn, String prev, int offset) throws SQLException {
        String ts = colTs();

        com.querydsl.core.types.Predicate pred = null;
        if (prev != null) {
            try {
                pred = com.querydsl.core.types.dsl.Expressions.numberPath(Long.class, ts).gt(Long.parseLong(prev));
            } catch (NumberFormatException e) {
pred = com.querydsl.core.types.dsl.Expressions.stringPath(ts).gt(prev);
            }
        }

        String atbl = syncDef.auditTable();
        if (atbl == null || atbl.isEmpty()) {
            atbl = tableName + "_audit";
        }

        try {
            return queryEngine.select(conn, atbl, metadata, null, pred,
                    Collections.singletonList(ts), PAGE_SIZE, offset);
        } catch (SQLException e) {
            throw new SQLException("Poll (AUDIT_TABLE) failed for table '" + tableName + "': " + e.getMessage(), e);
        }
    }

    /* ── row handling ───────────────────────────────────────────────── */

    private boolean handleRow(Map<String, Object> row, Object token, SyncResultsHandler handler) {
        ConnectorObject obj = buildObj(row);
        SyncToken st = new SyncToken(token);
        SyncDelta delta;

        if (syncDef.strategy() == SyncStrategy.AUDIT_TABLE) {
            String op = auditOp(row);
            if ("DELETE".equalsIgnoreCase(op)) {
                delta = mkDelta(st, SyncDeltaType.DELETE, null);
            } else {
                delta = mkDelta(st, SyncDeltaType.CREATE_OR_UPDATE, obj);
            }
        } else {
            // Check for soft-delete via deletedAt column
            String delVal = delVal(row);
            if (delVal != null && !delVal.isEmpty()) {
                // Soft delete: return UPDATE-type delta so the consumer can see the object
                // has a deletedAt time
                delta = mkDelta(st, SyncDeltaType.CREATE_OR_UPDATE, obj);
            } else {
                delta = mkDelta(st, SyncDeltaType.CREATE_OR_UPDATE, obj);
            }
        }

        try {
            return handler.handle(delta);
        } catch (org.identityconnectors.framework.common.exceptions.ConnectorException e) {
            throw e;
        } catch (Exception e) {
            throw new org.identityconnectors.framework.common.exceptions.ConnectorException("Error handling sync delta: " + e.getMessage(), e);
        }
    }

    /* ── builders ───────────────────────────────────────────────────── */

    private SyncDelta mkDelta(SyncToken token, SyncDeltaType type, ConnectorObject obj) {
        return new org.identityconnectors.framework.common.objects.SyncDeltaBuilder()
                .setToken(token)
                .setDeltaType(type)
                .setObject(obj)
                .build();
    }

    private ConnectorObject buildObj(Map<String, Object> row) {
        var b = new ConnectorObjectBuilder();
        b.setObjectClass(objectClass.objectClass());
        // Set Uid and Name - required by ConnectorObject
        b.addAttribute(new Uid(String.valueOf(row.getOrDefault("id", "auto-uid"))));
        b.addAttribute(new Name(row.getOrDefault("name", "auto-name").toString()));
        for (SqlAttributeDefinition attr : objectClass.attributes()) {
            var m = attr.sql();
            if (m != null) {
                String colName = m.column().value();
                // Case-insensitive lookup: JDBC may return column names in different case
                Object rawVal = row.get(colName);
                if (rawVal == null) {
                    String upperCol = colName.toUpperCase();
                    for (String rk : row.keySet()) {
                        if (rk.equalsIgnoreCase(colName)) {
                            rawVal = row.get(rk);
                            break;
                        }
                    }
                }
                if (rawVal != null) {
                    var converted = m.singleValueFromAttribute(rawVal);
                    if (converted != null) {
                        b.addAttribute(attr.attributeOf(Collections.singletonList(converted)));
                    }
                }
            }
        }
        return b.build();
    }

    private String auditOp(Map<String, Object> row) {
        return row.getOrDefault("operation", "CREATE_OR_UPDATE").toString();
    }

    private String delVal(Map<String, Object> row) {
        String col = syncDef.deletedAtColumn();
        if (col == null) return null;
        Object v = row.get(col);
        return v != null ? v.toString() : null;
    }

    private String colTs() {
        String c = syncDef.timestampColumn();
        return c != null ? c : "updated_at";
    }

    private Object lastVal(List<Map<String, Object>> rows, String col) {
        if (rows == null || rows.isEmpty()) {
            return null;
        }
        Map<String, Object> last = rows.get(rows.size() - 1);
        return last.get(col);
    }
}
