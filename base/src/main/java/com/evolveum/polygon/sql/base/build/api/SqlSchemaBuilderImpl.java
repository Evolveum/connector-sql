package com.evolveum.polygon.sql.base.build.api;

import com.evolveum.polygon.conndev.api.ContextLookup;
import com.evolveum.polygon.conndev.build.api.RelationshipBuilder;
import com.evolveum.polygon.conndev.concepts.DefinitionValue;
import com.evolveum.polygon.conndev.schema.BaseSchema;
import com.evolveum.polygon.conndev.schema.BaseSchemaBuilder;
import groovy.lang.Closure;
import org.identityconnectors.framework.spi.Connector;

public class SqlSchemaBuilderImpl extends BaseSchemaBuilder<SqlSchemaBuilderImpl, SqlObjectClassSchemaBuilderImpl,
        SqlSchemaBuilder, SqlObjectClassSchemaBuilder> implements SqlSchemaBuilder {


    public SqlSchemaBuilderImpl(Class<? extends Connector> connectorClass, ContextLookup context) {
        super(connectorClass, context);
    }

    @Override
    protected SqlObjectClassSchemaBuilderImpl newObjectClass(DefinitionValue<String> name) {
        return new SqlObjectClassSchemaBuilderImpl(this, name);
    }

    @Override
    public RelationshipBuilder relationship(String name, Closure<?> closure) {
        return null;
    }

    @Override
    public BaseSchema build() {
        return super.build();
    }
}