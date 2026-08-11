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
import com.evolveum.polygon.sql.base.build.api.SqlObjectClassDefinition;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.Path;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.sql.SQLQuery;
import groovy.lang.Closure;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.framework.common.objects.ConnectorObject;
import org.identityconnectors.framework.common.objects.ConnectorObjectBuilder;
import org.identityconnectors.framework.common.objects.OperationOptions;
import org.identityconnectors.framework.common.objects.ResultsHandler;
import org.identityconnectors.framework.common.objects.filter.Filter;

import java.util.List;

/**
 * Executes custom SQL search queries defined by Groovy closures.
 */
public class SqlCustomSearchOperation implements FilterAwareExecuteQueryProcessor {

    private final SqlBaseContext context;
    private final SqlObjectClassDefinition objectClass;
    private final Closure<?> queryClosure;

    public SqlCustomSearchOperation(SqlBaseContext ctx,
                                     SqlObjectClassDefinition oc,
                                     Closure<?> closure) {
        this.context = ctx;
        this.objectClass = oc;
        this.queryClosure = closure;
    }

    @Override
    public void executeQuery(ContextLookup c, Filter filter,
                              ResultsHandler resultsHandler,
                              OperationOptions options) {
        // Create a fresh query context
        var qCtx = new SqlCustomQueryBuilderContext(context, objectClass, filter);
        // Copy the closure prototype to avoid modifying the shared original
        var copy = (Closure<?>) queryClosure.clone();
        copy.setResolveStrategy(Closure.DELEGATE_ONLY);
        // Pass context as parameter 'q' AND as delegate
        copy.call(qCtx);

        // Build QueryDSL query from accumulated configuration
        var selectPaths = qCtx.getSelectPathsInternal();
        var fromTable = qCtx.getFromTableInternal();
        var wherePreds = qCtx.getWherePredicatesInternal();
        var orderBySpecs = qCtx.getOrderBysInternal();

        try (var conn = context.getConnection()) {
            SQLQuery<Tuple> query = conn.newQuery();

            // SELECT
            if (!selectPaths.isEmpty()) {
                query.select(selectPaths.toArray(new Path[0]));
            }

            // FROM
            if (fromTable != null) {
                query.from(fromTable.tableRef());
            }

            // WHERE
            for (BooleanExpression pred : wherePreds) {
                query.where(pred);
            }

            // ORDER BY
            if (!orderBySpecs.isEmpty()) {
                query.orderBy(orderBySpecs
                        .toArray(new OrderSpecifier[orderBySpecs.size()]));
            }

            // Execute with pagination
            int pageSize = 200;
            int offset = 0;

            while (true) {
                query.limit(pageSize).offset(offset);
                var rows = query.fetch();

                for (Tuple row : rows) {
                    var obj = buildConnectorObject(row, selectPaths);
                    if (!resultsHandler.handle(obj)) return;
                }

                if (rows.isEmpty() || rows.size() < pageSize) return;
                offset += pageSize;
            }
        } catch (ConnectorException e) {
            throw e;
        } catch (Exception e) {
            throw new ConnectorException(
                    "Custom query DSL failed: " + e.getMessage(), e);
        }
    }

    private ConnectorObject buildConnectorObject(Tuple row, List<Path<?>> selectPaths) {
        var builder = new ConnectorObjectBuilder();
        builder.setObjectClass(objectClass.objectClass());

        // Match each object class attribute to a SELECT path by column name,
        // then extract the value using the actual path used in the query.
        for (var attr : objectClass.attributes()) {
            var mapping = attr.sql();
            if (mapping == null || mapping.column() == null) continue;

            var colName = mapping.column().value();
            if (colName == null) continue;

            var matchedPath = findPathForColumn(selectPaths, colName);
            if (matchedPath == null) continue;

            @SuppressWarnings("unchecked")
            var value = row.get((Path<Object>) matchedPath);
            if (value != null) {
                try {
                    builder.addAttribute(attr.attributeOf(
                            mapping.singleValueFromAttribute(value)));
                } catch (Exception ignored) {
                    // Type conversion failure
                }
            }
        }

        return builder.build();
    }

    /**
     * Finds the SELECT path that corresponds to a column name.
     * QueryDSL paths are labeled as "alias"."columnName" - we match on the suffix.
     */
    private Path<?> findPathForColumn(List<Path<?>> selectPaths, String colName) {
        for (Path<?> p : selectPaths) {
            if (p.toString().toLowerCase().endsWith(colName.toLowerCase())) {
                return p;
            }
        }
        return null;
    }

    @Override
    public boolean supports(Filter filter) {
        return true;
    }
}
