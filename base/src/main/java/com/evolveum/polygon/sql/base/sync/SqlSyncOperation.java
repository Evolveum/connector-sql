/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.sql.base.sync;

import com.evolveum.polygon.conndev.api.ContextLookup;
import com.evolveum.polygon.conndev.spi.ObjectSyncOperation;
import com.evolveum.polygon.sql.base.SqlBaseContext;
import com.evolveum.polygon.sql.base.SqlTuple;
import com.evolveum.polygon.sql.base.build.api.SqlAttributeDefinition;
import com.evolveum.polygon.sql.base.build.api.SqlAttributeMapping;
import com.evolveum.polygon.sql.base.build.api.SqlObjectClassDefinition;
import com.evolveum.polygon.sql.base.sync.strategy.SyncFilterStrategy;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.Path;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.ComparablePath;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.sql.RelationalPathBase;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.framework.common.objects.*;
import org.identityconnectors.framework.spi.SyncTokenResultsHandler;

import java.sql.Date;
import java.sql.JDBCType;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

/**
 * QueryDSL-based sync operation for SQL object classes.
 */
public class SqlSyncOperation implements ObjectSyncOperation {

    private final SqlBaseContext context;
    private final SqlObjectClassDefinition objectClassDef;
    private final SyncConfig syncConfig;

    public SqlSyncOperation(SqlBaseContext context,
                            SqlObjectClassDefinition objectClassDef,
                            SyncConfig syncConfig) {
        this.context = context;
        this.objectClassDef = objectClassDef;
        this.syncConfig = syncConfig;
    }

    @Override
    public void sync(ObjectClass objectClass, SyncToken token,
                     SyncResultsHandler handler, OperationOptions options,
                     ContextLookup ctx) {
        RelationalPathBase<?> path = objectClassDef.sql().pathAlias("sync");

        var syncColName = syncConfig.resolveSyncColumn(objectClassDef);
Path<?> syncPath = syncColumnPath(path, syncColName);
        var syncFilter = syncConfig.filterStrategy().applySyncFilter(path);
        ComparablePath<?> syncCmp = (ComparablePath<?>) syncPath;
        var syncPoint = extractSyncValue(token);

        List<Path<?>> allCols = buildColumnPaths(path);

        long latestValue = 0L;

        try (var conn = context.getConnection()) {
            var jdbcConn = conn.getConnection();
            int pageSize = syncConfig.pageSize();
            int offset = 0;
            List<SqlAttributeMapping.SingleColumn> uidCols = findUidMappings();

            while (true) {
                List<Tuple> rows = context.getSqlQueryEngine().selectRange(
                        jdbcConn, path, allCols, syncCmp, syncPoint, syncFilter,
                        pageSize, offset);

                for (Tuple row : rows) {
                    var syncVal = row.get(syncPath);
                    long val = syncVal == null ? 0L : toLong(syncVal);
                    latestValue = Math.max(latestValue, val);

                    var uid = buildUid(uidCols, row, path);

                    var sqlRow = new SqlTuple(path, row);
                    var obj = buildConnObj(sqlRow, uid);

                    var bld = new SyncDeltaBuilder();
                    bld.setToken(new SyncToken(latestValue));
                    bld.setDeltaType(SyncDeltaType.CREATE_OR_UPDATE);
                    bld.setUid(new Uid(uid));
                    bld.setObjectClass(objectClass);
                    bld.setObject(obj);
                    var delta = bld.build();

                    if (!handler.handle(delta)) {
                        return;
                    }
                }

                if (rows.isEmpty() || rows.size() < pageSize) {
                    break;
                }

                offset += pageSize;
            }

            handleTombstones(path, syncPath, uidCols, latestValue, handler, objectClass);

        }

        if (handler instanceof SyncTokenResultsHandler sth) {
            sth.handleResult(new SyncToken(latestValue));
        }
    }

    @Override
    public SyncToken getLatestSyncToken(ObjectClass objectClass) {
        RelationalPathBase<?> path = objectClassDef.sql().pathAlias("sync");
        var syncColName = syncConfig.resolveSyncColumn(objectClassDef);
        Path<?> syncPath = syncColumnPath(path, syncColName);
        ComparablePath<?> syncCmp = (ComparablePath<?>) syncPath;

        try (var conn = context.getConnection()) {
            var maxVal = conn.newQuery()
                    .select(syncCmp.max())
                    .from(path)
                    .fetchOne();
            
            if (maxVal == null) {
                return null;
            }
            return new SyncToken(((Number) maxVal).longValue());
        } catch (Exception e) {
            throw new ConnectorException("getLatestSyncToken failed: " + e.getMessage(), e);
        }
    }

    private List<Path<?>> buildColumnPaths(RelationalPathBase<?> path) {
        List<Path<?>> cols = new ArrayList<>();
        for (SqlAttributeDefinition attr : objectClassDef.attributes()) {
            if (attr.connId() != null && attr.connId().isReturnedByDefault()) {
                var map = attr.sql();
                if (map != null) {
                    cols.addAll(map.selectPaths(path));
                }
            }
        }
        return cols;
    }

    private Path<?> syncColumnPath(RelationalPathBase<?> path, String colName) {
        for (SqlAttributeDefinition attr : objectClassDef.attributes()) {
            var map = attr.sql();
            if (map instanceof SqlAttributeMapping.SingleColumn sc
                    && sc.column().value().equalsIgnoreCase(colName)) {
                return sc.dslPath(path);
            }
        }
        return Expressions.path(Object.class, path, colName);
    }

    private List<SqlAttributeMapping.SingleColumn> findUidMappings() {
        List<SqlAttributeMapping.SingleColumn> result = new ArrayList<>();
        var intType = JDBCType.INTEGER;
        var bigIntType = JDBCType.BIGINT;
        
        for (SqlAttributeDefinition attr : objectClassDef.attributes()) {
            if (attr.connId() == null || !Uid.NAME.equals(attr.connId().getName())) {
                continue;
            }
            var map = attr.sql();
            if (map == null) continue;
            if (map instanceof SqlAttributeMapping.SingleColumn sc) {
                result.add(sc);
            } else if (map instanceof SqlAttributeMapping.MultiColumn mc) {
                result.add(mc.mainColumn());
                result.addAll(mc.additionalColumns());
            }
        }
        if (result.isEmpty()) {
            for (SqlAttributeDefinition attr : objectClassDef.attributes()) {
                var map = attr.sql();
                if (map instanceof SqlAttributeMapping.SingleColumn sc) {
                    var jt = sc.sqlMapping().jdbcType();
                    if (intType.equals(jt) || bigIntType.equals(jt)) {
                        result.add(sc);
                        break;
                    }
                }
            }
        }
        return result;
    }

    private String buildUid(List<SqlAttributeMapping.SingleColumn> uidCols,
                            Tuple row, RelationalPathBase<?> path) {
        if (uidCols.isEmpty()) return "";
        if (uidCols.size() == 1) {
            var v = row.get(uidCols.getFirst().dslPath(path));
            return v != null ? v.toString() : "";
        }
        List<String> parts = new ArrayList<>();
        for (SqlAttributeMapping.SingleColumn sc : uidCols) {
            var v = row.get(sc.dslPath(path));
            parts.add(v != null ? v.toString() : "");
        }
        return String.join(".", parts);
    }

    private ConnectorObject buildConnObj(SqlTuple row, String uid) {
        var builder = new ConnectorObjectBuilder();
        builder.setObjectClass(objectClassDef.objectClass());
        builder.setUid(new Uid(uid));

        for (SqlAttributeDefinition attr : objectClassDef.attributes()) {
            var map = attr.sql();
            if (map == null) continue;
            List<Object> vals = map.valuesFromObject(row);
            if (vals != null && !vals.isEmpty()) {
                builder.addAttribute(attr.attributeOf(vals));
            }
        }

        return builder.build();
    }

    private Object extractSyncValue(SyncToken token) {
        if (token == null || token.getValue() == null) return null;
        return token.getValue();
    }

    private long toLong(Object value) {
        if (value == null) return 0L;
        if (value instanceof Number n) return n.longValue();
        if (value instanceof Date d) return d.getTime();
        if (value instanceof Timestamp t) return t.getTime();
        return value == null ? 0L : 0L;
    }

    private void handleTombstones(RelationalPathBase<?> path, Path<?> syncPath,
                                   List<SqlAttributeMapping.SingleColumn> uidCols,
                                   long latestValue, SyncResultsHandler handler,
                                   ObjectClass objectClass) {
        var fs = syncConfig.filterStrategy();

        try (var conn = context.getConnection()) {
            var tombstoneFilter = fs.applyTombstoneFilter(
                    path, (ComparablePath<?>) syncPath, latestValue);
            if (tombstoneFilter == null) return;

            Path<?> uidCol = uidCols.isEmpty() ? syncPath : uidCols.getFirst().dslPath(path);
            @SuppressWarnings("unchecked")
            ComparablePath<?> syncCmp = (ComparablePath<?>) syncPath;

            List<Tuple> tombstones = context.getSqlQueryEngine().selectTombstones(
                    conn.getConnection(), path, uidCol, syncCmp,
                    tombstoneFilter, syncConfig.pageSize());

            for (Tuple row : tombstones) {
                var uid = buildUid(uidCols, row, path);
                var syncVal = row.get(syncPath);
                long tombstoneVal = syncVal == null ? latestValue : toLong(syncVal);

                var bld = new SyncDeltaBuilder();
                bld.setToken(new SyncToken(tombstoneVal > 0 ? tombstoneVal : latestValue));
                bld.setDeltaType(SyncDeltaType.DELETE);
                bld.setUid(new Uid(uid));
                bld.setObjectClass(objectClass);

                handler.handle(bld.build());
            }
        }
    }
}
