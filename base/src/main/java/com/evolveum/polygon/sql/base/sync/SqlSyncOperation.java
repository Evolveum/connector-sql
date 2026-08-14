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
import com.evolveum.polygon.sql.base.SqlObjectMapper;
import com.evolveum.polygon.sql.base.SqlTuple;
import com.evolveum.polygon.sql.base.build.api.SqlAttributeDefinition;
import com.evolveum.polygon.sql.base.build.api.SqlAttributeMapping;
import com.evolveum.polygon.sql.base.build.api.SqlObjectClassDefinition;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.Path;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.ComparablePath;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.sql.RelationalPathBase;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.framework.common.objects.*;
import org.identityconnectors.framework.spi.SyncTokenResultsHandler;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * QueryDSL-based sync operation for SQL object classes.
 */
public class SqlSyncOperation implements ObjectSyncOperation {

    private final SqlBaseContext context;
    private final SqlObjectClassDefinition objectClass;
    private final SqlObjectMapper objectMapper;
    private final SyncConfig syncConfig;

    public SqlSyncOperation(SqlBaseContext context,
                            SqlObjectClassDefinition objectClassDef,
                            SyncConfig syncConfig) {
        this.context = context;
        this.objectClass = objectClassDef;
        this.objectMapper = new SqlObjectMapper(objectClassDef);
        this.syncConfig = syncConfig;
    }

    @Override
    public void sync(SyncToken token, SyncResultsHandler handler, OperationOptions options, ContextLookup ctx) {
        RelationalPathBase<?> path = objectMapper.tablePath();

        var syncColName = syncConfig.resolveSyncColumn(objectClass);
        Path<?> syncPath = syncColumnPath(path, syncColName);
        var syncFilter = syncConfig.filterStrategy().applySyncFilter(path);
        var syncCmp = comparablePath(syncPath);
        var syncPoint = extractSyncValue(token);

        var attributes = objectMapper.selectColumns(path, options);
        var selectedPaths = new LinkedHashSet<>(objectMapper.onlyPaths(attributes));
        selectedPaths.add(syncPath);
        var allCols = selectedPaths.toArray(new Path[] {});
        var latestValue = syncPoint;

        try (var conn = context.getConnection()) {
            int pageSize = syncConfig.pageSize();
            int offset = 0;
            while (true) {
                var query = conn.newQuery().select(allCols).from(path);
                if (syncPoint != null) {
                    query.where(greaterThan(
                            syncCmp, toColumnValue(syncPath, syncPoint)));
                }
                if (syncFilter != null) {
                    query.where(syncFilter);
                }
                query.orderBy(syncCmp.asc());
                query.limit(pageSize).offset(offset);

                var rows = query.fetch();
                for (Tuple row : rows) {
                    var syncVal = row.get(syncPath);
                    latestValue = toTokenValue(syncVal);

                    var obj = objectMapper.buildConnectorObject(path, row, attributes);
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
        var syncCmp = comparablePath(syncPath);

        try (var conn = context.getConnection()) {
            var maxVal = conn.newQuery()
                    .select(syncCmp.max())
                    .from(path)
                    .fetchOne();
            
            if (maxVal == null) {
                return null;
            }
            return new SyncToken(toTokenValue(maxVal));
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

    private Object toTokenValue(Object value) {
        if (value instanceof Timestamp timestamp) {
            return timestamp.getTime();
        }
        if (value instanceof Date date) {
            return date.getTime();
        }
        if (value instanceof LocalTime time) {
            return time.toString();
        }
        return value;
    }

    private Object toColumnValue(Path<?> syncPath, Object tokenValue) {
        var columnType = syncPath.getType();
        if (tokenValue == null || columnType.isInstance(tokenValue)) {
            return tokenValue;
        }
        if (tokenValue instanceof Number number) {
            if (columnType == Byte.class) return number.byteValue();
            if (columnType == Short.class) return number.shortValue();
            if (columnType == Integer.class) return number.intValue();
            if (columnType == Long.class) return number.longValue();
            if (columnType == Float.class) return number.floatValue();
            if (columnType == Double.class) return number.doubleValue();
            if (columnType == BigInteger.class) return new BigInteger(number.toString());
            if (columnType == BigDecimal.class) return new BigDecimal(number.toString());
            if (columnType == Timestamp.class) return new Timestamp(number.longValue());
            if (columnType == Date.class) return new Date(number.longValue());
            if (columnType == ZonedDateTime.class) {
                return Instant.ofEpochMilli(number.longValue()).atZone(ZoneId.systemDefault());
            }
        }
        if (columnType == LocalTime.class && tokenValue instanceof String string) {
            return LocalTime.parse(string);
        }
        if (columnType == String.class) {
            return tokenValue.toString();
        }
        throw new ConnectorException(
                "Sync token value " + tokenValue + " cannot be converted to "
                        + columnType.getSimpleName());
    }

    private ComparablePath<?> comparablePath(Path<?> path) {
        if (!Comparable.class.isAssignableFrom(path.getType())) {
            throw new ConnectorException(
                    "Sync column " + path + " is not comparable");
        }
        return comparablePathUnchecked(path);
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private ComparablePath<?> comparablePathUnchecked(Path<?> path) {
        return Expressions.comparablePath(
                (Class<? extends Comparable>) path.getType(), path.getMetadata());
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private BooleanExpression greaterThan(ComparablePath<?> path, Object value) {
        if (!(value instanceof Comparable comparable)) {
            throw new ConnectorException(
                    "Sync token value " + value + " is not comparable");
        }
        return ((ComparablePath) path).gt(comparable);
    }

    private void handleTombstones(RelationalPathBase<?> path, Path<?> syncPath,
                                   Object latestValue, SyncResultsHandler handler) {
        var fs = syncConfig.filterStrategy();

        try (var conn = context.getConnection()) {
            var syncCmp = comparablePath(syncPath);
            var tombstoneFilter = fs.applyTombstoneFilter(
                    path, syncCmp, toColumnValue(syncPath, latestValue));
            if (tombstoneFilter == null) return;

            var uidMapping = objectClass.attributeFromConnIdName(Uid.NAME);
            var columns = new ArrayList<>(uidMapping.sql().selectPaths(path));
            columns.add(syncPath);
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
                var tombstoneVal = syncVal == null ? latestValue : toTokenValue(syncVal);

                var bld = new SyncDeltaBuilder();
                bld.setToken(new SyncToken(tombstoneVal));
                bld.setDeltaType(SyncDeltaType.DELETE);
                bld.setUid(new Uid(uid));
                bld.setObjectClass(objectClass.objectClass());

                handler.handle(bld.build());
            }
        }
    }
}
