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
import com.evolveum.polygon.sql.base.search.SqlSearchExecutor;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.Path;
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
public class SqlSyncOperation extends SqlSearchExecutor implements ObjectSyncOperation {

    private final SyncConfig syncConfig;

    public SqlSyncOperation(SqlBaseContext context,
                            SqlObjectClassDefinition objectClassDef,
                            SyncConfig syncConfig) {
        super(context, objectClassDef);
        this.syncConfig = syncConfig;
    }

    @Override
    public void sync(SyncToken token, SyncResultsHandler handler, OperationOptions options, ContextLookup ctx) {
        RelationalPathBase<?> path = getTablePath();

        var syncColName = syncConfig.resolveSyncColumn(objectClass);
        Path<?> syncPath = syncColumnPath(path, syncColName);
        var syncFilter = syncConfig.filterStrategy().applySyncFilter(path);
        ComparablePath<?> syncCmp = (ComparablePath<?>) syncPath;
        var syncPoint = extractSyncValue(token);



        var attributes = selectColumns(path, options);
        var allCols = onlyPaths(attributes).toArray(new Path[] {});


        long latestValue = 0L;

        try (var conn = context.getConnection()) {
            var jdbcConn = conn.getConnection();
            int pageSize = syncConfig.pageSize();
            int offset = 0;
            while (true) {
                var query = conn.newQuery().select(allCols).from(path);
                if (syncPoint != null) {
                    @SuppressWarnings("unchecked")
                    ComparablePath<Long> cp = (ComparablePath<Long>) syncCmp;
                    query.where(cp.gt(((Number) syncPoint).longValue()));
                }
                if (syncFilter != null) {
                    query.where(syncFilter);
                }
                query.orderBy(syncCmp.asc());
                query.limit(pageSize).offset(offset);

                var rows = query.fetch();
                for (Tuple row : rows) {
                    var syncVal = row.get(syncPath);
                    long val = syncVal == null ? 0L : toLong(syncVal);
                    latestValue = Math.max(latestValue, val);

                    var obj = buildConnectorObject(row, attributes);
                    var bld = new SyncDeltaBuilder();
                    bld.setToken(new SyncToken(latestValue));
                    bld.setDeltaType(SyncDeltaType.CREATE_OR_UPDATE);
                    bld.setUid(obj.getUid());
                    bld.setObjectClass(obj.getObjectClass());
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
            handleTombstones(path, syncPath, latestValue, handler);
        }

        if (handler instanceof SyncTokenResultsHandler sth) {
            sth.handleResult(new SyncToken(latestValue));
        }
    }

    @Override
    public SyncToken getLatestSyncToken() {
        RelationalPathBase<?> path = objectClass.sql().pathAlias("sync");
        var syncColName = syncConfig.resolveSyncColumn(objectClass);
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

    private Path<?> syncColumnPath(RelationalPathBase<?> path, String colName) {
        for (SqlAttributeDefinition attr : objectClass.attributes()) {
            var map = attr.sql();
            if (map instanceof SqlAttributeMapping.SingleColumn sc
                    && sc.column().value().equalsIgnoreCase(colName)) {
                return sc.dslPath(path);
            }
        }
        return Expressions.path(Object.class, path, colName);
    }

    private Object extractSyncValue(SyncToken token) {
        if (token == null || token.getValue() == null) return null;
        return token.getValue();
    }

    private long toLong(Object value) {
        if (value == null) return 0L;
        if (value instanceof Number n) return n.longValue();
        if (value instanceof Timestamp t) return t.getTime();
        if (value instanceof Date d) return d.getTime();
        return 0L;
    }

    private void handleTombstones(RelationalPathBase<?> path, Path<?> syncPath,
                                   long latestValue, SyncResultsHandler handler) {
        var fs = syncConfig.filterStrategy();

        try (var conn = context.getConnection()) {
            var tombstoneFilter = fs.applyTombstoneFilter(
                    path, (ComparablePath<?>) syncPath, latestValue);
            if (tombstoneFilter == null) return;

            var uidMapping = objectClass.attributeFromConnIdName(Uid.NAME);
            var columns = new ArrayList<>(uidMapping.sql().selectPaths(path));
            columns.add(syncPath);
            @SuppressWarnings("unchecked")
            ComparablePath<?> syncCmp = (ComparablePath<?>) syncPath;


            List<Tuple> tombstones = conn.newQuery()
                    .select(columns.toArray(new Path[]{}))
                    .from(path)
                    .where(tombstoneFilter)
                    .orderBy(syncCmp.asc())
                    .limit(syncConfig.pageSize())
                    .fetch();
            for (Tuple row : tombstones) {
                var uid = (String) uidMapping.sql().singleValueFromObject(new SqlTuple(path, row));
                var syncVal = row.get(syncPath);
                long tombstoneVal = syncVal == null ? latestValue : toLong(syncVal);

                var bld = new SyncDeltaBuilder();
                bld.setToken(new SyncToken(tombstoneVal > 0 ? tombstoneVal : latestValue));
                bld.setDeltaType(SyncDeltaType.DELETE);
                bld.setUid(new Uid(uid));
                bld.setObjectClass(objectClass.objectClass());

                handler.handle(bld.build());
            }
        }
    }
}
