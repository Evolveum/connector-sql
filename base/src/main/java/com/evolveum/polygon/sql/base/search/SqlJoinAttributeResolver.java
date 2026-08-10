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
import com.evolveum.polygon.sql.base.schema.SqlChildJoinConfig;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.PathMetadataFactory;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.core.types.dsl.StringPath;
import com.querydsl.sql.RelationalPathBase;
import com.querydsl.sql.SQLQuery;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.framework.common.objects.*;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.util.*;

/**
 * Batch attribute resolver that fetches child table data using parameterized queries.
 * Supports both embedded object mode (multiple columns) and simple attribute mode (single column).
 */
public class SqlJoinAttributeResolver implements AttributeResolver {

    private final SqlBaseContext sqlContext;
    private final SqlChildJoinConfig config;
    private final String attributeName;

    public SqlJoinAttributeResolver(SqlBaseContext ctx, SqlChildJoinConfig config, String attr) {
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

        var parentMap = collectParentIds(builders);
        if (parentMap.isEmpty()) {
            return;
        }

        boolean simple = config.valueColumn() != null;

        try (var wrapper = sqlContext.getConnection()) {
            if (simple) {
                resolveSimpleAttribute(wrapper, parentMap);
            } else {
                resolveEmbeddedObjects(wrapper.getConnection(), parentMap);
            }
        } catch (Exception e) {
            throw new ConnectorException(
                    "Failed to resolve attribute '" + attributeName +
                            "' from child table '" + config.childTable() + "'", e);
        }
    }

    private Map<String, ConnectorObjectBuilder> collectParentIds(
            Iterable<ConnectorObjectBuilder> builders) {
        var map = new LinkedHashMap<String, ConnectorObjectBuilder>();
        for (var builder : builders) {
            var obj = builder.build();
            var uid = obj.getUid();
            if (uid != null) {
                map.put(uid.getUidValue(), builder);
            }
        }
        return map;
    }

    /**
     * QueryDSL-based query for simple-attribute mode.
     * Only SELECTs the value column and join column (optimized).
     */
    private void resolveSimpleAttribute(SqlConnection conn,
                                        Map<String, ConnectorObjectBuilder> parentMap) {
        var childTable = config.childTable();
        var childJoinCol = config.childJoinColumn();
        var valueColumn = config.valueColumn();

        var path = new RelationalPathBase<>(Object.class,
                PathMetadataFactory.forVariable("c"), "", childTable);
        StringPath valuePath = Expressions.stringPath(path, valueColumn);
        StringPath joinPath = Expressions.stringPath(path, childJoinCol);

        var result = new LinkedHashMap<String, List<Object>>();
        try {
            SQLQuery<Tuple> query = conn.newQuery()
                    .select(valuePath, joinPath)
                    .from(path)
                    .where(joinPath.in(new ArrayList<>(parentMap.keySet())));
            for (Tuple row : query.fetch()) {
                var parentId = row.get(joinPath);
                if (parentId == null) {
                    continue;
                }
                var value = row.get(valuePath);
                if (value != null) {
                    result.computeIfAbsent(parentId, k -> new ArrayList<>()).add(value);
                }
            }
        } catch (Exception e) {
            throw new ConnectorException("Simple attribute query failed for '" + childTable + "'", e);
        }

        applyResults(parentMap, result);
    }

    /**
     * PreparedStatement-based query for embedded-object mode.
     * SELECT * is required because we don't know child column names at compile time.
     * Parameters are bound via placeholders to avoid SQL injection.
     */
    private void resolveEmbeddedObjects(Connection conn,
                                        Map<String, ConnectorObjectBuilder> parentMap) {
        var childTable = config.childTable();
        var childJoinCol = config.childJoinColumn();

        List<String> parentIds = new ArrayList<>(parentMap.keySet());
        var sql = new StringBuilder();
        sql.append("SELECT * FROM ").append(childTable)
                .append(" WHERE ").append(childJoinCol).append(" IN (");
        for (int i = 0; i < parentIds.size(); i++) {
            if (i > 0) sql.append(',');
            sql.append('?');
        }
        sql.append(')');

        var result = new LinkedHashMap<String, List<Object>>();
        try {
            try (var stmt = conn.prepareStatement(sql.toString())) {
                for (int i = 0; i < parentIds.size(); i++) {
                    stmt.setString(i + 1, parentIds.get(i));
                }

                try (var rs = stmt.executeQuery()) {
                    var rsmd = rs.getMetaData();
                    int colCount = rsmd.getColumnCount();

                    while (rs.next()) {
                        var parentId = rs.getString(childJoinCol);
                        if (parentId == null) {
                            continue;
                        }

                        var attrs = new LinkedHashSet<Attribute>();
                        for (int i = 1; i <= colCount; i++) {
                            var colName = rsmd.getColumnName(i);
                            var value = rs.getObject(i);
                            if (value != null) {
                                attrs.add(AttributeBuilder.build(colName, value));
                            }
                        }

                        var childOcl = new ObjectClass(childTable);
                        var embedded = new EmbeddedObject(childOcl, attrs);
                        result.computeIfAbsent(parentId, k -> new ArrayList<>()).add(embedded);
                    }
                }
            }
        } catch (Exception e) {
            throw new ConnectorException("Embedded object query failed for '" + childTable + "'", e);
        }

        applyResults(parentMap, result);
    }

    private void applyResults(Map<String, ConnectorObjectBuilder> parentMap,
                              Map<String, List<Object>> parentToValues) {
        for (var entry : parentToValues.entrySet()) {
            var builder = parentMap.get(entry.getKey());
            if (builder != null) {
                List<Object> values = entry.getValue();
                if (!values.isEmpty()) {
                    builder.addAttribute(AttributeBuilder.build(attributeName, values));
                }
            }
        }
    }
}
