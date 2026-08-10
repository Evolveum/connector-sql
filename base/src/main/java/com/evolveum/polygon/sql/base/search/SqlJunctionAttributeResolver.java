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
import com.evolveum.polygon.sql.base.connection.SqlConnection;
import com.evolveum.polygon.sql.base.schema.SqlJunctionJoinConfig;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.PathMetadataFactory;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.core.types.dsl.StringPath;
import com.querydsl.sql.RelationalPathBase;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.framework.common.objects.*;

import java.util.*;

/**
 * Batch attribute resolver that fetches junction table data using parameterized QueryDSL queries.
 * Builds ConnectorObjectReference instances for bidirectional references.
 */
public class SqlJunctionAttributeResolver implements AttributeResolver {

    private final SqlBaseContext sqlContext;
    private final SqlJunctionJoinConfig config;
    private final String attributeName;

    public SqlJunctionAttributeResolver(SqlBaseContext ctx, SqlJunctionJoinConfig config, String attr) {
        this.sqlContext = ctx;
        this.config = config;
        this.attributeName = attr;
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

        Map<String, ConnectorObjectBuilder> parentMap = collectParentIds(builders);
        if (parentMap.isEmpty()) {
            return;
        }

        try (var wrapper = sqlContext.getConnection()) {
            var result = fetchJunctionReferences(wrapper, parentMap.keySet());
            applyResults(parentMap, result);
        } catch (Exception e) {
            throw new ConnectorException(
                    "Failed to resolve junction attribute '" + attributeName +
                            "' from '" + config.junctionTable() + "'", e);
        }
    }

    private Map<String, ConnectorObjectBuilder> collectParentIds(
            Iterable<ConnectorObjectBuilder> builders) {
        Map<String, ConnectorObjectBuilder> map = new LinkedHashMap<>();
        for (ConnectorObjectBuilder builder : builders) {
            var obj = builder.build();
            var uid = obj.getUid();
            if (uid != null) {
                map.put(uid.getUidValue(), builder);
            }
        }
        return map;
    }

    /**
     * QueryDSL-based query for junction table resolution.
     * SELECTs only the two key columns used for references.
     */
    private Map<String, List<ConnectorObjectReference>> fetchJunctionReferences(
            SqlConnection conn,
            Set<String> parentIds) {
        var junctionTable = config.junctionTable();
        var junctionParentKey = config.junctionParentKey();
        var junctionTargetKey = config.junctionTargetKey();
        var targetObjectClass = config.targetObjectClass();

        var path = new RelationalPathBase<>(Object.class,
                PathMetadataFactory.forVariable("j"), "", junctionTable);
        StringPath parentKeyPath = Expressions.stringPath(path, junctionParentKey);
        StringPath targetKeyPath = Expressions.stringPath(path, junctionTargetKey);

        var result = new LinkedHashMap<String, List<ConnectorObjectReference>>();
        try {
            var query = conn.newQuery()
                    .select(parentKeyPath, targetKeyPath)
                    .from(path)
                    .where(parentKeyPath.in(new ArrayList<>(parentIds)));

            for (Tuple row : query.fetch()) {
                var parentId = row.get(parentKeyPath);
                var targetId = row.get(targetKeyPath);
                if (parentId == null || targetId == null) {
                    continue;
                }

                var refBuilder = new ConnectorObjectBuilder();
                refBuilder.setObjectClass(new ObjectClass(targetObjectClass));
                refBuilder.setUid(targetId);
                var identification = refBuilder.buildIdentification();
                var ref = new ConnectorObjectReference(identification);

                result.computeIfAbsent(parentId, k -> new ArrayList<>()).add(ref);
            }
        } catch (Exception e) {
            throw new ConnectorException(
                    "Junction query failed for table '" + junctionTable + "'", e);
        }

        return result;
    }

    private void applyResults(Map<String, ConnectorObjectBuilder> parentMap,
                               Map<String, List<ConnectorObjectReference>> parentToRefs) {
        for (var entry : parentToRefs.entrySet()) {
            var builder = parentMap.get(entry.getKey());
            if (builder != null) {
                List<ConnectorObjectReference> refs = entry.getValue();
                if (!refs.isEmpty()) {
                    builder.addAttribute(AttributeBuilder.build(attributeName, refs));
                }
            }
        }
    }
}
