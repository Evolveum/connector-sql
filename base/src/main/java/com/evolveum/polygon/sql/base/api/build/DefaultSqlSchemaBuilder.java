package com.evolveum.polygon.sql.base.api.build;

import com.evolveum.polygon.conndev.build.RelationshipBuilder;
import groovy.lang.Closure;

import java.util.HashMap;
import java.util.Map;

public class DefaultSqlSchemaBuilder implements SqlSchemaBuilder {

    private final Map<String, SqlObjectClassSchemaBuilder> objectClasses = new HashMap<>();

    @Override
    public SqlObjectClassSchemaBuilder objectClass(String objectClassName) {
        DefaultSqlObjectClassSchemaBuilder obj = new DefaultSqlObjectClassSchemaBuilder();
        obj.setObjectClassName(objectClassName);
        objectClasses.put(objectClassName, obj);
        return obj;
    }

    @Override
    public RelationshipBuilder relationship(String name, Closure<?> closure) {
        return null;
    }
}