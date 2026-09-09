/*
 * Copyright (c) 2026 Evolveum and contributors
 * 
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 * 
 */
package com.evolveum.polygon.sql.base.search;

import com.evolveum.polygon.conndev.spi.BatchAwareResultHandler;
import com.evolveum.polygon.sql.base.SqlBaseContext;
import com.evolveum.polygon.sql.base.SqlObjectMapper;
import com.evolveum.polygon.sql.base.build.api.SqlAttributeDefinition;
import com.evolveum.polygon.sql.base.build.api.SqlObjectClassDefinition;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.Path;
import com.querydsl.core.types.Order;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.Wildcard;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.sql.RelationalPathBase;
import com.querydsl.sql.SQLQuery;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.framework.common.objects.ConnectorObject;
import org.identityconnectors.framework.common.objects.OperationOptions;
import org.identityconnectors.framework.common.objects.ResultsHandler;
import org.identityconnectors.framework.common.objects.Uid;
import org.identityconnectors.framework.common.objects.filter.Filter;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public class SqlSearchExecutor {

    protected final SqlBaseContext context;
    protected final SqlObjectClassDefinition objectClass;
    private final SqlObjectMapper objectMapper;

    public SqlSearchExecutor(SqlBaseContext context, SqlObjectClassDefinition objectClass) {
        this.context = context;
        this.objectClass = objectClass;
        this.objectMapper = new SqlObjectMapper(objectClass);
    }

    public void execute(Filter filter, ResultsHandler resultsHandler, OperationOptions options) {
        execute(filter, resultsHandler, options, null);
    }

    public void execute(Filter filter, ResultsHandler resultsHandler, OperationOptions options,
                        BooleanExpression additionalPredicate) {
        var tablePath = objectClass.sql().pathAlias("o");
        var selectedAttributes = selectColumns(tablePath);

        try (var connection = context.getConnection()) {
            int pageSize = 200;
            int offset = 0;
            BooleanExpression predicate =
                    SqlFilterTranslator.translate(objectClass, tablePath, filter);
            predicate = combinePredicate(predicate, additionalPredicate);
            var columns = onlyPaths(selectedAttributes).toArray(new Path[0]);

            var uidPaths = objectClass.attributeFromConnIdName(Uid.NAME).sql()
                    .selectPaths(tablePath).toArray(Path<?>[]::new);
            if (!objectClass.sql().joins().isEmpty()) {
                // Validate the requested result before delivering any objects.
                // Views need this check too: JDBC cannot prove uniqueness of their join columns.
                var duplicates = connection.newQuery().select(uidPaths).from(tablePath);
                applyJoins(duplicates);
                if (predicate != null) {
                    duplicates.where(predicate);
                }
                if (!duplicates.groupBy(uidPaths).having(Wildcard.count.gt(1)).limit(1).fetch().isEmpty()) {
                    throw new ConnectorException("Joined object " + objectClass.name()
                            + " has multiple matching rows for one UID; joins must be single-valued");
                }
            }

            while (true) {
                try {
                    SQLQuery<Tuple> query = connection.newQuery()
                            .select(columns)
                            .from(tablePath)
                            .limit(pageSize)
                            .offset(offset);
                    applyJoins(query);
                    if (!objectClass.sql().joins().isEmpty()) {
                        for (var uidPath : uidPaths) {
                            query.orderBy(ascending(uidPath));
                        }
                    }
                    if (predicate != null) {
                        query.where(predicate);
                    }
                    var rows = query.fetch();
                    boolean stopped = false;
                    for (var row : rows) {
                        var object = buildConnectorObject(row, selectedAttributes);
                        if (!resultsHandler.handle(object)) {
                            stopped = true;
                            break;
                        }
                    }
                    BatchAwareResultHandler.batchFinished(resultsHandler);
                    if (stopped) {
                        return;
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

    private void applyJoins(SQLQuery<?> query) {
        for (var join : objectClass.sql().joins()) {
            query.leftJoin(join.path()).on(join.condition());
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static OrderSpecifier<?> ascending(Path<?> path) {
        return new OrderSpecifier(Order.ASC, path);
    }

    protected static BooleanExpression combinePredicate(BooleanExpression a, BooleanExpression b) {
        if (a != null && b != null) {
            return a.and(b);
        }
        if (a != null) {
            return a;
        }
        return b;
    }

    protected RelationalPathBase<?> getTablePath() {
        return objectMapper.tablePath();
    }

    protected Map<SqlAttributeDefinition, Collection<Path<?>>> selectColumns(Path<?> table) {
        return objectMapper.selectColumns(table);
    }

    protected ConnectorObject buildConnectorObject(
            Tuple row, Map<SqlAttributeDefinition, Collection<Path<?>>> attributes) {
        return objectMapper.buildConnectorObject(getTablePath(), row, attributes);
    }

    protected List<Path<?>> onlyPaths(
            Map<SqlAttributeDefinition, Collection<Path<?>>> selectedAttributes) {
        return objectMapper.onlyPaths(selectedAttributes);
    }
}
