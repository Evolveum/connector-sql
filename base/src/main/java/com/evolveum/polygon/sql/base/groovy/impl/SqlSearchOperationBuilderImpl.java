/*
 * Copyright (c) 2026 Evolveum and contributors
 * 
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 * 
 */
package com.evolveum.polygon.sql.base.groovy.impl;

import com.evolveum.polygon.conndev.concepts.DefinitionValue;
import com.evolveum.polygon.conndev.concepts.SourceLocation;
import com.evolveum.polygon.conndev.groovy.AbstractSearchOperationBuilder;
import com.evolveum.polygon.conndev.groovy.FilterAwareSearchProcessorBuilder;
import com.evolveum.polygon.conndev.spi.AttributeResolver;
import com.evolveum.polygon.conndev.spi.FilterAwareExecuteQueryProcessor;
import com.evolveum.polygon.sql.base.SqlBaseContext;
// SqlCustomSearchOperationBuilder nested in SqlSearchOperationBuilder
import com.evolveum.polygon.sql.base.build.api.SqlObjectClassDefinition;
import com.evolveum.polygon.sql.base.build.api.SqlSearchOperationBuilder;
import com.evolveum.polygon.sql.base.search.SqlSearchOperation;
import groovy.lang.Closure;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class SqlSearchOperationBuilderImpl extends AbstractSearchOperationBuilder<SqlObjectClassDefinition> implements SqlSearchOperationBuilder {


    private final BuiltInBuilder builtIn = new BuiltInBuilder();
    private SqlCustomSearchOperationBuilderImpl custom;
    private final SqlBaseContext context;
    private final SqlObjectClassDefinition objectClass;
    DefinitionValue<Boolean> enabled = DefinitionValue.DEFAULT_TRUE;
    private final List<AttributeResolver> sqlResolvers = new ArrayList<>();

    protected SqlSearchOperationBuilderImpl(SqlObjectOperationBuilderImpl parent, SqlBaseContext context, SqlObjectClassDefinition objectClass) {
        super(parent);
        this.context = context;
        this.objectClass = objectClass;
        builders.add(builtIn);
    }

    /** Registers a SQL-based attribute resolver for join/embedded data resolution. */
    public void registerSqlResolver(AttributeResolver resolver) {
        sqlResolvers.add(resolver);
    }

    @Override
    protected void applyAdditionalAttributeResolvers(
            Set<AttributeResolver> perObjectResolvers,
            Set<AttributeResolver> batchedResolvers) {
        super.applyAdditionalAttributeResolvers(perObjectResolvers, batchedResolvers);
        for (AttributeResolver resolver : sqlResolvers) {
            switch (resolver.resolutionType()) {
                case BATCH -> batchedResolvers.add(resolver);
                case PER_OBJECT -> perObjectResolvers.add(resolver);
            }
        }
    }


    @Override
    public boolean isEnabled() {
        return enabled.value();
    }

    @Override
    public SqlSearchOperationBuilder enabled(DefinitionValue<Boolean> value) {
        this.enabled = this.enabled.moreSpecific(value);
        return this;
    }

    @Override
    public SqlSpecific sql() {
        return sqlSpecific;
    }

    private final SqlSpecific sqlSpecific = new SqlSpecific() {
        @Override
        public BuiltIn builtIn() {
            return builtIn;
        }

        @Override
        public SqlCustomSearchOperationBuilder custom() {
                if (custom == null) {
                    custom = new SqlCustomSearchOperationBuilderImpl(context, objectClass);
                    builders.add(custom);
                }
                // Disabling built-in to avoid multiple emptyFilter handlers conflict
                builtIn.enabled = builtIn.enabled.moreSpecific(DefinitionValue.from(false, SourceLocation.capture()));
                return custom;
        }
    };

    class BuiltInBuilder implements BuiltIn, FilterAwareSearchProcessorBuilder {

        private DefinitionValue<Boolean> enabled = DefinitionValue.DEFAULT_TRUE;
        private Closure<?> whereClosure;

        @Override
        public BuiltIn enabled(boolean value) {
            this.enabled = this.enabled.moreSpecific(DefinitionValue.from(value, SourceLocation.capture()));
            return self();
        }

        @Override
        public BuiltIn where(Closure<?> closure) {
            this.whereClosure = closure;
            return self();
        }

        @Override
        public boolean isEnabled() {
            return enabled.value();
        }

        @Override
        public boolean emptyFilterSupported() {
            return true;
        }

        @Override
        public boolean anyFilterSupported() {
            return true;
        }

        // Note: supports(Filter) is handled by the built-in operation at runtime

        @Override
        public FilterAwareExecuteQueryProcessor build() {
            return new SqlSearchOperation(context, objectClass, whereClosure);
        }
    }
}
