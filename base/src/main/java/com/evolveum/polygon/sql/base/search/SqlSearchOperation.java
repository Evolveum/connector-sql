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
public class SqlSearchOperation extends SqlSearchExecutor implements FilterAwareExecuteQueryProcessor {


    public SqlSearchOperation(SqlBaseContext context, SqlObjectClassDefinition objectClass) {
        super(context, objectClass);
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

            var columns = onlyPaths(selectedAttributes).toArray(new Path[0]);

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

    @Override
    public boolean supports(Filter filter) {
        // FIXME: Allow only explicitly specified filters
        return true;
    }
}
