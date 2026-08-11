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
import org.identityconnectors.framework.common.objects.OperationOptions;
import org.identityconnectors.framework.common.objects.ResultsHandler;
import org.identityconnectors.framework.common.objects.filter.Filter;

/**
 * QueryDSL-based search operation for SQL object classes.
 * Supports WHERE clause customization via Groovy closures.
 */
public class SqlSearchOperation implements FilterAwareExecuteQueryProcessor {

    private final SqlSearchExecutor executor;

    public SqlSearchOperation(SqlBaseContext context, SqlObjectClassDefinition objectClass) {
        this.executor = new SqlSearchExecutor(context, objectClass);
    }

    @Override
    public void executeQuery(ContextLookup c, Filter filter, ResultsHandler resultsHandler,
                              OperationOptions options) {
        executor.execute(filter, resultsHandler, options);
    }

    @Override
    public boolean supports(Filter filter) {
        // Built-in handler supports all filters
        return true;
    }
}
