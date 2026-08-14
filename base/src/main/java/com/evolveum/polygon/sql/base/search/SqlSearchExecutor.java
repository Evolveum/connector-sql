/*
 * Copyright (c) 2026 Evolveum and contributors
 * 
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 * 
 */
package com.evolveum.polygon.sql.base.search;

import com.evolveum.polygon.sql.base.SqlBaseContext;
import com.evolveum.polygon.sql.base.SqlObjectMapper;
import com.evolveum.polygon.sql.base.build.api.SqlAttributeDefinition;
import com.evolveum.polygon.sql.base.build.api.SqlObjectClassDefinition;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.Path;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.sql.SQLQuery;
import com.querydsl.sql.RelationalPathBase;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.framework.common.objects.ConnectorObject;
import org.identityconnectors.framework.common.objects.OperationOptions;
import org.identityconnectors.framework.common.objects.ResultsHandler;
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
        var tablePath = objectClass.sql().pathAlias("o");
        var selectedAttributes = selectColumns(tablePath, options);

        try (var connection = context.getConnection()) {
            int pageSize = 200;
            int offset = 0;
            BooleanExpression predicate =
                    SqlFilterTranslator.translate(objectClass, tablePath, filter);
            var columns = onlyPaths(selectedAttributes).toArray(new Path[0]);

            while (true) {
                try {
                    SQLQuery<Tuple> query = connection.newQuery()
                            .select(columns)
                            .from(tablePath)
                            .limit(pageSize)
                            .offset(offset);
                    if (predicate != null) {
                        query.where(predicate);
                    }
                    var rows = query.fetch();
                    for (var row : rows) {
                        var object = buildConnectorObject(row, selectedAttributes);
                        if (!resultsHandler.handle(object)) {
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

    protected RelationalPathBase<?> getTablePath() {
        return objectMapper.tablePath();
    }

    protected Map<SqlAttributeDefinition, Collection<Path<?>>> selectColumns(
            Path<?> table, OperationOptions options) {
        return objectMapper.selectColumns(table, options);
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
