/*
 * Copyright (c) 2026 Evolveum and contributors
 * 
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 * 
 */
package com.evolveum.polygon.sql.base.groovy.impl;

import com.evolveum.polygon.conndev.groovy.AbstractOperationSupportBuilder;
import com.evolveum.polygon.conndev.schema.BaseObjectClassDefinition;
import com.evolveum.polygon.sql.base.SqlBaseContext;
import com.evolveum.polygon.sql.base.build.api.SqlObjectClassDefinition;
import com.evolveum.polygon.sql.base.build.api.SqlObjectOperationSupportBuilder;
import com.evolveum.polygon.sql.base.build.api.SqlOperationSupportBuilder;
import com.evolveum.polygon.sql.base.build.api.SqlSchema;

public class SqlOperationSupportBuilderImpl extends AbstractOperationSupportBuilder<SqlOperationSupportBuilder, SqlObjectOperationSupportBuilder> implements SqlOperationSupportBuilder {

    private final SqlBaseContext context;

    public SqlOperationSupportBuilderImpl(SqlBaseContext context) {
        super(context);
        this.context = context;
    }

    @Override
    protected SqlObjectOperationSupportBuilder newObjectSpecific(BaseObjectClassDefinition classDefinition) {
        return new SqlObjectOperationBuilderImpl(this.context, (SqlObjectClassDefinition) classDefinition);
    }

    @Override
    public SqlObjectOperationBuilderImpl objectClass(String user) {
        // Resolve to the object class's declared name before keying the handler, so that a
        // script referencing the detected table name (e.g. crew_member) still lands on the
        // object class declared for that table (e.g. CrewMember) — WP 13580.
        return (SqlObjectOperationBuilderImpl) super.objectClass(objectClassName(user));
    }

    /**
     * Resolves the object class name an operation script refers to: the schema's own lookup
     * (case-insensitive by ConnId {@code ObjectClass} identity) first, then the object class
     * whose mapped table name matches the requested name — scripts generated before a rename
     * still reference the detected table name. A name matching neither is a configuration
     * error and fails with the known object classes listed, instead of a NullPointerException
     * later in {@code SqlWriteHandlers}.
     */
    private String objectClassName(String user) {
        var schema = context.schema();
        if (schema == null || schema.objectClass(user) != null) {
            return user;
        }
        for (var definition : schema.objectClasses()) {
            var tableName = definition.sql() != null ? definition.sql().getTableName() : null;
            if (tableName != null && tableName.equalsIgnoreCase(user)) {
                return definition.name();
            }
        }
        throw new IllegalArgumentException("No object class definition for '" + user
                + "'; known object classes: " + knownObjectClasses(schema));
    }

    private static String knownObjectClasses(SqlSchema schema) {
        return schema.objectClasses().stream()
                .map(SqlObjectClassDefinition::name)
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .distinct()
                .toList()
                .toString();
    }
}
