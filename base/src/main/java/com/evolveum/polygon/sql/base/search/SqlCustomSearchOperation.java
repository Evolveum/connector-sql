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
import com.querydsl.core.types.PathMetadataFactory;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.core.types.dsl.StringPath;
import com.querydsl.sql.RelationalPathBase;
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
                RelationalPathBase<Object> tableRef = createTableRef(fromTable);
                query.from(tableRef);
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
                    var obj = buildConnectorObject(
                            row, selectPaths, fromTable);
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

    private RelationalPathBase<Object> createTableRef(SqlTablePath table) {
        return new RelationalPathBase<>(
                Object.class,
                PathMetadataFactory.forVariable(table.getAlias()),
                null,
                table.getTableName());
    }

    private ConnectorObject buildConnectorObject(Tuple row,
                                                 List<Path<?>> columns,
                                                 SqlTablePath fromTable) {
        var builder = new ConnectorObjectBuilder();
        builder.setObjectClass(objectClass.objectClass());

        // Extract values by column name from the tuple
        // QueryDSL stores results labeled by Expression.toString()
        // For aliased columns: "alias"."columnName"
        for (var attr : objectClass.attributes()) {
            var mapping = attr.sql();
            if (mapping == null || mapping.column() == null) continue;
            
            var colName = mapping.column().value();
            if (colName == null) continue;

            // Try to extract value from the tuple
            var value = extractByLabel(row, colName, fromTable);
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

    private Object extractByLabel(Tuple row, String colName, 
                                    SqlTablePath fromTable) {
        String alias = fromTable != null ? fromTable.getAlias() : null;
        
        // Try with alias prefix: QueryDSL stores paths as "alias"."columnName"
        // or just "columnName" - need to try both
        if (alias != null) {
            RelationalPathBase<Object> tableRef = createTableRef(fromTable);
            try {
                var path = Expressions.stringPath(tableRef, colName);
                return row.get(path);
            } catch (Exception ignored) {}
        }
        
        // Try bare column name
        try {
            StringPath path = Expressions.stringPath(colName);
            return row.get(path);
        } catch (Exception ignored) {}

        // Try with quotes
        try {
            StringPath path = Expressions.stringPath("\"" + colName + "\"");
            return row.get(path);
        } catch (Exception ignored) {}

        return null;
    }

    @Override
    public boolean supports(Filter filter) {
        return true;
    }
}
