package com.evolveum.polygon.sql.base.api.build;

import com.evolveum.polygon.conndev.annotations.Script;
import com.evolveum.polygon.conndev.build.SchemaBuilder;
import com.evolveum.polygon.conndev.concepts.GroovyClosures;
import groovy.lang.Closure;
import groovy.lang.DelegatesTo;

public interface SqlSchemaBuilder extends SchemaBuilder {

    @Override
    SqlObjectClassSchemaBuilder objectClass(String objectClassName);

    @Override
    default SqlObjectClassSchemaBuilder objectClass(String name,
                                                 @Script.Initialization
                                                 @DelegatesTo(SqlObjectClassSchemaBuilder.class)
                                                 Closure<?> closure) {
        return GroovyClosures.callAndReturnDelegate(closure, objectClass(name));
    }
}
