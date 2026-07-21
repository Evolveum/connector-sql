/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.sql.base.search;

import com.evolveum.polygon.conndev.api.ContextLookup;
import com.evolveum.polygon.conndev.spi.FilterAwareExecuteQueryProcessor;
import com.evolveum.polygon.sql.base.SqlBaseContext;
import com.evolveum.polygon.sql.base.SqlTuple;
import com.evolveum.polygon.sql.base.build.api.SqlAttributeDefinition;
import com.evolveum.polygon.sql.base.build.api.SqlObjectClassDefinition;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.Path;
import com.querydsl.core.types.Predicate;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.sql.RelationalPathBase;
import com.querydsl.sql.SQLQuery;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.framework.common.objects.*;
import org.identityconnectors.framework.common.objects.filter.Filter;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * QueryDSL-based search operation for SQL object classes.
 *
 */
public class SqlSearchOperation implements FilterAwareExecuteQueryProcessor {

    private final SqlBaseContext context;
    private final SqlObjectClassDefinition objectClass;

    public SqlSearchOperation(SqlBaseContext context, SqlObjectClassDefinition objectClass) {
        this.context = context;
        this.objectClass = objectClass;
    }

    private RelationalPathBase<?> getTablePath() {
        return objectClass.sql().pathAlias("o");
    }

    @Override
    public void executeQuery(ContextLookup c, Filter filter, ResultsHandler resultsHandler,
                              OperationOptions options) {

        var tablePath = objectClass.sql().pathAlias("o");
        var selectedAttributes = selectColumns(tablePath, options);

        try (var conn = context.getConnection()) {

            int pageSize = 200;
            int offset = 0;
            var predicate = Expressions.TRUE;
            if (filter != null) {
                predicate = SqlFilterTranslator.translate(objectClass, tablePath, filter);
            }

            var columns = selectedAttributes.values().stream().flatMap(Collection::stream)
                    .toList()
                    .toArray(new Path[0]);

            while (true) {
                try {
                    SQLQuery<Tuple> query =conn.newQuery()
                            .select(columns)
                            .from(tablePath)
                            .where(predicate)
                            .limit(pageSize)
                            .offset(offset);
                    var rows = query.fetch();
                    for (var row : rows) {
                        var obj = buildConnectorObject(row, selectedAttributes);
                        if (!resultsHandler.handle(obj)) {
                            return;
                        }
                    }

                    if (rows.isEmpty() || rows.size() < pageSize) {
                        return;
                    }

                    offset += pageSize;
                } catch (Exception e) {
                    throw new ConnectorException(
                            "QueryDSL select failed: " + e.getMessage(), e);
                }
            }
        }
    }

    private Map<SqlAttributeDefinition, Collection<Path<?>>> selectColumns(Path<?> table, OperationOptions options) {
        Map<SqlAttributeDefinition, Collection<Path<?>>> columns = new HashMap<>();
        // UID and Name must be always selected
        var uidAttribute = objectClass.attributeFromConnIdName(Uid.NAME);
        columns.put(uidAttribute, uidAttribute.sql().selectPaths(table));

        var nameAttribute = objectClass.attributeFromConnIdName(Name.NAME);
        if (nameAttribute.sql() != null) {
            columns.put(nameAttribute, nameAttribute.sql().selectPaths(table));
        }

        for (var attr : objectClass.attributes()) {
            if (attr.sql() != null && attr.connId().isReturnedByDefault()) {
                columns.put(attr, attr.sql().selectPaths(table));
            }
        }

        return columns;
    }

    private ConnectorObject buildConnectorObject(Tuple row, Map<SqlAttributeDefinition, Collection<Path<?>>> attributes) {
        var builder = new ConnectorObjectBuilder();
        builder.setObjectClass(objectClass.objectClass());
        for (var attrEntry : attributes.entrySet()) {
            var attr = attrEntry.getKey();
            var mapping = attr.sql();
                if (mapping != null) {
                    var value = mapping.valuesFromObject(new SqlTuple(getTablePath(), row));
                    builder.addAttribute(attr.attributeOf(value));
                }
        }
        // FIXME: Maybe there is need to compute UID, NAME attributes
        return builder.build();
    }

    @Override
    public boolean supports(Filter filter) {
        // FIXME: Allow only explicitly specified filters
        return true;
    }
}
