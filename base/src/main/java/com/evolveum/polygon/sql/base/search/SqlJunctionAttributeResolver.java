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
import com.evolveum.polygon.sql.base.schema.SqlJunctionJoinConfig;
import com.querydsl.core.types.Path;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.framework.common.objects.AttributeBuilder;
import org.identityconnectors.framework.common.objects.ConnectorObjectBuilder;
import org.identityconnectors.framework.common.objects.ConnectorObjectReference;
import org.identityconnectors.framework.common.objects.ObjectClass;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Resolves read-only object references stored in a junction table. */
public class SqlJunctionAttributeResolver implements AttributeResolver {

    private final SqlBaseContext sqlContext;
    private final SqlJunctionJoinConfig config;
    private final String attributeName;

    public SqlJunctionAttributeResolver(
            SqlBaseContext context, SqlJunctionJoinConfig config, String attributeName) {
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
                    sqlContext, connection, config.parentTable(),
                    config.parentJoinKeys(), buildersByUid);
            if (parents.isEmpty()) {
                return;
            }

            var junction = new SqlTableAccess(sqlContext, config.junctionTable(), "j");
            var parentCriteria = parents.keySet().stream()
                    .map(values -> SqlRelatedJoinResolverSupport.relatedCriteria(
                            config.parentJoinKeys(), values))
                    .toList();
            var selected = new LinkedHashSet<Path<?>>();
            config.parentJoinKeys().stream()
                    .map(key -> junction.columnPath(key.childColumn()))
                    .forEach(selected::add);
            config.targetJoinKeys().stream()
                    .map(key -> junction.columnPath(key.childColumn()))
                    .forEach(selected::add);

            var resolvedRows = new ArrayList<ResolvedJunctionRow>();
            var targetValues = new LinkedHashSet<SqlRelatedJoinResolverSupport.JoinValues>();
            var rows = connection.newQuery()
                    .select(selected.toArray(Path<?>[]::new))
                    .from(junction.path())
                    .where(junction.matchingAny(parentCriteria))
                    .fetch();
            for (var row : rows) {
                var parent = parents.get(SqlRelatedJoinResolverSupport.relatedValues(
                        junction, row, config.parentJoinKeys()));
                if (parent == null) {
                    continue;
                }
                var target = SqlRelatedJoinResolverSupport.relatedValues(
                        junction, row, config.targetJoinKeys());
                resolvedRows.add(new ResolvedJunctionRow(parent, target));
                targetValues.add(target);
            }

            var targetUids = SqlRelatedJoinResolverSupport.targetUids(
                    sqlContext, connection, config.targetObjectClass(),
                    config.targetJoinKeys(), targetValues);
            applyResults(resolvedRows, targetUids);
        } catch (Exception e) {
            throw new ConnectorException(
                    "Failed to resolve junction attribute '" + attributeName
                            + "' from '" + config.junctionTable() + "'", e);
        }
    }

    private void applyResults(
            List<ResolvedJunctionRow> rows,
            Map<SqlRelatedJoinResolverSupport.JoinValues, String> targetUids) {
        var references = new LinkedHashMap<ConnectorObjectBuilder, List<ConnectorObjectReference>>();
        for (var row : rows) {
            var targetUid = targetUids.get(row.targetValues());
            if (targetUid == null) {
                continue;
            }
            var target = new ConnectorObjectBuilder();
            target.setObjectClass(new ObjectClass(config.targetObjectClass()));
            target.setUid(targetUid);
            references.computeIfAbsent(row.parent(), ignored -> new ArrayList<>())
                    .add(new ConnectorObjectReference(target.buildIdentification()));
        }
        for (var entry : references.entrySet()) {
            if (!entry.getValue().isEmpty()) {
                entry.getKey().addAttribute(AttributeBuilder.build(attributeName, entry.getValue()));
            }
        }
    }

    private record ResolvedJunctionRow(
            ConnectorObjectBuilder parent,
            SqlRelatedJoinResolverSupport.JoinValues targetValues) {
    }
}
