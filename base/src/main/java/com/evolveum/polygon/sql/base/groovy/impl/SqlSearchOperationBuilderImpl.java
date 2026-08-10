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
import com.evolveum.polygon.sql.base.build.api.SqlObjectClassDefinition;
import com.evolveum.polygon.sql.base.build.api.SqlSearchOperationBuilder;
import com.evolveum.polygon.sql.base.search.SqlSearchOperation;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class SqlSearchOperationBuilderImpl extends AbstractSearchOperationBuilder implements SqlSearchOperationBuilder {


    private final BuiltInBuilder builtIn = new BuiltInBuilder();
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
        return new SqlSpecific() {
            @Override
            public BuiltIn builtIn() {
                return builtIn;
            }
        };
    }

    class BuiltInBuilder implements BuiltIn, FilterAwareSearchProcessorBuilder {

        private DefinitionValue<Boolean> enabled = DefinitionValue.DEFAULT_TRUE;

        @Override
        public BuiltIn enabled(boolean value) {
            this.enabled = this.enabled.moreSpecific(DefinitionValue.from(value, SourceLocation.capture()));
            return self();
        }

        @Override
        public boolean isEnabled() {
            return enabled.value();
        }

        @Override
        public boolean emptyFilterSupported() {
            // FIXME: make configurable
            return true;
        }

        @Override
        public boolean anyFilterSupported() {
            // FIXME: make configurable
            return true;
        }

        @Override
        public FilterAwareExecuteQueryProcessor build() {
            // FIXME: Also add configuration if necessary.
            return new SqlSearchOperation(context, objectClass);
        }
    }
}
