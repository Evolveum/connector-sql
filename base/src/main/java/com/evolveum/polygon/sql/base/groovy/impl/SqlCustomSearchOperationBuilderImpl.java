/*
 * Copyright (c) 2026 Evolveum and contributors
 * 
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 * 
 */
package com.evolveum.polygon.sql.base.groovy.impl;

import com.evolveum.polygon.conndev.groovy.FilterAwareSearchProcessorBuilder;
import com.evolveum.polygon.conndev.spi.FilterAwareExecuteQueryProcessor;
import com.evolveum.polygon.sql.base.SqlBaseContext;
import com.evolveum.polygon.sql.base.build.api.SqlObjectClassDefinition;
import com.evolveum.polygon.sql.base.build.api.SqlSearchOperationBuilder;
import com.evolveum.polygon.sql.base.search.SqlCustomSearchOperation;
import groovy.lang.Closure;
import org.identityconnectors.framework.common.objects.filter.Filter;

/**
 * Implementation of custom search operation builder.
 */
public class SqlCustomSearchOperationBuilderImpl implements 
        SqlSearchOperationBuilder.SqlCustomSearchOperationBuilder,
        FilterAwareSearchProcessorBuilder {

    private final SqlBaseContext context;
    private final SqlObjectClassDefinition objectClass;
    private Closure<?> queryClosure;
    private boolean emptyFilterSupported = true;
    private boolean enabled = true;

    SqlCustomSearchOperationBuilderImpl(SqlBaseContext context,
                                         SqlObjectClassDefinition objectClass) {
        this.context = context;
        this.objectClass = objectClass;
    }

    @Override
    public SqlSearchOperationBuilder.SqlCustomSearchOperationBuilder query(Closure<?> closure) {
        this.queryClosure = closure;
        return this;
    }

    @Override
    public SqlSearchOperationBuilder.SqlCustomSearchOperationBuilder emptyFilterSupported(boolean value) {
        this.emptyFilterSupported = value;
        return this;
    }

    @Override
    public boolean isEnabled() {
        return enabled && queryClosure != null;
    }

    @Override
    public boolean emptyFilterSupported() {
        return emptyFilterSupported;
    }

    @Override
    public boolean anyFilterSupported() {
        return false;
    }

    /**
     * Hook for custom filter matching logic.
     * {@inheritDoc}
     */
    public boolean supports(Filter filter) {
        if (filter == null) {
            return emptyFilterSupported();
        }
        return queryClosure != null;
    }

    @Override
    public FilterAwareExecuteQueryProcessor build() {
        if (queryClosure == null) {
            return null;
        }
        return new SqlCustomSearchOperation(context, objectClass, queryClosure);
    }

    public SqlObjectClassDefinition getObjectClass() {
        return objectClass;
    }

    public SqlBaseContext getContext() {
        return context;
    }
}
