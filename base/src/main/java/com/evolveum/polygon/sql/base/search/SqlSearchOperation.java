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
import com.querydsl.core.types.dsl.BooleanExpression;
import groovy.lang.Closure;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.framework.common.objects.OperationOptions;
import org.identityconnectors.framework.common.objects.ResultsHandler;
import org.identityconnectors.framework.common.objects.filter.Filter;

/**
 * QueryDSL-based search operation for SQL object classes.
 * Supports WHERE clause customization via Groovy closures.
 */
public class SqlSearchOperation implements FilterAwareExecuteQueryProcessor {

    private final SqlSearchExecutor executor;
    private final Closure<?> whereClosure;

    public SqlSearchOperation(SqlBaseContext context, SqlObjectClassDefinition objectClass) {
        this(context, objectClass, null);
    }

    public SqlSearchOperation(SqlBaseContext context, SqlObjectClassDefinition objectClass,
                               Closure<?> whereClosure) {
        this.executor = new SqlSearchExecutor(context, objectClass);
        this.whereClosure = whereClosure;
    }

    @Override
    public void executeQuery(ContextLookup c, Filter filter, ResultsHandler resultsHandler,
                              OperationOptions options) {
        BooleanExpression additionalPredicate = null;
        if (whereClosure != null) {
            var tablePath = executor.getTablePath();
            var builder = new SqlWherePredicateBuilder(tablePath, executor.context);
            try {
                var copy = (Closure<?>) whereClosure.clone();
                copy.setResolveStrategy(Closure.DELEGATE_FIRST);
                copy.call(builder);
            } catch (Exception e) {
                throw new ConnectorException("Error evaluating where closure: " + e.getMessage(), e);
            }
            additionalPredicate = builder.build();
        }
        executor.execute(filter, resultsHandler, options, additionalPredicate);
    }

    @Override
    public boolean supports(Filter filter) {
        // Built-in handler supports all filters
        return true;
    }
}