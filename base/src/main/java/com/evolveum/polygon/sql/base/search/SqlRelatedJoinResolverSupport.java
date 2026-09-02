/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.sql.base.search;

import com.evolveum.polygon.sql.base.SqlBaseContext;
import com.evolveum.polygon.sql.base.SqlTableAccess;
import com.evolveum.polygon.sql.base.SqlTuple;
import com.evolveum.polygon.sql.base.build.api.SqlAttributeDefinition;
import com.evolveum.polygon.sql.base.build.api.SqlObjectClassDefinition;
import com.evolveum.polygon.sql.base.connection.SqlConnection;
import com.evolveum.polygon.sql.base.schema.ChildTableRelationship.JoinKey;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.Path;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.sql.RelationalPathBase;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.framework.common.objects.ConnectorObjectBuilder;
import org.identityconnectors.framework.common.objects.Uid;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/** Shared parent-key and composite-join resolution for related-table attribute resolvers. */
final class SqlRelatedJoinResolverSupport {

    private SqlRelatedJoinResolverSupport() {
    }

    static Map<String, ConnectorObjectBuilder> collectByUid(
            Iterable<ConnectorObjectBuilder> builders) {
        var result = new LinkedHashMap<String, ConnectorObjectBuilder>();
        for (var builder : builders) {
            var uid = builder.build().getUid();
            if (uid != null) {
                result.put(uid.getUidValue(), builder);
            }
        }
        return result;
    }

    static Map<JoinValues, ConnectorObjectBuilder> indexParents(
            SqlBaseContext context, SqlConnection connection,
            String parentTable, List<JoinKey> joinKeys,
            Map<String, ConnectorObjectBuilder> buildersByUid) {
        if (buildersByUid.isEmpty()) {
            return Map.of();
        }

        var definition = objectClass(context, parentTable);
        var tablePath = definition.sql().pathAlias("rp");
        var table = new SqlTableAccess(context, definition.sql().getTableName(), tablePath);
        var uidDefinition = definition.attributeFromConnIdName(Uid.NAME);
        if (uidDefinition == null || uidDefinition.sql() == null
                || uidDefinition.sql().sqlFilter() == null) {
            throw new ConnectorException(
                    "Object class " + definition.name() + " has no queryable UID mapping");
        }

        var uidBasedIndex = indexFromUidColumns(
                tablePath, uidDefinition, joinKeys, buildersByUid);
        if (uidBasedIndex != null) {
            return uidBasedIndex;
        }

        var selected = new LinkedHashSet<Path<?>>();
        selected.addAll(uidDefinition.sql().selectPaths(tablePath));
        joinKeys.stream()
                .map(JoinKey::parentColumn)
                .map(table::columnPath)
                .forEach(selected::add);

        BooleanExpression uidPredicate = null;
        for (var uid : buildersByUid.keySet()) {
            var current = uidDefinition.sql().sqlFilter().eq(tablePath, uid);
            uidPredicate = uidPredicate == null ? current : uidPredicate.or(current);
        }

        var rows = connection.newQuery()
                .select(selected.toArray(Path<?>[]::new))
                .from(tablePath)
                .where(uidPredicate)
                .fetch();
        var result = new LinkedHashMap<JoinValues, ConnectorObjectBuilder>();
        for (var row : rows) {
            var uidValues = uidDefinition.sql().valuesFromObject(new SqlTuple(tablePath, row));
            if (uidValues.isEmpty() || uidValues.getFirst() == null) {
                continue;
            }
            var builder = buildersByUid.get(uidValues.getFirst().toString());
            if (builder != null) {
                result.put(parentValues(table, row, joinKeys), builder);
            }
        }
        return result;
    }

    static Map<JoinValues, String> targetUids(
            SqlBaseContext context, SqlConnection connection,
            String targetTable, List<JoinKey> targetJoinKeys,
            Collection<JoinValues> requestedValues) {
        if (requestedValues.isEmpty()) {
            return Map.of();
        }

        var definition = objectClass(context, targetTable);
        var tablePath = definition.sql().pathAlias("rt");
        var table = new SqlTableAccess(context, definition.sql().getTableName(), tablePath);
        var uidDefinition = definition.attributeFromConnIdName(Uid.NAME);
        if (uidDefinition == null || uidDefinition.sql() == null) {
            throw new ConnectorException(
                    "Object class " + definition.name() + " has no UID mapping");
        }

        var criteria = requestedValues.stream()
                .map(values -> parentCriteria(table, targetJoinKeys, values))
                .toList();
        var selected = new LinkedHashSet<Path<?>>();
        selected.addAll(uidDefinition.sql().selectPaths(tablePath));
        targetJoinKeys.stream()
                .map(JoinKey::parentColumn)
                .map(table::columnPath)
                .forEach(selected::add);

        var rows = connection.newQuery()
                .select(selected.toArray(Path<?>[]::new))
                .from(tablePath)
                .where(table.matchingAny(criteria))
                .fetch();
        var result = new LinkedHashMap<JoinValues, String>();
        for (var row : rows) {
            var uidValues = uidDefinition.sql().valuesFromObject(new SqlTuple(tablePath, row));
            if (!uidValues.isEmpty() && uidValues.getFirst() != null) {
                result.put(parentValues(table, row, targetJoinKeys),
                        uidValues.getFirst().toString());
            }
        }
        return result;
    }

    static Map<String, Object> relatedCriteria(
            List<JoinKey> joinKeys, JoinValues values) {
        if (joinKeys.size() != values.values().size()) {
            throw new IllegalArgumentException("Join key and value counts differ");
        }
        var result = new LinkedHashMap<String, Object>();
        for (int i = 0; i < joinKeys.size(); i++) {
            result.put(joinKeys.get(i).childColumn(), values.values().get(i));
        }
        return result;
    }

    static JoinValues relatedValues(
            SqlTableAccess relatedTable, Tuple row, List<JoinKey> joinKeys) {
        var values = new ArrayList<Object>(joinKeys.size());
        for (var joinKey : joinKeys) {
            values.add(relatedTable.value(row, joinKey.childColumn()));
        }
        return new JoinValues(values);
    }

    private static Map<String, Object> parentCriteria(
            SqlTableAccess parentTable, List<JoinKey> joinKeys, JoinValues values) {
        if (joinKeys.size() != values.values().size()) {
            throw new IllegalArgumentException("Join key and value counts differ");
        }
        var result = new LinkedHashMap<String, Object>();
        for (int i = 0; i < joinKeys.size(); i++) {
            var column = joinKeys.get(i).parentColumn();
            result.put(column, parentTable.toWireValue(column, values.values().get(i)));
        }
        return result;
    }

    private static Map<JoinValues, ConnectorObjectBuilder> indexFromUidColumns(
            RelationalPathBase<?> tablePath,
            SqlAttributeDefinition uidDefinition,
            List<JoinKey> joinKeys,
            Map<String, ConnectorObjectBuilder> buildersByUid) {
        var result = new LinkedHashMap<JoinValues, ConnectorObjectBuilder>();
        for (var entry : buildersByUid.entrySet()) {
            var uidColumns = new LinkedHashMap<String, Object>();
            for (var columnValue : uidDefinition.sql().columnValues(tablePath, entry.getKey())) {
                uidColumns.put(columnValue.path().getMetadata().getName(), columnValue.value());
            }
            var joinValues = new ArrayList<Object>(joinKeys.size());
            for (var joinKey : joinKeys) {
                var value = valueIgnoreCase(uidColumns, joinKey.parentColumn());
                if (value == MissingValue.INSTANCE) {
                    return null;
                }
                joinValues.add(value);
            }
            result.put(new JoinValues(joinValues), entry.getValue());
        }
        return result;
    }

    private static Object valueIgnoreCase(Map<String, Object> values, String column) {
        for (var entry : values.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(column)) {
                return entry.getValue();
            }
        }
        return MissingValue.INSTANCE;
    }

    private static JoinValues parentValues(
            SqlTableAccess parentTable, Tuple row, List<JoinKey> joinKeys) {
        var values = new ArrayList<Object>(joinKeys.size());
        for (var joinKey : joinKeys) {
            values.add(parentTable.value(row, joinKey.parentColumn()));
        }
        return new JoinValues(values);
    }

    private static SqlObjectClassDefinition objectClass(
            SqlBaseContext context, String tableName) {
        var schema = context.schema();
        if (schema == null) {
            throw new ConnectorException("SQL schema is not initialized");
        }
        return schema.objectClasses().stream()
                .filter(candidate -> candidate.sql() != null
                        && candidate.sql().getTableName().equalsIgnoreCase(tableName))
                .findFirst()
                .orElseThrow(() -> new ConnectorException(
                        "No object class mapping found for table " + tableName));
    }

    record JoinValues(List<Object> values) {
        JoinValues {
            values = List.copyOf(values);
        }
    }

    private enum MissingValue {
        INSTANCE
    }
}
