/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.sql.base.search;

import com.evolveum.polygon.conndev.api.ContextLookup;
import com.evolveum.polygon.conndev.build.api.AttributeResolverBuilder;
import com.evolveum.polygon.conndev.schema.BaseAttributeDefinition;
import com.evolveum.polygon.conndev.spi.AttributeResolver;
import com.evolveum.polygon.sql.base.SqlBaseContext;
import com.evolveum.polygon.sql.base.SqlTableAccess;
import com.evolveum.polygon.sql.base.connection.SqlConnection;
import com.evolveum.polygon.sql.base.schema.SqlChildJoinConfig;
import com.querydsl.core.types.Path;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.framework.common.objects.Attribute;
import org.identityconnectors.framework.common.objects.AttributeBuilder;
import org.identityconnectors.framework.common.objects.ConnectorObjectBuilder;
import org.identityconnectors.framework.common.objects.EmbeddedObject;
import org.identityconnectors.framework.common.objects.ObjectClass;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Resolves scalar or embedded attributes stored in an owned child table. */
public class SqlJoinAttributeResolver implements AttributeResolver {

    private final SqlBaseContext sqlContext;
    private final SqlChildJoinConfig config;
    private final String attributeName;

    public SqlJoinAttributeResolver(SqlBaseContext context, SqlChildJoinConfig config, String attributeName) {
        this.sqlContext = context;
        this.config = config;
        this.attributeName = attributeName;
    }

    @Override
    public Set<BaseAttributeDefinition> getSupportedAttributes() {
        return Set.of();
    }

    @Override
    public AttributeResolverBuilder.ResolutionType resolutionType() {
        return AttributeResolverBuilder.ResolutionType.BATCH;
    }

    @Override
    public void resolveSingle(ContextLookup context, ConnectorObjectBuilder builder) {
        resolve(context, Collections.singletonList(builder));
    }

    @Override
    public void resolve(ContextLookup context, Iterable<ConnectorObjectBuilder> builders) {
        if (sqlContext == null) {
            return;
        }

        var buildersByUid = SqlRelatedJoinResolverSupport.collectByUid(builders);
        if (buildersByUid.isEmpty()) {
            return;
        }

        try (var connection = sqlContext.getConnection()) {
            var parents = SqlRelatedJoinResolverSupport.indexParents(
                    sqlContext, connection, config.parentTable(), config.joinKeys(), buildersByUid);
            if (parents.isEmpty()) {
                return;
            }
            var childTable = new SqlTableAccess(sqlContext, config.childTable(), "c");
            var criteria = parents.keySet().stream()
                    .map(values -> SqlRelatedJoinResolverSupport.relatedCriteria(
                            config.joinKeys(), values))
                    .toList();
            var values = config.valueColumn() != null
                    ? resolveScalar(connection, childTable, criteria, parents)
                    : resolveEmbedded(connection, childTable, criteria, parents);
            applyResults(values);
        } catch (Exception e) {
            throw new ConnectorException(
                    "Failed to resolve attribute '" + attributeName
                            + "' from child table '" + config.childTable() + "'", e);
        }
    }

    private Map<ConnectorObjectBuilder, List<Object>> resolveScalar(
            SqlConnection connection,
            SqlTableAccess childTable,
            List<Map<String, Object>> criteria,
            Map<SqlRelatedJoinResolverSupport.JoinValues, ConnectorObjectBuilder> parents) {
        var selected = new LinkedHashSet<Path<?>>();
        selected.add(childTable.columnPath(config.valueColumn()));
        config.joinKeys().stream()
                .map(key -> childTable.columnPath(key.childColumn()))
                .forEach(selected::add);

        var result = new LinkedHashMap<ConnectorObjectBuilder, List<Object>>();
        var rows = connection.newQuery()
                .select(selected.toArray(Path<?>[]::new))
                .from(childTable.path())
                .where(childTable.matchingAny(criteria))
                .fetch();
        for (var row : rows) {
            var parent = parents.get(SqlRelatedJoinResolverSupport.relatedValues(
                    childTable, row, config.joinKeys()));
            var value = childTable.toConnIdValue(
                    config.valueColumn(), childTable.value(row, config.valueColumn()));
            if (parent != null && value != null) {
                result.computeIfAbsent(parent, ignored -> new ArrayList<>()).add(value);
            }
        }
        return result;
    }

    private Map<ConnectorObjectBuilder, List<Object>> resolveEmbedded(
            SqlConnection connection,
            SqlTableAccess childTable,
            List<Map<String, Object>> criteria,
            Map<SqlRelatedJoinResolverSupport.JoinValues, ConnectorObjectBuilder> parents) {
        var selected = childTable.metadata().getColumns().stream()
                .map(column -> childTable.columnPath(column.getName()))
                .distinct()
                .toArray(Path<?>[]::new);
        var result = new LinkedHashMap<ConnectorObjectBuilder, List<Object>>();
        var rows = connection.newQuery()
                .select(selected)
                .from(childTable.path())
                .where(childTable.matchingAny(criteria))
                .fetch();
        for (var row : rows) {
            var parent = parents.get(SqlRelatedJoinResolverSupport.relatedValues(
                    childTable, row, config.joinKeys()));
            if (parent == null) {
                continue;
            }
            var attributes = new LinkedHashSet<Attribute>();
            for (var column : childTable.metadata().getColumns()) {
                var value = childTable.toConnIdValue(
                        column.getName(), childTable.value(row, column.getName()));
                if (value != null) {
                    attributes.add(AttributeBuilder.build(column.getName(), value));
                }
            }
            var embedded = new EmbeddedObject(new ObjectClass(config.childTable()), attributes);
            result.computeIfAbsent(parent, ignored -> new ArrayList<>()).add(embedded);
        }
        return result;
    }

    private void applyResults(Map<ConnectorObjectBuilder, List<Object>> values) {
        for (var entry : values.entrySet()) {
            if (!entry.getValue().isEmpty()) {
                entry.getKey().addAttribute(AttributeBuilder.build(attributeName, entry.getValue()));
            }
        }
    }
}
