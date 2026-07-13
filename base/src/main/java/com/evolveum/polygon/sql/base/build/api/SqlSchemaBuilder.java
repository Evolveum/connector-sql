package com.evolveum.polygon.sql.base.build.api;

import com.evolveum.polygon.conndev.annotations.Script;
import com.evolveum.polygon.conndev.build.api.SchemaBuilder;
import com.evolveum.polygon.conndev.concepts.GroovyClosures;
import groovy.lang.Closure;
import groovy.lang.DelegatesTo;

public interface SqlSchemaBuilder extends SchemaBuilder<SqlSchemaBuilder, SqlObjectClassSchemaBuilder> {

    @Override
    SqlObjectClassSchemaBuilder objectClass(String objectClassName);

    @Override
    default SqlObjectClassSchemaBuilder objectClass(String name,
                                                 @Script.Initialization
                                                 @DelegatesTo(SqlObjectClassSchemaBuilder.class)
                                                 Closure<?> closure) {
        return GroovyClosures.callAndReturnDelegate(closure, objectClass(name));
    }

    SqlSchemaBuilder onlyExplicitlyListed(boolean value);

    Boolean getOnlyExplicitlyListed();
}
